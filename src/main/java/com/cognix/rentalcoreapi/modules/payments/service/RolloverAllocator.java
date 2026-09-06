package com.cognix.rentalcoreapi.modules.payments.service;

import com.cognix.rentalcoreapi.shared.util.BillingCycleUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The one implementation of how received cash spreads across billing cycles.
 *
 * <p>Pure by construction: it takes the CASH rows an agreement holds, in the
 * order they were recorded, and returns what every derived figure must be —
 * each CASH row's {@code overpayment}, the whole ROLLOVER chain, and the total
 * applied to opening arrears. It reads no repository, builds no entity and
 * reads no clock, so recording a payment and replaying an agreement after a
 * correction cannot be two copies of the same arithmetic that drift apart.
 *
 * <p>That drift is not hypothetical: it is exactly what
 * {@code V30__rebuild_payment_rollover_allocation.sql} had to repair, because
 * the allocation could only be re-expressed in SQL rather than reused. This
 * class is the Java statement of that replay; V30 is its statement in SQL.
 */
@Component
public class RolloverAllocator {

    /**
     * Beyond this many cycles ahead, stop hunting for an open slot for leftover
     * credit — guards against runaway walking if a tenant has an implausibly
     * long run of already-covered cycles. Matches {@code max_lookahead} in
     * V30__rebuild_payment_rollover_allocation.sql.
     */
    public static final int MAX_LOOKAHEAD_CYCLES = 120;

    /** A CASH row reduced to only what allocation depends on. */
    public record CashRow(UUID id, BigDecimal amount,
                          LocalDate periodStart, LocalDate periodEnd) {
    }

    /** A ROLLOVER row the chain requires, in the order it must be written. */
    public record RolloverRow(UUID fundedBy, BigDecimal amount,
                              LocalDate periodStart, LocalDate periodEnd) {
    }

    /**
     * @param overpaymentByCashId what each CASH row's {@code overpayment} must be
     * @param rollovers           the chain the agreement's cycles require
     * @param arrearsPaid         total applied to opening arrears (rows filed
     *                            before cycle tracking begins)
     */
    public record Allocation(Map<UUID, BigDecimal> overpaymentByCashId,
                             List<RolloverRow> rollovers,
                             BigDecimal arrearsPaid) {
    }

    /**
     * A cycle is identified by its start AND end. {@code PaymentRequest} only
     * validates {@code end >= start}, so non-canonical periods exist in the
     * data, and every consumer that reads a cycle — {@code computeCycleStatuses},
     * {@code PeriodPaidLookup}, {@code sumRetainedByAgreementAndCycle} — keys on
     * the pair. Keying on the start alone (as V30's SQL does) would let a
     * non-canonical row satisfy a canonical cycle's need during the replay while
     * remaining invisible to the ledger.
     */
    private record CycleKey(LocalDate start, LocalDate end) {
    }

    public Allocation allocate(BigDecimal rent, int billingDay, LocalDate cutoff,
                               List<CashRow> cashInRecordedOrder) {
        return allocate(rent, billingDay, cutoff, cashInRecordedOrder, MAX_LOOKAHEAD_CYCLES);
    }

    /**
     * {@code maxLookahead} is a parameter only so the bounded-walk test can use
     * a small cap instead of seeding 121 cycles. Production always uses the
     * public overload.
     */
    Allocation allocate(BigDecimal rent, int billingDay, LocalDate cutoff,
                        List<CashRow> cashInRecordedOrder, int maxLookahead) {

        Map<UUID, BigDecimal> overpayments = new LinkedHashMap<>();
        List<RolloverRow> rollovers = new ArrayList<>();
        Map<CycleKey, BigDecimal> retained = new HashMap<>();
        BigDecimal arrearsPaid = BigDecimal.ZERO;

        for (CashRow row : cashInRecordedOrder) {

            // A payment for a period before cycle-tracking begins isn't for any
            // billing cycle at all — it's the tenant paying down pre-existing
            // arrears. It neither takes credit from a cycle nor spills into one.
            if (row.periodStart().isBefore(cutoff)) {
                overpayments.put(row.id(), BigDecimal.ZERO);
                arrearsPaid = arrearsPaid.add(row.amount());
                continue;
            }

            // Sized off what the cycle STILL needs, not off the bare rent. A
            // cycle already holding part of its rent must not be charged for it
            // twice — and the difference would not resurface anywhere: it would
            // simply never enter the rollover chain.
            BigDecimal applied = row.amount().min(need(rent, retained, row.periodStart(), row.periodEnd()));
            BigDecimal spill = row.amount().subtract(applied);

            credit(retained, row.periodStart(), row.periodEnd(), applied);
            overpayments.put(row.id(), spill);

            // A zero rent leaves every downstream cycle needing nothing, so
            // there is nowhere to carry the spill and the walk would burn its
            // whole lookahead writing nothing.
            if (rent.signum() <= 0) {
                continue;
            }

            LocalDate cycleStart = row.periodEnd().plusDays(1);
            for (int depth = 0; spill.signum() > 0 && depth < maxLookahead; depth++) {
                LocalDate cycleEnd = BillingCycleUtils.cycleEnd(cycleStart, billingDay);

                // How much this cycle can still absorb. A cycle already holding
                // part of its rent — from cash, or from the tail of an earlier
                // chain — takes only the shortfall and the rest travels on. The
                // last row in a chain is partial by construction, so skipping
                // any cycle that merely *has* a rollover row would jump a
                // part-covered cycle and leave a permanent hole behind it.
                BigDecimal cycleNeed = need(rent, retained, cycleStart, cycleEnd);
                if (cycleNeed.signum() > 0) {
                    BigDecimal credit = spill.min(cycleNeed);
                    rollovers.add(new RolloverRow(row.id(), credit, cycleStart, cycleEnd));
                    credit(retained, cycleStart, cycleEnd, credit);
                    spill = spill.subtract(credit);
                }
                cycleStart = cycleEnd.plusDays(1);
            }
        }

        return new Allocation(overpayments, List.copyOf(rollovers), arrearsPaid);
    }

    /** What a billing cycle still needs: its rent less what it already retains. */
    private static BigDecimal need(BigDecimal rent, Map<CycleKey, BigDecimal> retained,
                                   LocalDate start, LocalDate end) {
        return rent.subtract(retained.getOrDefault(new CycleKey(start, end), BigDecimal.ZERO))
                .max(BigDecimal.ZERO);
    }

    private static void credit(Map<CycleKey, BigDecimal> retained,
                               LocalDate start, LocalDate end, BigDecimal amount) {
        retained.merge(new CycleKey(start, end), amount, BigDecimal::add);
    }
}
