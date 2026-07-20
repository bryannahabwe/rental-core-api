package com.cognix.rentalcoreapi.modules.payments.service;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.agreements.repository.RentalAgreementRepository;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.payments.dto.PaymentRequest;
import com.cognix.rentalcoreapi.modules.payments.dto.PaymentResponse;
import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentMethod;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentSource;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
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

    public PagedResponse<PaymentResponse> getAllPayments(
            Pageable pageable, UUID tenantId, UUID agreementId,
            String search, LocalDate from, LocalDate to) {

        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Page<Payment> page;
        if (from != null && to != null) {
            page = paymentRepository.findAllWithFiltersAndDates(
                    landlordId, tenantId, agreementId, search, from, to, pageable);
        } else {
            page = paymentRepository.findAllWithFilters(
                    landlordId, tenantId, agreementId, search, pageable);
        }

        return PagedResponse.from(page.map(PaymentResponse::from));
    }

    public PaymentResponse getPayment(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        return paymentRepository.findByIdAndLandlordId(id, landlordId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
    }

    // Beyond this many cycles ahead, stop hunting for an open slot for
    // leftover rollover credit — guards against runaway recursion if a
    // tenant has an implausibly long run of pre-existing rollover rows.
    private static final int MAX_ROLLOVER_LOOKAHEAD_CYCLES = 120;

    @Transactional
    public PaymentResponse recordPayment(PaymentRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        var agreement = agreementRepository.findByIdAndLandlordId(
                        request.agreementId(), landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Agreement not found"));

        if (agreement.getStatus() == AgreementStatus.TERMINATED) {
            throw new IllegalArgumentException(
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

        BigDecimal overpayment = appliesToOpeningArrears
                ? BigDecimal.ZERO
                : paidAmount.subtract(expectedAmount).max(BigDecimal.ZERO);

        Payment payment = Payment.builder()
                .landlord(userRepository.getReferenceById(landlordId))
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

        if (appliesToOpeningArrears) {
            agreement.setOpeningBalance(agreement.getOpeningBalance().add(paidAmount));
            agreementRepository.save(agreement);
        } else if (overpayment.compareTo(BigDecimal.ZERO) > 0) {
            createRolloverPayment(
                    agreement, overpayment,
                    request.periodEndDate().plusDays(1),
                    landlordId, request.paymentDate(), 0);
        }

        return PaymentResponse.from(saved);
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

        // If a rollover already exists for this cycle, it's already fully
        // credited (rollovers are always created for the full rent amount
        // unless they're the last one in a chain) — move on to the next
        // cycle instead of silently dropping this amount.
        boolean exists = paymentRepository
                .existsByAgreementIdAndPeriodStartDateAndSource(
                        agreement.getId(), nextCycleStart, PaymentSource.ROLLOVER);
        if (exists) {
            createRolloverPayment(agreement, rolloverAmount,
                    nextCycleEnd.plusDays(1), landlordId, originalPaymentDate, depth + 1);
            return;
        }

        BigDecimal expectedAmount = agreement.getRentAmount();
        BigDecimal actualRollover = rolloverAmount.min(expectedAmount);
        BigDecimal remainingOverpayment = rolloverAmount
                .subtract(expectedAmount).max(BigDecimal.ZERO);

        Payment rollover = Payment.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .tenant(agreement.getTenant())
                .unit(agreement.getUnit())
                .agreement(agreement)
                .paymentDate(originalPaymentDate)
                .amount(actualRollover)
                .method(PaymentMethod.CASH)
                .periodStartDate(nextCycleStart)
                .periodEndDate(nextCycleEnd)
                .expectedAmount(expectedAmount)
                .overpayment(remainingOverpayment)
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