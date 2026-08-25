package com.cognix.rentalcoreapi.modules.payments.service;

import com.cognix.rentalcoreapi.modules.payments.dto.CycleRetained;
import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves, for a batch of payment rows, how much the billing cycle behind
 * each row has been paid in total.
 *
 * <p>A payment row on its own cannot answer "is this period settled?" — a
 * cycle is routinely covered by several rows (the tail of a rollover chain
 * plus a cash top-up, say), and judging each row against the full rent makes
 * every one of them read PARTIAL no matter how much the period holds. The
 * cycle totals come back in a single grouped query per batch rather than one
 * per row.
 */
@Component
@RequiredArgsConstructor
public class PeriodPaidLookup {

    private final PaymentRepository paymentRepository;

    /** Cycle identity — a period is only ever shared within one agreement. */
    private record CycleKey(UUID agreementId, LocalDate start, LocalDate end) {
        static CycleKey of(Payment p) {
            return new CycleKey(p.getAgreement().getId(),
                    p.getPeriodStartDate(), p.getPeriodEndDate());
        }

        static CycleKey of(CycleRetained c) {
            return new CycleKey(c.agreementId(), c.periodStartDate(), c.periodEndDate());
        }
    }

    /** Cycle totals for a batch of payments, resolved in one query. */
    public Index forPayments(Collection<Payment> payments) {
        Set<UUID> agreementIds = payments.stream()
                .map(p -> p.getAgreement().getId())
                .collect(Collectors.toSet());

        // `IN ()` is not valid SQL — an empty page needs no query at all.
        if (agreementIds.isEmpty()) {
            return new Index(Map.of());
        }

        Map<CycleKey, BigDecimal> totals = new HashMap<>();
        for (CycleRetained row : paymentRepository.findRetainedByCycle(agreementIds)) {
            totals.put(CycleKey.of(row), row.retained());
        }
        return new Index(totals);
    }

    /**
     * Cycle totals derived from an already-complete set of rows — used by the
     * tenant ledger, which has loaded every payment on the agreement and so
     * needs no further round trip.
     */
    public static Index fromCompleteHistory(Collection<Payment> allPayments) {
        Map<CycleKey, BigDecimal> totals = new HashMap<>();
        for (Payment p : allPayments) {
            totals.merge(CycleKey.of(p),
                    p.getAmount().subtract(p.getOverpayment()), BigDecimal::add);
        }
        return new Index(totals);
    }

    public static final class Index {
        private final Map<CycleKey, BigDecimal> totals;

        private Index(Map<CycleKey, BigDecimal> totals) {
            this.totals = totals;
        }

        /**
         * Total retained by this row's period. Falls back to the row's own
         * retained amount, which is what a period holds when this is the only
         * row on it — never a figure that overstates the period.
         */
        public BigDecimal paidFor(Payment p) {
            return totals.getOrDefault(CycleKey.of(p),
                    p.getAmount().subtract(p.getOverpayment()));
        }
    }
}
