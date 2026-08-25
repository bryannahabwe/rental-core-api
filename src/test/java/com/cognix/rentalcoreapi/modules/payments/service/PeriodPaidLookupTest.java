package com.cognix.rentalcoreapi.modules.payments.service;

import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.payments.dto.CycleRetained;
import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentSource;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeriodPaidLookupTest {

    private static final BigDecimal RENT = new BigDecimal("180000.00");
    private static final LocalDate JUL_START = LocalDate.of(2026, 7, 1);
    private static final LocalDate JUL_END = LocalDate.of(2026, 7, 31);
    private static final UUID AGREEMENT = UUID.randomUUID();

    @Mock private PaymentRepository paymentRepository;
    @InjectMocks private PeriodPaidLookup lookup;

    @Test
    void resolvesACycleTotalForEveryRowOnThatCycle() {
        Payment rollover = payment(new BigDecimal("70000.00"), PaymentSource.ROLLOVER);
        Payment cash = payment(new BigDecimal("110000.00"), PaymentSource.CASH);

        when(paymentRepository.findRetainedByCycle(any()))
                .thenReturn(List.of(new CycleRetained(AGREEMENT, JUL_START, JUL_END, RENT)));

        PeriodPaidLookup.Index index = lookup.forPayments(List.of(rollover, cash));

        assertThat(index.paidFor(rollover)).isEqualByComparingTo(RENT);
        assertThat(index.paidFor(cash)).isEqualByComparingTo(RENT);
    }

    @Test
    void emptyBatchIssuesNoQuery() {
        // `IN ()` is not valid SQL, so an empty page must not reach the repository.
        assertThat(lookup.forPayments(List.of())).isNotNull();

        verify(paymentRepository, never()).findRetainedByCycle(any());
    }

    @Test
    void unresolvedRowFallsBackToItsOwnRetainedAmount() {
        // Never a figure that overstates what the period holds.
        Payment cash = payment(new BigDecimal("110000.00"), PaymentSource.CASH);
        when(paymentRepository.findRetainedByCycle(any())).thenReturn(List.of());

        assertThat(lookup.forPayments(List.of(cash)).paidFor(cash))
                .isEqualByComparingTo(new BigDecimal("110000.00"));
    }

    @Test
    void completeHistoryIsSummedWithoutAQuery() {
        // The ledger already holds every row on the agreement.
        Payment rollover = payment(new BigDecimal("70000.00"), PaymentSource.ROLLOVER);
        Payment cash = payment(new BigDecimal("110000.00"), PaymentSource.CASH);

        PeriodPaidLookup.Index index =
                PeriodPaidLookup.fromCompleteHistory(List.of(rollover, cash));

        assertThat(index.paidFor(cash)).isEqualByComparingTo(RENT);
    }

    @Test
    void completeHistoryNetsOffCreditThatRolledOutOfTheCycle() {
        // A cycle that took 610k against 180k rent retains 180k; the 430k it
        // passed on is carried by the rollover rows it spawned.
        Payment lump = payment(new BigDecimal("610000.00"), PaymentSource.CASH);
        lump.setOverpayment(new BigDecimal("430000.00"));

        assertThat(PeriodPaidLookup.fromCompleteHistory(List.of(lump)).paidFor(lump))
                .isEqualByComparingTo(RENT);
    }

    private Payment payment(BigDecimal amount, PaymentSource source) {
        RentalAgreement agreement = RentalAgreement.builder().rentAmount(RENT).build();
        agreement.setId(AGREEMENT);

        return Payment.builder()
                .agreement(agreement)
                .amount(amount)
                .periodStartDate(JUL_START)
                .periodEndDate(JUL_END)
                .expectedAmount(RENT)
                .overpayment(BigDecimal.ZERO)
                .source(source)
                .build();
    }
}
