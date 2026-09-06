package com.cognix.rentalcoreapi.modules.payments.service;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.BillingModel;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.agreements.repository.RentalAgreementRepository;
import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.payments.dto.PaymentRequest;
import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentMethod;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentSource;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import com.cognix.rentalcoreapi.modules.settings.service.LandlordSettingsService;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;
import com.cognix.rentalcoreapi.modules.units.model.RentalUnit;
import com.cognix.rentalcoreapi.shared.exception.ConflictException;
import com.cognix.rentalcoreapi.shared.exception.NotFoundException;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import com.cognix.rentalcoreapi.shared.security.PropertyAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * What {@link PaymentService} itself owns: guards, ordering, and the audit
 * sentence. The allocation arithmetic lives in {@link RolloverAllocator} and is
 * tested against the real thing in {@link RolloverAllocatorTest}, so this class
 * mocks the replay and asserts only that it is invoked — including on the
 * record path, which is the test that stops a second copy of the arithmetic
 * being reintroduced here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    private static final UUID LANDLORD = UUID.randomUUID();
    private static final UUID AGREEMENT = UUID.randomUUID();
    private static final UUID PAYMENT = UUID.randomUUID();
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
    @Mock private PaymentAllocationService allocationService;
    @Mock private LandlordSettingsService settingsService;

    @InjectMocks private PaymentService paymentService;

    private MockedStatic<JwtUtils> jwt;
    private RentalAgreement agreement;
    private Payment existing;

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
                .openingBalanceEntered(BigDecimal.ZERO)
                .billingDay(1)
                .billingModel(BillingModel.ADVANCE)
                .build();
        agreement.setId(AGREEMENT);

        existing = Payment.builder()
                .landlord(new User())
                .property(property)
                .tenant(tenant)
                .unit(unit)
                .agreement(agreement)
                .paymentDate(LocalDate.of(2026, 1, 5))
                .amount(RENT)
                .method(PaymentMethod.CASH)
                .periodStartDate(CYCLE_START)
                .periodEndDate(CYCLE_END)
                .expectedAmount(RENT)
                .overpayment(BigDecimal.ZERO)
                .source(PaymentSource.CASH)
                .build();
        existing.setId(PAYMENT);

        when(agreementRepository.findByIdAndLandlordIdForUpdate(AGREEMENT, LANDLORD))
                .thenReturn(Optional.of(agreement));
        when(paymentRepository.findByIdAndLandlordId(PAYMENT, LANDLORD))
                .thenReturn(Optional.of(existing));
        when(userRepository.getReferenceById(LANDLORD)).thenReturn(new User());
        // Mirrors BaseEntity's @PrePersist: a saved row comes back with an id,
        // which the audit entry then references.
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(inv -> {
                    Payment p = inv.getArgument(0);
                    if (p.getId() == null) {
                        p.setId(UUID.randomUUID());
                    }
                    return p;
                });
        when(paymentRepository.sumRetainedByAgreementAndCycle(any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(allocationService.reallocate(any()))
                .thenReturn(new PaymentAllocationService.Result(0, 0, BigDecimal.ZERO));
    }

    @AfterEach
    void tearDown() {
        jwt.close();
    }

    private PaymentRequest request(BigDecimal amount, LocalDate periodStart, LocalDate periodEnd) {
        return new PaymentRequest(AGREEMENT, LocalDate.of(2026, 1, 5), amount,
                PaymentMethod.CASH, periodStart, periodEnd, null, null);
    }

    // ── Recording ────────────────────────────────────────────────────────

    @Test
    void recordingWritesTheCashRowThenReplaysTheAgreement() {
        paymentService.recordPayment(request(RENT, CYCLE_START, CYCLE_END));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(captor.capture());
        Payment cash = captor.getValue();
        assertThat(cash.getSource()).isEqualTo(PaymentSource.CASH);
        assertThat(cash.getAmount()).isEqualByComparingTo(RENT);
        // The row is written flat; every derived figure comes from the replay.
        assertThat(cash.getOverpayment()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(allocationService).reallocate(agreement);
        // No chain is built here — a second copy of that arithmetic in this
        // class is exactly what V30 had to repair.
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void terminatedAgreement_rejectsANewPayment() {
        agreement.setStatus(AgreementStatus.TERMINATED);

        assertThatThrownBy(() -> paymentService.recordPayment(request(RENT, CYCLE_START, CYCLE_END)))
                .isInstanceOf(ConflictException.class);

        verify(paymentRepository, never()).saveAndFlush(any());
        verify(allocationService, never()).reallocate(any());
    }

    // ── Editing ──────────────────────────────────────────────────────────

    @Test
    void editingUpdatesTheRowAndReplaysTheAgreement() {
        paymentService.updatePayment(PAYMENT,
                request(new BigDecimal("150000.00"), CYCLE_START, CYCLE_END));

        assertThat(existing.getAmount()).isEqualByComparingTo(new BigDecimal("150000.00"));
        verify(allocationService).reallocate(agreement);
    }

    @Test
    void editingIsAllowedAfterMoveOut() {
        // A payment mis-keyed before move-out would otherwise be uncorrectable
        // forever, which defeats the point of the endpoint.
        agreement.setStatus(AgreementStatus.TERMINATED);

        paymentService.updatePayment(PAYMENT,
                request(new BigDecimal("150000.00"), CYCLE_START, CYCLE_END));

        verify(allocationService).reallocate(agreement);
    }

    @Test
    void editingAmountToTheSameFigureWritesNoAuditRow() {
        // The request's BigDecimal carries the JSON's scale (150000, scale 0)
        // while the stored one carries the column's (scale 2). Compared as text
        // that reads as a change, and the feed fills with edits nobody made.
        paymentService.updatePayment(PAYMENT,
                new PaymentRequest(AGREEMENT, existing.getPaymentDate(), new BigDecimal("100000"),
                        PaymentMethod.CASH, CYCLE_START, CYCLE_END, null, null));

        verify(auditWriter, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void editingRecordsWhatChanged() {
        when(allocationService.reallocate(any()))
                .thenReturn(new PaymentAllocationService.Result(1, 2, BigDecimal.ZERO));

        paymentService.updatePayment(PAYMENT,
                request(new BigDecimal("300000.00"), CYCLE_START, CYCLE_END));

        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(auditWriter).record(eq(AuditModule.PAYMENT), eq(AuditAction.UPDATE),
                any(), eq(PAYMENT.toString()), statement.capture());
        assertThat(statement.getValue())
                .contains("Tester edited a payment for Jane (A1)")
                .contains("amount '100000' → '300000'")
                .contains("carried-forward credit rebuilt (1 rows → 2)");
    }

    @Test
    void movingAPaymentToAnotherAgreementIsRejected() {
        UUID other = UUID.randomUUID();
        RentalAgreement target = RentalAgreement.builder()
                .property(agreement.getProperty()).tenant(agreement.getTenant())
                .unit(agreement.getUnit()).rentAmount(RENT)
                .status(AgreementStatus.ACTIVE).billingDay(1).build();
        target.setId(other);
        when(agreementRepository.findByIdAndLandlordIdForUpdate(other, LANDLORD))
                .thenReturn(Optional.of(target));

        assertThatThrownBy(() -> paymentService.updatePayment(PAYMENT,
                new PaymentRequest(other, existing.getPaymentDate(), RENT,
                        PaymentMethod.CASH, CYCLE_START, CYCLE_END, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("different agreement");

        verify(allocationService, never()).reallocate(any());
    }

    // ── Deleting ─────────────────────────────────────────────────────────

    @Test
    void deletingRemovesTheRowThenReplaysTheAgreement() {
        paymentService.deletePayment(PAYMENT);

        var order = org.mockito.Mockito.inOrder(paymentRepository, allocationService);
        order.verify(paymentRepository).delete(existing);
        // The DELETE has to land first, or the replay allocates against the row
        // it is meant to be removing.
        order.verify(paymentRepository).flush();
        order.verify(allocationService).reallocate(agreement);
    }

    @Test
    void deletingRecordsEnoughToReKeyTheRowByHand() {
        paymentService.deletePayment(PAYMENT);

        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(auditWriter).record(eq(AuditModule.PAYMENT), eq(AuditAction.DELETE),
                any(), eq(PAYMENT.toString()), statement.capture());
        assertThat(statement.getValue())
                .contains("Tester deleted a UGX 100,000 payment for Jane (A1)")
                .contains("covering 2026-01-01 – 2026-01-31")
                .contains("received on 2026-01-05");
    }

    @Test
    void deletingIsAllowedAfterMoveOut() {
        agreement.setStatus(AgreementStatus.TERMINATED);

        paymentService.deletePayment(PAYMENT);

        verify(allocationService).reallocate(agreement);
    }

    // ── Receipt numbers ──────────────────────────────────────────────────

    @Test
    void issuingAReceiptDrawsANumberOnceAndKeepsIt() {
        when(settingsService.drawReceiptNumber(any())).thenReturn("RCP-007");

        String first = paymentService.issueReceipt(PAYMENT);
        String second = paymentService.issueReceipt(PAYMENT);

        assertThat(first).isEqualTo("RCP-007");
        // Re-printing must reproduce the copy the tenant holds, not hand them a
        // different number and burn one out of the sequence.
        assertThat(second).isEqualTo("RCP-007");
        verify(settingsService, org.mockito.Mockito.times(1)).drawReceiptNumber(any());
        assertThat(existing.getReceiptNo()).isEqualTo("RCP-007");
    }

    @Test
    void issuingAReceiptNamesThePaymentInTheReceiptLog() {
        when(settingsService.drawReceiptNumber(any())).thenReturn("RCP-007");

        paymentService.issueReceipt(PAYMENT);

        ArgumentCaptor<String> issuedFor = ArgumentCaptor.forClass(String.class);
        verify(settingsService).drawReceiptNumber(issuedFor.capture());
        assertThat(issuedFor.getValue()).isEqualTo("Jane (A1), payment of 2026-01-05");
    }

    @Test
    void carriedForwardCreditCannotBeReceipted() {
        existing.setSource(PaymentSource.ROLLOVER);

        assertThatThrownBy(() -> paymentService.issueReceipt(PAYMENT))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("carried-forward credit");

        verify(settingsService, never()).drawReceiptNumber(any());
    }

    @Test
    void deletingSaysWhichReceiptIsStillOutThere() {
        // The one fact that cannot be recovered once the row is gone.
        existing.setReceiptNo("RCP-007");

        paymentService.deletePayment(PAYMENT);

        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(auditWriter).record(eq(AuditModule.PAYMENT), eq(AuditAction.DELETE),
                any(), eq(PAYMENT.toString()), statement.capture());
        assertThat(statement.getValue()).contains("Receipt RCP-007 stays issued.");
    }

    @Test
    void deletingAnUnreceiptedPaymentSaysNothingAboutReceipts() {
        paymentService.deletePayment(PAYMENT);

        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(auditWriter).record(eq(AuditModule.PAYMENT), eq(AuditAction.DELETE),
                any(), eq(PAYMENT.toString()), statement.capture());
        assertThat(statement.getValue()).doesNotContain("Receipt");
    }

    @Test
    void editingFlagsAReceiptAlreadyInTheTenantsHands() {
        existing.setReceiptNo("RCP-007");

        paymentService.updatePayment(PAYMENT,
                request(new BigDecimal("300000.00"), CYCLE_START, CYCLE_END));

        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(auditWriter).record(eq(AuditModule.PAYMENT), eq(AuditAction.UPDATE),
                any(), eq(PAYMENT.toString()), statement.capture());
        assertThat(statement.getValue()).contains("receipt RCP-007 already issued");
    }

    // ── Guards shared by both ────────────────────────────────────────────

    @Test
    void rolloverRowCannotBeEdited() {
        existing.setSource(PaymentSource.ROLLOVER);

        assertThatThrownBy(() -> paymentService.updatePayment(PAYMENT, request(RENT, CYCLE_START, CYCLE_END)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("carried-forward credit");
    }

    @Test
    void rolloverRowCannotBeDeleted_andNamesThePaymentThatFundedIt() {
        UUID funderId = UUID.randomUUID();
        Payment funder = Payment.builder()
                .amount(new BigDecimal("300000.00"))
                .paymentDate(LocalDate.of(2026, 1, 5))
                .source(PaymentSource.CASH)
                .build();
        funder.setId(funderId);
        existing.setSource(PaymentSource.ROLLOVER);
        existing.setFundedByPaymentId(funderId);
        when(paymentRepository.findById(funderId)).thenReturn(Optional.of(funder));

        assertThatThrownBy(() -> paymentService.deletePayment(PAYMENT))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Correct the UGX 300,000 payment received on 2026-01-05");

        verify(paymentRepository, never()).delete(any());
    }

    @Test
    void anotherAccountsPaymentIsNotFound() {
        when(paymentRepository.findByIdAndLandlordId(PAYMENT, LANDLORD))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.deletePayment(PAYMENT))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void editingChecksAccessToThePaymentsPropertyAndTheAgreements() {
        paymentService.updatePayment(PAYMENT, request(RENT, CYCLE_START, CYCLE_END));

        verify(propertyAccessGuard, org.mockito.Mockito.times(2))
                .assertCanAccess(agreement.getProperty().getId());
    }
}
