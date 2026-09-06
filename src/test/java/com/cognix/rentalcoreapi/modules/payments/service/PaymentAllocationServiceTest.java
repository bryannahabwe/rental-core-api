package com.cognix.rentalcoreapi.modules.payments.service;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.BillingModel;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentMethod;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentSource;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;
import com.cognix.rentalcoreapi.modules.units.model.RentalUnit;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The replay against a real database — the assertions here are about STORED
 * state, which is where correcting a payment either works or quietly does not.
 *
 * <p>The headline property is that removing a payment leaves the agreement
 * exactly as it was before that payment: not just the same totals, but the same
 * rows on the same cycles and the same opening balance. Rolls back; needs the
 * same Postgres the context test does.
 */
@SpringBootTest
@Transactional
class PaymentAllocationServiceTest {

    private static final BigDecimal RENT = new BigDecimal("100000.00");
    private static final LocalDate JAN_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_END = LocalDate.of(2026, 1, 31);
    private static final LocalDate FEB_START = LocalDate.of(2026, 2, 1);
    private static final LocalDate MAR_START = LocalDate.of(2026, 3, 1);

    @Autowired private PaymentAllocationService allocationService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private EntityManager em;

    private User landlord;
    private RentalAgreement agreement;

    @BeforeEach
    void seed() {
        landlord = User.builder().name("Owner").email(UUID.randomUUID() + "@test.local").build();
        landlord.setId(UUID.randomUUID());
        landlord.setAccountOwnerId(landlord.getId());
        em.persist(landlord);

        Property property = Property.builder().landlord(landlord).name("Block A").build();
        em.persist(property);

        Tenant tenant = Tenant.builder().landlord(landlord).property(property).name("Jane").build();
        em.persist(tenant);

        RentalUnit unit = RentalUnit.builder().landlord(landlord).property(property)
                .roomNumber("A1").rentAmount(RENT).build();
        em.persist(unit);

        agreement = RentalAgreement.builder()
                .landlord(landlord).property(property).tenant(tenant).unit(unit)
                .rentAmount(RENT).status(AgreementStatus.ACTIVE)
                .startDate(JAN_START).billingDay(1).billingModel(BillingModel.ADVANCE)
                .openingBalance(BigDecimal.ZERO).openingBalanceEntered(BigDecimal.ZERO)
                .build();
        em.persist(agreement);
        em.flush();
    }

    // ── The headline invariant ───────────────────────────────────────────

    @Test
    void removingAPaymentRestoresTheAgreementExactlyAsItWas() {
        recordCash("100000.00", JAN_START, JAN_END);
        List<String> before = chainSnapshot();
        BigDecimal openingBefore = agreement.getOpeningBalance();

        Payment mistake = recordCash("300000.00", JAN_START, JAN_END);
        assertThat(chainSnapshot()).isNotEqualTo(before);

        removeCash(mistake);

        assertThat(chainSnapshot()).isEqualTo(before);
        assertThat(agreement.getOpeningBalance()).isEqualByComparingTo(openingBefore);
    }

    @Test
    void recordThenRemoveIsIdentity_forEveryShapeAPaymentTakes() {
        // A non-trivial history to add to and take away from, so the property
        // is tested against a chain that already exists rather than an empty one.
        recordCash("40000.00", JAN_START, JAN_END);
        recordCash("250000.00", JAN_START, JAN_END);
        List<String> before = chainSnapshot();
        BigDecimal openingBefore = agreement.getOpeningBalance();

        record Shape(String label, String amount, LocalDate start, LocalDate end) {
        }
        List<Shape> shapes = List.of(
                new Shape("exact", "100000.00", MAR_START, LocalDate.of(2026, 3, 31)),
                new Shape("overpay one cycle", "200000.00", MAR_START, LocalDate.of(2026, 3, 31)),
                new Shape("overpay three cycles", "400000.00", MAR_START, LocalDate.of(2026, 3, 31)),
                new Shape("into a part-paid cycle", "30000.00", JAN_START, JAN_END),
                new Shape("into a settled cycle", "100000.00", JAN_START, JAN_END),
                new Shape("opening arrears", "150000.00",
                        JAN_START.minusMonths(1), JAN_START.minusDays(1)));

        for (Shape shape : shapes) {
            Payment p = recordCash(shape.amount(), shape.start(), shape.end());
            removeCash(p);

            assertThat(chainSnapshot()).as("chain after %s", shape.label()).isEqualTo(before);
            assertThat(agreement.getOpeningBalance())
                    .as("opening balance after %s", shape.label())
                    .isEqualByComparingTo(openingBefore);
        }
    }

    // ── Editing ──────────────────────────────────────────────────────────

    @Test
    void editingAnAmountDownResizesTheChain_andBackUpRestoresIt() {
        Payment p = recordCash("300000.00", JAN_START, JAN_END);
        List<String> full = chainSnapshot();
        assertThat(rollovers()).hasSize(2);

        p.setAmount(new BigDecimal("150000.00"));
        paymentRepository.saveAndFlush(p);
        allocationService.reallocate(agreement);

        assertThat(rollovers()).singleElement().satisfies(r -> {
            assertThat(r.getPeriodStartDate()).isEqualTo(FEB_START);
            assertThat(r.getAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
        });
        // March is back to holding nothing.
        assertThat(retained(MAR_START, LocalDate.of(2026, 3, 31)))
                .isEqualByComparingTo(BigDecimal.ZERO);

        p.setAmount(new BigDecimal("300000.00"));
        paymentRepository.saveAndFlush(p);
        allocationService.reallocate(agreement);

        assertThat(chainSnapshot()).isEqualTo(full);
    }

    @Test
    void appendingAPaymentLeavesTheExistingChainRowsUntouched() {
        // The record path only ever adds to the chain, so the bag diff must
        // reuse every row rather than churn its ids — the tenant ledger orders
        // transactions by createdAt, and a rewritten row jumps to the end.
        recordCash("300000.00", JAN_START, JAN_END);
        List<UUID> idsBefore = rollovers().stream().map(Payment::getId).toList();

        recordCash("100000.00", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(rollovers().stream().map(Payment::getId)).containsAll(idsBefore);
    }

    // ── Opening arrears ──────────────────────────────────────────────────

    @Test
    void arrearsPaymentMovesTheOpeningBalance_andRemovingItPutsItBack() {
        agreement.setOpeningBalanceEntered(new BigDecimal("-200000.00"));
        allocationService.reallocate(agreement);
        assertThat(agreement.getOpeningBalance()).isEqualByComparingTo(new BigDecimal("-200000.00"));

        Payment arrears = recordCash("150000.00", JAN_START.minusMonths(1), JAN_START.minusDays(1));

        assertThat(agreement.getOpeningBalance()).isEqualByComparingTo(new BigDecimal("-50000.00"));
        // Arrears are not rent for any cycle, so nothing rolls forward.
        assertThat(rollovers()).isEmpty();

        removeCash(arrears);
        assertThat(agreement.getOpeningBalance()).isEqualByComparingTo(new BigDecimal("-200000.00"));
    }

    @Test
    void movingAPaymentFromArrearsIntoACycleShiftsItOffTheOpeningBalance() {
        agreement.setOpeningBalanceEntered(new BigDecimal("-200000.00"));
        Payment p = recordCash("150000.00", JAN_START.minusMonths(1), JAN_START.minusDays(1));
        assertThat(agreement.getOpeningBalance()).isEqualByComparingTo(new BigDecimal("-50000.00"));

        p.setPeriodStartDate(JAN_START);
        p.setPeriodEndDate(JAN_END);
        paymentRepository.saveAndFlush(p);
        allocationService.reallocate(agreement);

        assertThat(agreement.getOpeningBalance()).isEqualByComparingTo(new BigDecimal("-200000.00"));
        assertThat(retained(JAN_START, JAN_END)).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(rollovers()).singleElement()
                .satisfies(r -> assertThat(r.getAmount()).isEqualByComparingTo(new BigDecimal("50000.00")));
    }

    @Test
    void movingAPaymentFromACycleIntoArrearsTakesItsChainWithIt() {
        agreement.setOpeningBalanceEntered(new BigDecimal("-500000.00"));
        Payment p = recordCash("300000.00", JAN_START, JAN_END);
        assertThat(rollovers()).hasSize(2);

        p.setPeriodStartDate(JAN_START.minusMonths(1));
        p.setPeriodEndDate(JAN_START.minusDays(1));
        paymentRepository.saveAndFlush(p);
        allocationService.reallocate(agreement);

        assertThat(rollovers()).isEmpty();
        assertThat(agreement.getOpeningBalance()).isEqualByComparingTo(new BigDecimal("-200000.00"));
    }

    // ── Why the replay has to be whole-agreement ─────────────────────────

    @Test
    void removingTheEarliestPaymentReAllocatesEveryLaterOne() {
        // Undoing "just this payment's chain" gets this wrong: the second lump
        // sum's spill was sized against what the first had already covered, so
        // removing the first has to re-size the second's chain too.
        Payment first = recordCash("300000.00", JAN_START, JAN_END);
        recordCash("300000.00", JAN_START, JAN_END);
        assertThat(retained(MAR_START, LocalDate.of(2026, 3, 31))).isEqualByComparingTo(RENT);

        removeCash(first);

        // Only the surviving 300k remains: January, February and March each
        // hold one month, and nothing reaches April.
        assertThat(retained(JAN_START, JAN_END)).isEqualByComparingTo(RENT);
        assertThat(retained(FEB_START, LocalDate.of(2026, 2, 28))).isEqualByComparingTo(RENT);
        assertThat(retained(MAR_START, LocalDate.of(2026, 3, 31))).isEqualByComparingTo(RENT);
        assertThat(retained(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void replayingTwiceChangesNothingTheSecondTime() {
        recordCash("40000.00", JAN_START, JAN_END);
        recordCash("350000.00", JAN_START, JAN_END);
        List<String> once = chainSnapshot();
        List<UUID> ids = rollovers().stream().map(Payment::getId).toList();

        allocationService.reallocate(agreement);

        assertThat(chainSnapshot()).isEqualTo(once);
        // Idempotent down to the rows themselves, not merely the figures.
        assertThat(rollovers().stream().map(Payment::getId)).isEqualTo(ids);
    }

    @Test
    void everyRolloverNamesThePaymentThatFundedIt() {
        Payment funder = recordCash("300000.00", JAN_START, JAN_END);

        assertThat(rollovers()).isNotEmpty().allSatisfy(r ->
                assertThat(r.getFundedByPaymentId()).isEqualTo(funder.getId()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Writes a CASH row the way PaymentService does, then replays. */
    private Payment recordCash(String amount, LocalDate start, LocalDate end) {
        Payment payment = Payment.builder()
                .landlord(landlord)
                .property(agreement.getProperty())
                .tenant(agreement.getTenant())
                .unit(agreement.getUnit())
                .agreement(agreement)
                .paymentDate(LocalDate.of(2026, 1, 5))
                .amount(new BigDecimal(amount))
                .method(PaymentMethod.CASH)
                .periodStartDate(start)
                .periodEndDate(end)
                .expectedAmount(agreement.getRentAmount())
                .overpayment(BigDecimal.ZERO)
                .source(PaymentSource.CASH)
                .build();
        Payment saved = paymentRepository.saveAndFlush(payment);
        allocationService.reallocate(agreement);
        return saved;
    }

    private void removeCash(Payment payment) {
        paymentRepository.delete(payment);
        paymentRepository.flush();
        allocationService.reallocate(agreement);
    }

    /**
     * Every row on the agreement as a comparable tuple. Deliberately excludes
     * ids and createdAt: what has to come back after a correction is the money
     * on the cycles, not the identity of derived rows.
     */
    private List<String> chainSnapshot() {
        em.flush();
        return paymentRepository.findAllByAgreementIdOrderByCreatedAtAscIdAsc(agreement.getId())
                .stream()
                .map(p -> "%s %s %s..%s amount=%s overpayment=%s".formatted(
                        p.getSource(), p.getPaymentDate(),
                        p.getPeriodStartDate(), p.getPeriodEndDate(),
                        p.getAmount().stripTrailingZeros().toPlainString(),
                        p.getOverpayment().stripTrailingZeros().toPlainString()))
                .sorted()
                .toList();
    }

    private List<Payment> rollovers() {
        em.flush();
        return paymentRepository.findAllByAgreementIdOrderByCreatedAtAscIdAsc(agreement.getId())
                .stream()
                .filter(p -> p.getSource() == PaymentSource.ROLLOVER)
                .sorted(Comparator.comparing(Payment::getPeriodStartDate))
                .toList();
    }

    /** What a cycle holds: SUM(amount - overpayment), the figure the ledger reads. */
    private BigDecimal retained(LocalDate start, LocalDate end) {
        em.flush();
        BigDecimal sum = paymentRepository
                .sumRetainedByAgreementAndCycle(agreement.getId(), start, end);
        return sum != null ? sum : BigDecimal.ZERO;
    }
}
