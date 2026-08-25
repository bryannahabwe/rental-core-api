package com.cognix.rentalcoreapi.modules.payments.repository;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.payments.dto.CycleRetained;
import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentMethod;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentSource;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the grouped per-cycle aggregate against a real database — the JPQL
 * constructor expression and GROUP BY behind it only surface at execution, not
 * at bootstrap. Rolls back; needs the same Postgres the context test does.
 */
@SpringBootTest
@Transactional
class PaymentRepositoryTest {

    private static final BigDecimal RENT = new BigDecimal("180000.00");
    private static final LocalDate JUL_START = LocalDate.of(2026, 7, 1);
    private static final LocalDate JUL_END = LocalDate.of(2026, 7, 31);
    private static final LocalDate AUG_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate AUG_END = LocalDate.of(2026, 8, 31);

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private EntityManager em;

    private User landlord;
    private Property property;
    private Tenant tenant;
    private RentalUnit unit;
    private RentalAgreement agreement;

    @BeforeEach
    void seed() {
        landlord = User.builder().name("Owner").email(UUID.randomUUID() + "@test.local").build();
        landlord.setId(UUID.randomUUID());
        landlord.setAccountOwnerId(landlord.getId());
        em.persist(landlord);

        property = Property.builder().landlord(landlord).name("Block A").build();
        em.persist(property);

        tenant = Tenant.builder().landlord(landlord).property(property).name("Sinani").build();
        em.persist(tenant);

        unit = RentalUnit.builder().landlord(landlord).property(property)
                .roomNumber("A5").rentAmount(RENT).build();
        em.persist(unit);

        agreement = RentalAgreement.builder()
                .landlord(landlord).property(property).tenant(tenant).unit(unit)
                .rentAmount(RENT).status(AgreementStatus.ACTIVE)
                .startDate(LocalDate.of(2026, 4, 1))
                .build();
        em.persist(agreement);
    }

    @Test
    void sumsEveryRowOnACycle_soATopUpCompletesTheRolloverTail() {
        // July as the bug left it: a 70k rollover tail plus a 110k cash top-up.
        // Neither row covers the rent alone; together they settle the period.
        persistPayment(new BigDecimal("70000.00"), PaymentSource.ROLLOVER, JUL_START, JUL_END, BigDecimal.ZERO);
        persistPayment(new BigDecimal("110000.00"), PaymentSource.CASH, JUL_START, JUL_END, BigDecimal.ZERO);
        em.flush();

        CycleRetained july = onlyCycle(JUL_START);
        assertThat(july.retained()).isEqualByComparingTo(RENT);
        assertThat(july.agreementId()).isEqualTo(agreement.getId());
        assertThat(july.periodEndDate()).isEqualTo(JUL_END);
    }

    @Test
    void netsOffCreditThatRolledOutOfTheCycle() {
        // A cycle that took a 610k lump sum against 180k rent retains 180k —
        // the 430k it passed on belongs to the rollover rows it spawned, and
        // counting it here would show a phantom credit on the source cycle.
        persistPayment(new BigDecimal("610000.00"), PaymentSource.CASH,
                JUL_START, JUL_END, new BigDecimal("430000.00"));
        em.flush();

        assertThat(onlyCycle(JUL_START).retained()).isEqualByComparingTo(RENT);
    }

    @Test
    void groupsPerCycle_notPerAgreement() {
        persistPayment(RENT, PaymentSource.CASH, JUL_START, JUL_END, BigDecimal.ZERO);
        persistPayment(new BigDecimal("60000.00"), PaymentSource.CASH, AUG_START, AUG_END, BigDecimal.ZERO);
        em.flush();

        List<CycleRetained> cycles = paymentRepository.findRetainedByCycle(Set.of(agreement.getId()));

        assertThat(cycles).hasSize(2);
        assertThat(onlyCycle(JUL_START).retained()).isEqualByComparingTo(RENT);
        assertThat(onlyCycle(AUG_START).retained()).isEqualByComparingTo(new BigDecimal("60000.00"));
    }

    @Test
    void agreementWithNoPayments_yieldsNoRows() {
        assertThat(paymentRepository.findRetainedByCycle(Set.of(agreement.getId()))).isEmpty();
    }

    private CycleRetained onlyCycle(LocalDate periodStart) {
        return paymentRepository.findRetainedByCycle(Set.of(agreement.getId())).stream()
                .filter(c -> c.periodStartDate().equals(periodStart))
                .findFirst()
                .orElseThrow();
    }

    private void persistPayment(BigDecimal amount, PaymentSource source,
                                LocalDate periodStart, LocalDate periodEnd,
                                BigDecimal overpayment) {
        em.persist(Payment.builder()
                .landlord(landlord).property(property).tenant(tenant)
                .unit(unit).agreement(agreement)
                .paymentDate(LocalDate.of(2026, 8, 22))
                .amount(amount)
                .method(PaymentMethod.CASH)
                .periodStartDate(periodStart)
                .periodEndDate(periodEnd)
                .expectedAmount(RENT)
                .overpayment(overpayment)
                .source(source)
                .build());
    }
}
