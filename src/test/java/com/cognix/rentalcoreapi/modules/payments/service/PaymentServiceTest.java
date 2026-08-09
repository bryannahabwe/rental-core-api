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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    void existingRolloverInNextCycle_skipsForwardWithoutDroppingAmount() {
        // The immediate next cycle already carries a rollover, so the credit
        // must land on the cycle after it rather than being silently dropped.
        LocalDate nextStart = CYCLE_END.plusDays(1);           // 2026-02-01
        when(paymentRepository.existsByAgreementIdAndPeriodStartDateAndSource(
                eq(AGREEMENT), eq(nextStart), eq(PaymentSource.ROLLOVER)))
                .thenReturn(true);

        paymentService.recordPayment(request(RENT.multiply(BigDecimal.valueOf(2)), CYCLE_START, CYCLE_END));

        Payment rollover = captureSingleRolloverRow();
        // Feb was occupied, so the credit lands on the March cycle instead.
        assertThat(rollover.getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(rollover.getAmount()).isEqualByComparingTo(RENT);
    }

    @Test
    void rolloverRecursionIsBounded_whenEveryCycleAlreadyHasOne() {
        // Every cycle already has a rollover: the search must terminate at the
        // lookahead cap instead of recursing forever, and write nothing.
        when(paymentRepository.existsByAgreementIdAndPeriodStartDateAndSource(
                any(), any(), eq(PaymentSource.ROLLOVER)))
                .thenReturn(true);

        paymentService.recordPayment(request(RENT.multiply(BigDecimal.valueOf(2)), CYCLE_START, CYCLE_END));

        // The cash row is still written; no rollover row can be placed anywhere.
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    // ── helpers ──────────────────────────────────────────────────────────

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
