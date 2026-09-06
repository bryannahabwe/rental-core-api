package com.cognix.rentalcoreapi.modules.payments.service;

import com.cognix.rentalcoreapi.modules.payments.service.RolloverAllocator.Allocation;
import com.cognix.rentalcoreapi.modules.payments.service.RolloverAllocator.CashRow;
import com.cognix.rentalcoreapi.modules.payments.service.RolloverAllocator.RolloverRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The money invariants live here. No Mockito, no Spring, no datasource — the
 * allocator takes the CASH rows an agreement holds and returns what every
 * derived figure must be, so a test states the actual prior state ("January
 * already holds a 30k payment") rather than a stubbed aggregate standing in
 * for it.
 */
class RolloverAllocatorTest {

    private static final BigDecimal RENT = new BigDecimal("100000.00");
    private static final LocalDate CUTOFF = LocalDate.of(2026, 1, 1);
    private static final int BILLING_DAY = 1;

    private static final LocalDate JAN_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_END = LocalDate.of(2026, 1, 31);
    private static final LocalDate FEB_START = LocalDate.of(2026, 2, 1);
    private static final LocalDate FEB_END = LocalDate.of(2026, 2, 28);
    private static final LocalDate MAR_START = LocalDate.of(2026, 3, 1);
    private static final LocalDate MAR_END = LocalDate.of(2026, 3, 31);

    private final RolloverAllocator allocator = new RolloverAllocator();

    private final List<CashRow> rows = new ArrayList<>();

    /** Appends a CASH row in recorded order and returns its id. */
    private UUID cash(String amount, LocalDate start, LocalDate end) {
        UUID id = UUID.randomUUID();
        rows.add(new CashRow(id, new BigDecimal(amount), start, end));
        return id;
    }

    private Allocation allocate() {
        return allocator.allocate(RENT, BILLING_DAY, CUTOFF, List.copyOf(rows));
    }

    @Test
    void exactPayment_leavesNoSpillAndNoChain() {
        UUID id = cash("100000.00", JAN_START, JAN_END);

        Allocation result = allocate();

        assertThat(result.overpaymentByCashId().get(id)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.rollovers()).isEmpty();
        assertThat(result.arrearsPaid()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void overpayment_spillsOneRolloverIntoNextCycle() {
        UUID id = cash("200000.00", JAN_START, JAN_END);

        Allocation result = allocate();

        assertThat(result.overpaymentByCashId().get(id)).isEqualByComparingTo(RENT);
        assertThat(result.rollovers()).singleElement().satisfies(r -> {
            assertThat(r.fundedBy()).isEqualTo(id);
            assertThat(r.amount()).isEqualByComparingTo(RENT);
            assertThat(r.periodStart()).isEqualTo(FEB_START);
            assertThat(r.periodEnd()).isEqualTo(FEB_END);
        });
    }

    @Test
    void overpayment_spillsAcrossMultipleCycles_eachTakingAFullRent() {
        // Three months at once: January keeps one, February and March take one
        // each. No row carries a leftover of its own — the remainder is always
        // the NEXT row, or `amount - overpayment` would net to zero for every
        // middle cycle.
        UUID id = cash("300000.00", JAN_START, JAN_END);

        Allocation result = allocate();

        assertThat(result.overpaymentByCashId().get(id))
                .isEqualByComparingTo(RENT.multiply(BigDecimal.valueOf(2)));
        assertThat(result.rollovers()).hasSize(2);
        assertThat(result.rollovers()).allSatisfy(r ->
                assertThat(r.amount()).isEqualByComparingTo(RENT));
        assertThat(startsOf(result)).containsExactly(FEB_START, MAR_START);
    }

    @Test
    void fullyCoveredNextCycle_skipsForwardWithoutDroppingAmount() {
        // February was already settled in its own right, so the spill from a
        // January lump sum has to travel past it rather than pile on.
        cash("100000.00", FEB_START, FEB_END);
        cash("200000.00", JAN_START, JAN_END);

        Allocation result = allocate();

        assertThat(result.rollovers()).singleElement().satisfies(r -> {
            assertThat(r.periodStart()).isEqualTo(MAR_START);
            assertThat(r.amount()).isEqualByComparingTo(RENT);
        });
    }

    @Test
    void partPaidSourceCycle_rollsForwardOnlyWhatItDidNotNeed() {
        // The reported defect in miniature: a cycle already holding part of its
        // rent must not be charged for that part twice. Sizing the spill off the
        // bare rent would roll 100k here instead of 130k, and the 30k would
        // simply stop existing as credit.
        cash("30000.00", JAN_START, JAN_END);
        UUID lump = cash("230000.00", JAN_START, JAN_END);

        Allocation result = allocate();

        assertThat(result.overpaymentByCashId().get(lump))
                .isEqualByComparingTo(new BigDecimal("160000.00"));
        assertThat(result.rollovers()).hasSize(2);
        assertThat(result.rollovers().get(0).amount()).isEqualByComparingTo(RENT);
        assertThat(result.rollovers().get(1).amount()).isEqualByComparingTo(new BigDecimal("60000.00"));
    }

    @Test
    void partCoveredDownstreamCycle_isToppedUpRatherThanSkipped() {
        // February holds 40k. The last row of any chain is partial by
        // construction, so skipping a cycle that merely *has* credit would jump
        // it entirely and leave a permanent hole behind.
        cash("40000.00", FEB_START, FEB_END);
        cash("200000.00", JAN_START, JAN_END);

        Allocation result = allocate();

        assertThat(result.rollovers()).hasSize(2);
        assertThat(result.rollovers().get(0).periodStart()).isEqualTo(FEB_START);
        assertThat(result.rollovers().get(0).amount()).isEqualByComparingTo(new BigDecimal("60000.00"));
        assertThat(result.rollovers().get(1).periodStart()).isEqualTo(MAR_START);
        assertThat(result.rollovers().get(1).amount()).isEqualByComparingTo(new BigDecimal("40000.00"));
    }

    @Test
    void paymentIntoAlreadySettledCycle_rollsForwardInFull() {
        cash("100000.00", JAN_START, JAN_END);
        UUID second = cash("100000.00", JAN_START, JAN_END);

        Allocation result = allocate();

        assertThat(result.overpaymentByCashId().get(second)).isEqualByComparingTo(RENT);
        assertThat(result.rollovers()).singleElement()
                .satisfies(r -> assertThat(r.periodStart()).isEqualTo(FEB_START));
    }

    @Test
    void reportedScenario_lumpSumClearsEveryCycleItWasCollectedFor() {
        // 180k rent. April already holds 110k; a 610k lump sum arrives against
        // April. April→July must each end up holding a full month, and August
        // must be left alone.
        BigDecimal rent = new BigDecimal("180000.00");
        LocalDate aprStart = LocalDate.of(2026, 4, 1);
        LocalDate aprEnd = LocalDate.of(2026, 4, 30);

        List<CashRow> history = List.of(
                new CashRow(UUID.randomUUID(), new BigDecimal("110000.00"), aprStart, aprEnd),
                new CashRow(UUID.randomUUID(), new BigDecimal("610000.00"), aprStart, aprEnd));

        Allocation result = allocator.allocate(rent, 1, aprStart, history);

        assertThat(result.rollovers()).hasSize(3);
        assertThat(result.rollovers()).allSatisfy(r ->
                assertThat(r.amount()).isEqualByComparingTo(rent));
        assertThat(startsOf(result)).containsExactly(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
    }

    @Test
    void openingArrears_takeNoCycleCreditAndSpillNowhere() {
        LocalDate beforeStart = CUTOFF.minusMonths(1);
        UUID id = cash("200000.00", beforeStart, beforeStart.plusDays(30));

        Allocation result = allocate();

        assertThat(result.overpaymentByCashId().get(id)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.rollovers()).isEmpty();
        assertThat(result.arrearsPaid()).isEqualByComparingTo(new BigDecimal("200000.00"));
    }

    @Test
    void replayIsIdempotent() {
        cash("30000.00", JAN_START, JAN_END);
        cash("350000.00", JAN_START, JAN_END);

        Allocation first = allocate();
        Allocation second = allocate();

        assertThat(second.overpaymentByCashId()).isEqualTo(first.overpaymentByCashId());
        assertThat(second.rollovers()).isEqualTo(first.rollovers());
        assertThat(second.arrearsPaid()).isEqualByComparingTo(first.arrearsPaid());
    }

    @Test
    void recordOrderDecidesWhichRowCarriesTheSpill() {
        // Both orders are individually correct and put the same money on the
        // same cycles — but the row that lands second is the one that spills.
        // This pins the (createdAt, id) contract the replay walks in.
        UUID small = cash("30000.00", JAN_START, JAN_END);
        UUID large = cash("200000.00", JAN_START, JAN_END);
        Allocation smallFirst = allocate();

        rows.clear();
        rows.add(new CashRow(large, new BigDecimal("200000.00"), JAN_START, JAN_END));
        rows.add(new CashRow(small, new BigDecimal("30000.00"), JAN_START, JAN_END));
        Allocation largeFirst = allocate();

        assertThat(smallFirst.overpaymentByCashId().get(large))
                .isEqualByComparingTo(new BigDecimal("130000.00"));
        assertThat(smallFirst.overpaymentByCashId().get(small))
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(largeFirst.overpaymentByCashId().get(large))
                .isEqualByComparingTo(RENT);
        assertThat(largeFirst.overpaymentByCashId().get(small))
                .isEqualByComparingTo(new BigDecimal("30000.00"));
    }

    @Test
    void zeroRentWritesNoChain() {
        // Every downstream cycle needs nothing, so there is nowhere to carry
        // the spill — and no reason to walk 120 cycles finding that out.
        UUID id = UUID.randomUUID();
        Allocation result = allocator.allocate(BigDecimal.ZERO, BILLING_DAY, CUTOFF,
                List.of(new CashRow(id, new BigDecimal("500000.00"), JAN_START, JAN_END)));

        assertThat(result.rollovers()).isEmpty();
        assertThat(result.overpaymentByCashId().get(id))
                .isEqualByComparingTo(new BigDecimal("500000.00"));
    }

    @Test
    void nonCanonicalPeriodDoesNotSatisfyACanonicalCycle() {
        // A row filed on 1–15 Jan is not the January cycle as far as the ledger
        // is concerned, so it must not absorb January's need during the replay
        // either. Keying a cycle on its start alone would let it.
        cash("100000.00", JAN_START, JAN_START.plusDays(14));
        UUID canonical = cash("100000.00", JAN_START, JAN_END);

        Allocation result = allocate();

        assertThat(result.overpaymentByCashId().get(canonical))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.rollovers()).isEmpty();
    }

    @Test
    void walkIsBoundedWhenNoDownstreamCycleCanAbsorbTheSpill() {
        // Three cycles ahead are all settled and the lookahead stops at three,
        // so the credit finds nowhere to land and the walk terminates rather
        // than running forever.
        List<CashRow> history = List.of(
                new CashRow(UUID.randomUUID(), RENT, FEB_START, FEB_END),
                new CashRow(UUID.randomUUID(), RENT, MAR_START, MAR_END),
                new CashRow(UUID.randomUUID(), RENT, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)),
                new CashRow(UUID.randomUUID(), new BigDecimal("200000.00"), JAN_START, JAN_END));

        Allocation result = allocator.allocate(RENT, BILLING_DAY, CUTOFF, history, 3);

        assertThat(result.rollovers()).isEmpty();
    }

    private static List<LocalDate> startsOf(Allocation result) {
        return result.rollovers().stream().map(RolloverRow::periodStart).toList();
    }
}
