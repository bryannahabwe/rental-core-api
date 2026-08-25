package com.cognix.rentalcoreapi.modules.payments.service;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.agreements.repository.RentalAgreementRepository;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.payments.dto.PaymentRequest;
import com.cognix.rentalcoreapi.modules.payments.dto.PaymentResponse;
import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentMethod;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentSource;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import com.cognix.rentalcoreapi.shared.exception.ConflictException;
import com.cognix.rentalcoreapi.shared.exception.NotFoundException;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import com.cognix.rentalcoreapi.shared.security.PropertyAccessGuard;
import com.cognix.rentalcoreapi.shared.util.BillingCycleUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    // Beyond this many cycles ahead, stop hunting for an open slot for
    // leftover rollover credit — guards against runaway recursion if a
    // tenant has an implausibly long run of already-covered cycles.
    private static final int MAX_ROLLOVER_LOOKAHEAD_CYCLES = 120;

    @Transactional
    public PaymentResponse recordPayment(PaymentRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        var agreement = agreementRepository.findByIdAndLandlordId(
                        request.agreementId(), landlordId)
                .orElseThrow(() -> new NotFoundException("Agreement not found"));
        propertyAccessGuard.assertCanAccess(agreement.getProperty().getId());

        if (agreement.getStatus() == AgreementStatus.TERMINATED) {
            throw new ConflictException(
                    "Cannot record payment for a terminated agreement");
        }

        BigDecimal expectedAmount = agreement.getRentAmount();
        BigDecimal paidAmount = request.amount();

        // A payment for a period before cycle-tracking begins isn't for any
        // billing cycle at all — it's the tenant paying down pre-existing
        // arrears (the same balance the "Opening Balance" field represents).
        // Apply it there directly instead of running it through the normal
        // cycle/overpayment/rollover flow, which would otherwise either get
        // silently excluded from balance totals (correct, but arrears never
        // actually clear) or double-count it against unrelated later cycles.
        boolean appliesToOpeningArrears = request.periodStartDate()
                .isBefore(BillingCycleUtils.effectiveStartDate(agreement));

        // Sized off what the cycle STILL needs, not off the bare rent. A cycle
        // that already holds part of its rent must not be charged for it twice
        // — and it silently was: the difference simply never entered the
        // rollover chain, so a later cycle came up short by exactly the amount
        // already paid here.
        BigDecimal overpayment = appliesToOpeningArrears
                ? BigDecimal.ZERO
                : paidAmount.subtract(remainingNeed(
                                agreement, request.periodStartDate(), request.periodEndDate()))
                        .max(BigDecimal.ZERO);

        Payment payment = Payment.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .property(agreement.getProperty())
                .tenant(agreement.getTenant())
                .unit(agreement.getUnit())
                .agreement(agreement)
                .paymentDate(request.paymentDate())
                .amount(paidAmount)
                .method(request.method())
                .periodStartDate(request.periodStartDate())
                .periodEndDate(request.periodEndDate())
                .expectedAmount(expectedAmount)
                .overpayment(overpayment)
                .source(PaymentSource.CASH)
                .reference(request.reference())
                .notes(request.notes())
                .build();

        // saveAndFlush (not save) so @CreationTimestamp's INSERT-time
        // population actually happens before this response is built.
        Payment saved = paymentRepository.saveAndFlush(payment);

        auditWriter.record(AuditModule.PAYMENT, AuditAction.RECORD_PAYMENT,
                agreement.getProperty().getId(), agreement.getTenant().getName(),
                "%s recorded a UGX %,.0f payment for %s (%s).".formatted(
                        JwtUtils.getCurrentUserName(), paidAmount,
                        agreement.getTenant().getName(), agreement.getUnit().getRoomNumber()));

        if (appliesToOpeningArrears) {
            agreement.setOpeningBalance(agreement.getOpeningBalance().add(paidAmount));
            agreementRepository.save(agreement);
        } else if (overpayment.compareTo(BigDecimal.ZERO) > 0) {
            createRolloverPayment(
                    agreement, overpayment,
                    request.periodEndDate().plusDays(1),
                    landlordId, request.paymentDate(), 0);
        }

        return PaymentResponse.from(saved, retainedByCycle(saved));
    }

    /**
     * What a billing cycle still needs: its rent less what it already retains.
     *
     * <p>Every credit decision is sized off this rather than off the bare rent
     * — how much of a payment spills forward, and how much of that spill each
     * downstream cycle absorbs. Sizing off rent alone charges a cycle again for
     * money it already holds, and the difference does not resurface anywhere:
     * a 610k lump sum against an April already holding 110k rolled 430k instead
     * of 540k, leaving July 110k short of a month it had in fact been paid.
     */
    private BigDecimal remainingNeed(
            RentalAgreement agreement, LocalDate cycleStart, LocalDate cycleEnd) {

        return agreement.getRentAmount()
                .subtract(retained(agreement.getId(), cycleStart, cycleEnd))
                .max(BigDecimal.ZERO);
    }

    private BigDecimal retained(UUID agreementId, LocalDate cycleStart, LocalDate cycleEnd) {
        BigDecimal sum = paymentRepository.sumRetainedByAgreementAndCycle(
                agreementId, cycleStart, cycleEnd);
        return sum != null ? sum : BigDecimal.ZERO;
    }

    private BigDecimal retainedByCycle(Payment payment) {
        return retained(payment.getAgreement().getId(),
                payment.getPeriodStartDate(), payment.getPeriodEndDate());
    }

    private void createRolloverPayment(
            RentalAgreement agreement, BigDecimal rolloverAmount,
            LocalDate nextCycleStart, UUID landlordId, LocalDate originalPaymentDate,
            int depth) {

        if (rolloverAmount.compareTo(BigDecimal.ZERO) <= 0
                || depth >= MAX_ROLLOVER_LOOKAHEAD_CYCLES) {
            return;
        }

        int billingDay = agreement.getBillingDay();
        LocalDate nextCycleEnd = BillingCycleUtils.cycleEnd(nextCycleStart, billingDay);

        // How much this cycle can still absorb. A cycle already holding part of
        // its rent — from cash, or from the tail of an earlier rollover chain —
        // takes only the shortfall and the rest travels on.
        //
        // This replaces a check that skipped any cycle already carrying a
        // ROLLOVER row, on the assumption that rollover rows are always written
        // for a full month. The last row in a chain is partial by construction,
        // so a part-covered cycle was jumped over entirely and left underfunded
        // while the credit landed a month too late.
        BigDecimal need = remainingNeed(agreement, nextCycleStart, nextCycleEnd);

        if (need.compareTo(BigDecimal.ZERO) <= 0) {
            createRolloverPayment(agreement, rolloverAmount,
                    nextCycleEnd.plusDays(1), landlordId, originalPaymentDate, depth + 1);
            return;
        }

        BigDecimal actualRollover = rolloverAmount.min(need);
        BigDecimal remainingOverpayment = rolloverAmount.subtract(actualRollover);

        Payment rollover = Payment.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .property(agreement.getProperty())
                .tenant(agreement.getTenant())
                .unit(agreement.getUnit())
                .agreement(agreement)
                .paymentDate(originalPaymentDate)
                .amount(actualRollover)
                .method(PaymentMethod.CASH)
                .periodStartDate(nextCycleStart)
                .periodEndDate(nextCycleEnd)
                .expectedAmount(agreement.getRentAmount())
                // This row's `amount` is already the credit for THIS cycle
                // (capped at what the cycle still needed). Any leftover is
                // carried by the NEXT rollover row below — NOT by this row's
                // overpayment. Storing it here would make `amount - overpayment`
                // (the per-cycle retained figure) net to zero for every middle
                // cycle.
                .overpayment(BigDecimal.ZERO)
                .source(PaymentSource.ROLLOVER)
                .reference("Rollover from " + nextCycleStart.minusDays(1))
                .notes(null)
                .build();

        paymentRepository.save(rollover);

        if (remainingOverpayment.compareTo(BigDecimal.ZERO) > 0) {
            createRolloverPayment(agreement, remainingOverpayment,
                    nextCycleEnd.plusDays(1), landlordId, originalPaymentDate, depth + 1);
        }
    }
}
