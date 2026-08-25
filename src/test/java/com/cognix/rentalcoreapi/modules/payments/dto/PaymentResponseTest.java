package com.cognix.rentalcoreapi.modules.payments.dto;

import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentMethod;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentSource;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;
import com.cognix.rentalcoreapi.modules.units.model.RentalUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code periodStatus} describes the billing period, not the single row it is
 * attached to. A period covered by several rows — the usual shape once any
 * rollover is involved — must not report itself unpaid on every one of them.
 */
class PaymentResponseTest {

    private static final BigDecimal RENT = new BigDecimal("180000.00");

    @Test
    void topUpFinishingAPartPaidPeriod_readsPaid() {
        // The reported symptom: 70k arrived as a rollover, 110k as cash. The
        // cash row is short of the rent on its own, but the period is settled.
        Payment cash = payment(new BigDecimal("110000.00"), PaymentSource.CASH);

        assertThat(PaymentResponse.from(cash, RENT).periodStatus()).isEqualTo("PAID");
    }

    @Test
    void rowShortOfTheRentWithNothingElseOnThePeriod_readsPartial() {
        Payment cash = payment(new BigDecimal("110000.00"), PaymentSource.CASH);

        assertThat(PaymentResponse.from(cash, new BigDecimal("110000.00")).periodStatus())
                .isEqualTo("PARTIAL");
    }

    @Test
    void rolloverRowKeepsItsOwnLabel_whateverThePeriodHolds() {
        // On the payments table this is the only thing distinguishing credit
        // carried forward from cash actually received.
        Payment rollover = payment(new BigDecimal("70000.00"), PaymentSource.ROLLOVER);

        assertThat(PaymentResponse.from(rollover, RENT).periodStatus()).isEqualTo("ROLLOVER");
        assertThat(PaymentResponse.from(rollover, new BigDecimal("70000.00")).periodStatus())
                .isEqualTo("ROLLOVER");
    }

    @Test
    void periodHoldingNothing_readsUnpaid() {
        Payment reversed = payment(BigDecimal.ZERO, PaymentSource.CASH);

        assertThat(PaymentResponse.from(reversed, BigDecimal.ZERO).periodStatus())
                .isEqualTo("UNPAID");
    }

    @Test
    void periodTotalIsCarriedOnTheResponse() {
        Payment cash = payment(new BigDecimal("110000.00"), PaymentSource.CASH);

        assertThat(PaymentResponse.from(cash, RENT).periodPaidAmount())
                .isEqualByComparingTo(RENT);
    }

    private Payment payment(BigDecimal amount, PaymentSource source) {
        Property property = new Property();
        property.setId(UUID.randomUUID());
        property.setName("Block A");
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Sinani");
        RentalUnit unit = new RentalUnit();
        unit.setId(UUID.randomUUID());
        unit.setRoomNumber("A5");
        RentalAgreement agreement = RentalAgreement.builder()
                .property(property).tenant(tenant).unit(unit).rentAmount(RENT).build();
        agreement.setId(UUID.randomUUID());

        return Payment.builder()
                .property(property)
                .tenant(tenant)
                .unit(unit)
                .agreement(agreement)
                .paymentDate(LocalDate.of(2026, 8, 22))
                .amount(amount)
                .method(PaymentMethod.CASH)
                .periodStartDate(LocalDate.of(2026, 7, 1))
                .periodEndDate(LocalDate.of(2026, 7, 31))
                .expectedAmount(RENT)
                .overpayment(BigDecimal.ZERO)
                .source(source)
                .build();
    }
}
