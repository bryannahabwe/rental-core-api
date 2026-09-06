package com.cognix.rentalcoreapi.modules.payments.service;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.audit.AuditDiff;
import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.agreements.repository.RentalAgreementRepository;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.payments.dto.PaymentRequest;
import com.cognix.rentalcoreapi.modules.payments.dto.PaymentResponse;
import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentSource;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import com.cognix.rentalcoreapi.shared.exception.ConflictException;
import com.cognix.rentalcoreapi.shared.exception.NotFoundException;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import com.cognix.rentalcoreapi.shared.security.PropertyAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RentalAgreementRepository agreementRepository;
    private final UserRepository userRepository;
    private final PropertyAccessGuard propertyAccessGuard;
    private final AuditWriter auditWriter;
    private final PeriodPaidLookup periodPaidLookup;
    private final PaymentAllocationService allocationService;

    public PagedResponse<PaymentResponse> getAllPayments(
            Pageable pageable, UUID tenantId, UUID agreementId,
            String search, LocalDate from, LocalDate to) {

        UUID landlordId = JwtUtils.getCurrentLandlordId();
        UUID propertyId = propertyAccessGuard.requireAccessibleProperty();

        // `from` and `to` are each honoured on their own. The previous
        // both-or-nothing branch meant a half-specified range was silently
        // ignored and the caller got every row back with no indication why.
        Page<Payment> page = paymentRepository.findAllWithFilters(
                landlordId, propertyId, tenantId, agreementId, search, from, to, pageable);

        // One grouped query for every cycle this page touches, so each row's
        // status reflects its whole period rather than its own amount — a
        // top-up on a part-paid cycle must not read PARTIAL forever.
        PeriodPaidLookup.Index periodPaid = periodPaidLookup.forPayments(page.getContent());

        return PagedResponse.from(page.map(p -> PaymentResponse.from(p, periodPaid.paidFor(p))));
    }

    public PaymentResponse getPayment(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        Payment payment = paymentRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        propertyAccessGuard.assertCanAccess(payment.getProperty().getId());
        return PaymentResponse.from(payment, retainedByCycle(payment));
    }

    @Transactional
    public PaymentResponse recordPayment(PaymentRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        RentalAgreement agreement = lockAgreement(request.agreementId(), landlordId);

        if (agreement.getStatus() == AgreementStatus.TERMINATED) {
            throw new ConflictException(
                    "Cannot record payment for a terminated agreement");
        }

        Payment payment = Payment.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .property(agreement.getProperty())
                .tenant(agreement.getTenant())
                .unit(agreement.getUnit())
                .agreement(agreement)
                .paymentDate(request.paymentDate())
                .amount(request.amount())
                .method(request.method())
                .periodStartDate(request.periodStartDate())
                .periodEndDate(request.periodEndDate())
                .expectedAmount(agreement.getRentAmount())
                .overpayment(BigDecimal.ZERO)
                .source(PaymentSource.CASH)
                .reference(request.reference())
                .notes(request.notes())
                .build();

        // saveAndFlush (not save) so @CreationTimestamp's INSERT-time
        // population actually happens before the replay orders rows by it.
        Payment saved = paymentRepository.saveAndFlush(payment);

        // The row's own overpayment, the rollover chain it spawns and any
        // effect on the opening balance are all derived — the replay sizes
        // every one of them. A new row sorts last in recorded order, so it is
        // allocated against exactly the state its predecessors left behind.
        allocationService.reallocate(agreement);

        auditWriter.record(AuditModule.PAYMENT, AuditAction.RECORD_PAYMENT,
                agreement.getProperty().getId(), saved.getId().toString(),
                "%s recorded a UGX %,.0f payment for %s (%s).".formatted(
                        JwtUtils.getCurrentUserName(), request.amount(),
                        agreement.getTenant().getName(), agreement.getUnit().getRoomNumber()));

        return PaymentResponse.from(saved, retainedByCycle(saved));
    }

    /**
     * Corrects a payment already recorded — a mis-typed amount, the wrong date,
     * the wrong billing period.
     *
     * <p>Allowed on a terminated agreement, unlike recording: a payment
     * mis-keyed before move-out would otherwise be uncorrectable forever, which
     * is the whole thing this exists to prevent. Recording a *new* payment onto
     * a terminated agreement stays refused.
     */
    @Transactional
    public PaymentResponse updatePayment(UUID id, PaymentRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Payment payment = requireCorrectableCashRow(id, landlordId);
        RentalAgreement agreement = lockAgreement(request.agreementId(), landlordId);

        // Moving a payment between agreements would rewrite the denormalised
        // property/tenant/unit columns, cross the property-access boundary, and
        // leave a single audit row carrying one propertyId — so a cross-property
        // move would vanish from one property's activity feed entirely.
        // Delete-and-re-record produces two correctly-scoped rows instead.
        if (!agreement.getId().equals(payment.getAgreement().getId())) {
            throw new ConflictException(
                    "A payment cannot be moved to a different agreement. "
                            + "Delete it and record it again on the correct one.");
        }

        List<String> changes = new ArrayList<>();
        AuditDiff.diff(changes, "amount", payment.getAmount(), request.amount());
        AuditDiff.diff(changes, "payment date", payment.getPaymentDate(), request.paymentDate());
        AuditDiff.diff(changes, "period start", payment.getPeriodStartDate(), request.periodStartDate());
        AuditDiff.diff(changes, "period end", payment.getPeriodEndDate(), request.periodEndDate());
        AuditDiff.diff(changes, "method", payment.getMethod(), request.method());
        AuditDiff.diff(changes, "reference", payment.getReference(), request.reference());
        AuditDiff.diff(changes, "notes", payment.getNotes(), request.notes());

        payment.setPaymentDate(request.paymentDate());
        payment.setAmount(request.amount());
        payment.setMethod(request.method());
        payment.setPeriodStartDate(request.periodStartDate());
        payment.setPeriodEndDate(request.periodEndDate());
        payment.setExpectedAmount(agreement.getRentAmount());
        payment.setReference(request.reference());
        payment.setNotes(request.notes());

        Payment saved = paymentRepository.saveAndFlush(payment);

        var result = allocationService.reallocate(agreement);
        if (result.rolloversBefore() != result.rolloversAfter()) {
            changes.add("carried-forward credit rebuilt (%d rows → %d)"
                    .formatted(result.rolloversBefore(), result.rolloversAfter()));
        }

        // An edit that moved nothing is not an event worth a row in the feed.
        if (!changes.isEmpty()) {
            auditWriter.record(AuditModule.PAYMENT, AuditAction.UPDATE,
                    agreement.getProperty().getId(), saved.getId().toString(),
                    "%s edited a payment for %s (%s): %s.".formatted(
                            JwtUtils.getCurrentUserName(), agreement.getTenant().getName(),
                            agreement.getUnit().getRoomNumber(), String.join("; ", changes)));
        }

        return PaymentResponse.from(saved, retainedByCycle(saved));
    }

    /**
     * Removes a payment recorded in error and re-derives everything that leaned
     * on it. The row goes; the audit trail keeps what it was, since a deleted
     * row can no longer speak for itself.
     */
    @Transactional
    public void deletePayment(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Payment payment = requireCorrectableCashRow(id, landlordId);
        RentalAgreement agreement = lockAgreement(payment.getAgreement().getId(), landlordId);

        // Captured before the delete — nothing below can read the row.
        String statement = "%s deleted a UGX %,.0f payment for %s (%s) covering %s – %s, received on %s.".formatted(
                JwtUtils.getCurrentUserName(), payment.getAmount(),
                payment.getTenant().getName(), payment.getUnit().getRoomNumber(),
                payment.getPeriodStartDate(), payment.getPeriodEndDate(),
                payment.getPaymentDate());
        UUID propertyId = payment.getProperty().getId();

        paymentRepository.delete(payment);
        // The DELETE must reach the database before the replay reads the rows,
        // or it allocates against the payment it is meant to be removing.
        paymentRepository.flush();

        allocationService.reallocate(agreement);

        auditWriter.record(AuditModule.PAYMENT, AuditAction.DELETE,
                propertyId, id.toString(), statement);
    }

    /**
     * The payment a correction may act on: one this account owns, at a property
     * the caller can reach, and actually a record of money received.
     */
    private Payment requireCorrectableCashRow(UUID id, UUID landlordId) {
        Payment payment = paymentRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        propertyAccessGuard.assertCanAccess(payment.getProperty().getId());

        // A ROLLOVER row is derived, not received: it re-labels cash already
        // recorded on the CASH row that spawned it. Editing one by hand would
        // be undone by the next replay anyway.
        if (payment.getSource() == PaymentSource.ROLLOVER) {
            throw new ConflictException(
                    "This is carried-forward credit, not a payment received. "
                            + describeFunder(payment));
        }
        return payment;
    }

    private String describeFunder(Payment rollover) {
        return paymentRepository.findById(
                        rollover.getFundedByPaymentId() != null
                                ? rollover.getFundedByPaymentId() : rollover.getId())
                .filter(p -> p.getSource() == PaymentSource.CASH)
                .map(p -> "Correct the UGX %,.0f payment received on %s that it was carried forward from."
                        .formatted(p.getAmount(), p.getPaymentDate()))
                .orElse("Correct the payment it was carried forward from instead.");
    }

    private RentalAgreement lockAgreement(UUID agreementId, UUID landlordId) {
        RentalAgreement agreement = agreementRepository
                .findByIdAndLandlordIdForUpdate(agreementId, landlordId)
                .orElseThrow(() -> new NotFoundException("Agreement not found"));
        propertyAccessGuard.assertCanAccess(agreement.getProperty().getId());
        return agreement;
    }

    private BigDecimal retainedByCycle(Payment payment) {
        BigDecimal sum = paymentRepository.sumRetainedByAgreementAndCycle(
                payment.getAgreement().getId(),
                payment.getPeriodStartDate(), payment.getPeriodEndDate());
        return sum != null ? sum : BigDecimal.ZERO;
    }
}
