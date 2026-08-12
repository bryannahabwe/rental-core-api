package com.cognix.rentalcoreapi.modules.agreements.service;

import com.cognix.rentalcoreapi.modules.agreements.dto.CycleStatusResponse;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentSource;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import com.cognix.rentalcoreapi.shared.util.BillingCycleUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The single source of truth for a rental agreement's outstanding balance.
 *
 * <p>Takes the agreement's per-cycle statuses as an argument (the caller
 * computes them via {@link RentalAgreementService#computeCycleStatuses}) rather
 * than depending on {@code RentalAgreementService} itself — that keeps the
 * dependency one-way ({@code RentalAgreementService} and {@code TenantService}
 * both depend on this, never the reverse) and avoids a bean cycle.
 */
@Component
@RequiredArgsConstructor
public class AgreementBalanceCalculator {

    private final PaymentRepository paymentRepository;

    public record BalanceSummary(
            BigDecimal totalEverOwed,
            BigDecimal totalEverPaid,
            BigDecimal outstanding,
            BigDecimal openingCredit,
            BigDecimal openingArrears
    ) {
    }

    public BalanceSummary summarize(RentalAgreement agreement, List<CycleStatusResponse> cycles) {
        // Sum of rent for every cycle actually due so far — derived from the
        // exact same per-cycle walk (computeCycleStatuses) the ledger and the
        // Payments page cycle picker use, rather than a separate closed-form
        // "cyclesElapsed × rent" formula. Two independent implementations of
        // "how many cycles are owed" can drift apart on edge cases (e.g. they
        // used to disagree for agreements with no explicit startDate); this
        // way there's exactly one. Each cycle already carries whether it is
        // `due` (computed via BillingCycleUtils.isDue in computeCycleStatuses),
        // so a future cycle offered only for paying ahead never counts as owed.
        BigDecimal dueExpected = BigDecimal.ZERO;
        for (CycleStatusResponse cycle : cycles) {
            if (cycle.due()) {
                dueExpected = dueExpected.add(cycle.expectedAmount());
            }
        }

        // Apply opening balance
        BigDecimal openingCredit = agreement.getOpeningBalance().max(BigDecimal.ZERO);
        BigDecimal openingArrears = agreement.getOpeningBalance().min(BigDecimal.ZERO).abs();
        BigDecimal totalEverOwed = dueExpected.subtract(openingCredit).add(openingArrears);

        // Same cutoff the cycle walk above starts counting cycles from — a
        // payment recorded for a period before this (a prior arrangement,
        // stray data entry, an import) was never counted as owed above, so
        // it must not be allowed to silently cancel out arrears on a later,
        // actually-due cycle. Also subtract overpayment already re-recorded
        // via rollover rows, so a lump-sum payment isn't credited twice
        // (once on the original CASH row, again on the ROLLOVER rows it spawned).
        LocalDate cutoff = BillingCycleUtils.effectiveStartDate(agreement);
        BigDecimal totalEverPaid = paymentRepository
                .sumByAgreementFromDate(agreement.getId(), cutoff)
                .subtract(paymentRepository.sumOverpaymentByAgreementAndSourceFromDate(
                        agreement.getId(), PaymentSource.CASH, cutoff));

        // Outstanding
        BigDecimal outstanding = totalEverOwed.subtract(totalEverPaid)
                .max(BigDecimal.ZERO);

        return new BalanceSummary(
                totalEverOwed, totalEverPaid, outstanding, openingCredit, openingArrears);
    }
}
