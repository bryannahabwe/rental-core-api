package com.cognix.rentalcoreapi.modules.payments.service;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.BillingModel;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.agreements.repository.RentalAgreementRepository;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.payments.dto.PaymentRequest;
import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentMethod;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentSource;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;
import com.cognix.rentalcoreapi.modules.units.model.RentalUnit;
import com.cognix.rentalcoreapi.shared.exception.ConflictException;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import com.cognix.rentalcoreapi.shared.security.PropertyAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the payment recording + overpayment-rollover engine. Pure
 * Mockito — no Spring context, no datasource — with the {@link JwtUtils} static
 * lookups stubbed. The rollover chain is the money-critical logic here, so the
 * assertions capture the actual {@link Payment} rows the service builds.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    private static final UUID LANDLORD = UUID.randomUUID();
    private static final UUID AGREEMENT = UUID.randomUUID();
    private static final BigDecimal RENT = new BigDecimal("100000.00");
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate CYCLE_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate CYCLE_END = LocalDate.of(2026, 1, 31);

    @Mock private PaymentRepository paymentRepository;
    @Mock private RentalAgreementRepository agreementRepository;
    @Mock private UserRepository userRepository;
    @Mock private PropertyAccessGuard propertyAccessGuard;
    @Mock private AuditWriter auditWriter;
    @Mock private PeriodPaidLookup periodPaidLookup;

    @InjectMocks private PaymentService paymentService;

    private MockedStatic<JwtUtils> jwt;
    private RentalAgreement agreement;

    @BeforeEach
    void setUp() {
        jwt = org.mockito.Mockito.mockStatic(JwtUtils.class);
        jwt.when(JwtUtils::getCurrentLandlordId).thenReturn(LANDLORD);
        jwt.when(JwtUtils::getCurrentUserName).thenReturn("Tester");

        Property property = new Property();
        property.setId(UUID.randomUUID());
        property.setName("Block A");
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Jane");
        RentalUnit unit = new RentalUnit();
        unit.setId(UUID.randomUUID());
        unit.setRoomNumber("A1");

        agreement = RentalAgreement.builder()
                .property(property)
                .tenant(tenant)
                .unit(unit)
                .startDate(START)
                .rentAmount(RENT)
                .status(AgreementStatus.ACTIVE)
                .openingBalance(BigDecimal.ZERO)
                .billingDay(1)
                .billingModel(BillingModel.ADVANCE)
                .build();
        agreement.setId(AGREEMENT);

        when(agreementRepository.findByIdAndLandlordId(AGREEMENT, LANDLORD))
                .thenReturn(Optional.of(agreement));
        when(userRepository.getReferenceById(LANDLORD)).thenReturn(new User());
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Default: every cycle starts empty. Tests that need a cycle to
        // already hold money override this for the specific period.
        when(paymentRepository.sumRetainedByAgreementAndCycle(any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
    }

    @AfterEach
    void tearDown() {
        jwt.close();
    }

    private PaymentRequest request(BigDecimal amount, LocalDate periodStart, LocalDate periodEnd) {
        return new PaymentRequest(AGREEMENT, LocalDate.of(2026, 1, 5), amount,
                PaymentMethod.CASH, periodStart, periodEnd, null, null);
    }

    @Test
    void exactPayment_recordsCashRow_noRollover() {
        paymentService.recordPayment(request(RENT, CYCLE_START, CYCLE_END));

        Payment cash = captureCashRow();
        assertThat(cash.getSource()).isEqualTo(PaymentSource.CASH);
        assertThat(cash.getAmount()).isEqualByComparingTo(RENT);
        assertThat(cash.getOverpayment()).isEqualByComparingTo(BigDecimal.ZERO);

        // No overpayment → no rollover rows written.
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void overpayment_spillsOneRolloverIntoNextCycle() {
        // Pay two months at once: the source cycle keeps one month's rent and
        // one month's rent rolls forward to the next cycle.
        paymentService.recordPayment(request(RENT.multiply(BigDecimal.valueOf(2)), CYCLE_START, CYCLE_END));

        Payment cash = captureCashRow();
        assertThat(cash.getAmount()).isEqualByComparingTo(RENT.multiply(BigDecimal.valueOf(2)));
        assertThat(cash.getOverpayment()).isEqualByComparingTo(RENT); // one month spills

        Payment rollover = captureSingleRolloverRow();
        assertThat(rollover.getSource()).isEqualTo(PaymentSource.ROLLOVER);
        assertThat(rollover.getAmount()).isEqualByComparingTo(RENT);
        assertThat(rollover.getOverpayment()).isEqualByComparingTo(BigDecimal.ZERO);
        // Next cycle begins the day after this cycle ended.
        assertThat(rollover.getPeriodStartDate()).isEqualTo(CYCLE_END.plusDays(1));
    }

    @Test
    void multiCycleOverpayment_eachRolloverRowKeepsOneMonthAndCarriesNoOverpayment() {
        // Pay three months at once (the reported scenario: 600k against 200k
        // rent). The source cycle keeps one month; the surplus chains through
        // two rollover cycles. Each rollover row's amount is exactly one month
        // and its overpayment is 0 — so retained (amount - overpayment) is one
        // month's rent on every cycle and the middle cycle never collapses to 0.
        paymentService.recordPayment(
                request(RENT.multiply(BigDecimal.valueOf(3)), CYCLE_START, CYCLE_END));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(2)).save(captor.capture());

        for (Payment rollover : captor.getAllValues()) {
            assertThat(rollover.getSource()).isEqualTo(PaymentSource.ROLLOVER);
            assertThat(rollover.getAmount()).isEqualByComparingTo(RENT);
            assertThat(rollover.getOverpayment()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    void openingArrears_appliesToBalance_andBypassesRollover() {
        // A payment for a period before tracking begins is opening arrears —
        // it must not create a rollover and must bump the opening balance.
        LocalDate beforeStart = START.minusMonths(1);
        paymentService.recordPayment(
                request(RENT.multiply(BigDecimal.valueOf(2)), beforeStart, beforeStart.plusDays(30)));

        Payment cash = captureCashRow();
        assertThat(cash.getOverpayment()).isEqualByComparingTo(BigDecimal.ZERO);

        // Opening balance took the full amount; no rollover written.
        assertThat(agreement.getOpeningBalance())
                .isEqualByComparingTo(RENT.multiply(BigDecimal.valueOf(2)));
        verify(agreementRepository).save(agreement);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void terminatedAgreement_isRejectedAsConflict() {
        agreement.setStatus(AgreementStatus.TERMINATED);

        assertThatThrownBy(() -> paymentService.recordPayment(request(RENT, CYCLE_START, CYCLE_END)))
                .isInstanceOf(ConflictException.class);

        verify(paymentRepository, never()).saveAndFlush(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void fullyCoveredNextCycle_skipsForwardWithoutDroppingAmount() {
        // The immediate next cycle is already paid in full, so the credit must
        // land on the cycle after it rather than being silently dropped.
        LocalDate febStart = CYCLE_END.plusDays(1);            // 2026-02-01
        cycleHolds(febStart, LocalDate.of(2026, 2, 28), RENT);

        paymentService.recordPayment(request(RENT.multiply(BigDecimal.valueOf(2)), CYCLE_START, CYCLE_END));

        Payment rollover = captureSingleRolloverRow();
        // Feb needed nothing, so the credit lands on the March cycle instead.
        assertThat(rollover.getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(rollover.getAmount()).isEqualByComparingTo(RENT);
    }

    @Test
    void rolloverRecursionIsBounded_whenEveryCycleIsAlreadyCovered() {
        // Every cycle is already paid in full: the search must terminate at the
        // lookahead cap instead of recursing forever, and write nothing.
        when(paymentRepository.sumRetainedByAgreementAndCycle(any(), any(), any()))
                .thenReturn(RENT);

        paymentService.recordPayment(request(RENT.multiply(BigDecimal.valueOf(2)), CYCLE_START, CYCLE_END));

        // The cash row is still written; no rollover row can be placed anywhere.
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void partPaidSourceCycle_rollsForwardOnlyWhatItDidNotNeed() {
        // The reported bug. The source cycle already holds 30k of its 100k
        // rent, so it needs 70k — and a 350k lump sum must roll 280k forward,
        // not 250k. Sizing the spill off the bare rent charged this cycle its
        // full 100k a second time, and the 30k difference never reappeared:
        // the far end of the chain came up exactly that much short.
        cycleHolds(CYCLE_START, CYCLE_END, new BigDecimal("30000.00"));

        paymentService.recordPayment(
                request(new BigDecimal("350000.00"), CYCLE_START, CYCLE_END));

        Payment cash = captureCashRow();
        assertThat(cash.getOverpayment()).isEqualByComparingTo(new BigDecimal("280000.00"));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(3)).save(captor.capture());
        List<Payment> chain = captor.getAllValues();

        // Feb and Mar take a full month each; Apr takes the 80k tail.
        assertThat(chain.get(0).getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(chain.get(0).getAmount()).isEqualByComparingTo(RENT);
        assertThat(chain.get(1).getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(chain.get(1).getAmount()).isEqualByComparingTo(RENT);
        assertThat(chain.get(2).getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(chain.get(2).getAmount()).isEqualByComparingTo(new BigDecimal("80000.00"));
    }

    @Test
    void partCoveredDownstreamCycle_isToppedUpRatherThanSkipped() {
        // Feb holds 40k — the partial tail of an earlier rollover chain. It
        // needs 60k, and must absorb exactly that before the rest travels on.
        // The old dedup check treated any rollover row as a full month and
        // jumped the whole cycle, leaving Feb 60k short for good.
        cycleHolds(CYCLE_END.plusDays(1), LocalDate.of(2026, 2, 28), new BigDecimal("40000.00"));

        paymentService.recordPayment(
                request(RENT.multiply(BigDecimal.valueOf(2)), CYCLE_START, CYCLE_END));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(2)).save(captor.capture());
        List<Payment> chain = captor.getAllValues();

        assertThat(chain.get(0).getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(chain.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("60000.00"));
        assertThat(chain.get(0).getSource()).isEqualTo(PaymentSource.ROLLOVER);
        // The 40k Feb could not take carries on to March.
        assertThat(chain.get(1).getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(chain.get(1).getAmount()).isEqualByComparingTo(new BigDecimal("40000.00"));
    }

    @Test
    void paymentIntoAlreadySettledCycle_rollsForwardInFull() {
        // Nothing is owed on this cycle, so none of the payment sticks here.
        cycleHolds(CYCLE_START, CYCLE_END, RENT);

        paymentService.recordPayment(request(RENT, CYCLE_START, CYCLE_END));

        assertThat(captureCashRow().getOverpayment()).isEqualByComparingTo(RENT);
        assertThat(captureSingleRolloverRow().getPeriodStartDate())
                .isEqualTo(CYCLE_END.plusDays(1));
    }

    @Test
    void reportedScenario_lumpSumClearsEveryCycleItWasCollectedFor() {
        // Verbatim from the bug report. Rent 180k. April already holds 110k, so
        // the picker demanded 610k — 70k of April plus May, June and July in
        // full — and the tenant paid exactly that.
        //
        // Sized off the bare rent, April was charged its full 180k a second
        // time: only 430k entered the chain, July received 70k of its 180k, and
        // the tenant was asked for the same 110k twice. Sized off what April
        // still needed, all four cycles come out settled.
        agreement.setRentAmount(new BigDecimal("180000.00"));
        agreement.setStartDate(LocalDate.of(2026, 4, 1));

        LocalDate aprStart = LocalDate.of(2026, 4, 1);
        LocalDate aprEnd = LocalDate.of(2026, 4, 30);

        Map<LocalDate, BigDecimal> ledger = new HashMap<>();
        ledger.put(aprStart, new BigDecimal("110000.00"));
        trackRetainedIn(ledger);

        paymentService.recordPayment(
                request(new BigDecimal("610000.00"), aprStart, aprEnd));

        // 610k less the 70k April actually needed.
        assertThat(captureCashRow().getOverpayment())
                .isEqualByComparingTo(new BigDecimal("540000.00"));

        assertThat(ledger.get(aprStart)).isEqualByComparingTo(new BigDecimal("180000.00"));
        assertThat(ledger.get(LocalDate.of(2026, 5, 1))).isEqualByComparingTo(new BigDecimal("180000.00"));
        assertThat(ledger.get(LocalDate.of(2026, 6, 1))).isEqualByComparingTo(new BigDecimal("180000.00"));
        // The cycle the bug left 110k short.
        assertThat(ledger.get(LocalDate.of(2026, 7, 1))).isEqualByComparingTo(new BigDecimal("180000.00"));
        // Nothing spilled past the cycles the money was collected for.
        assertThat(ledger).doesNotContainKey(LocalDate.of(2026, 8, 1));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /**
     * Back the repository with a mutable per-cycle ledger, so a rollover chain
     * sees each cycle as the rows already written to it leave it — the way it
     * behaves against a real database, and the only way a multi-cycle chain
     * can be asserted end to end.
     */
    private void trackRetainedIn(Map<LocalDate, BigDecimal> ledger) {
        when(paymentRepository.sumRetainedByAgreementAndCycle(any(), any(), any()))
                .thenAnswer(inv -> ledger.getOrDefault(inv.getArgument(1), BigDecimal.ZERO));

        Answer<Payment> record = inv -> {
            Payment p = inv.getArgument(0);
            ledger.merge(p.getPeriodStartDate(),
                    p.getAmount().subtract(p.getOverpayment()), BigDecimal::add);
            return p;
        };
        when(paymentRepository.save(any(Payment.class))).thenAnswer(record);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(record);
    }

    /** Stub one cycle as already holding `retained` of its rent. */
    private void cycleHolds(LocalDate start, LocalDate end, BigDecimal retained) {
        when(paymentRepository.sumRetainedByAgreementAndCycle(
                eq(AGREEMENT), eq(start), eq(end)))
                .thenReturn(retained);
    }

    private Payment captureCashRow() {
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private Payment captureSingleRolloverRow() {
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        List<Payment> saved = captor.getAllValues();
        assertThat(saved).hasSize(1);
        return saved.get(0);
    }
}
