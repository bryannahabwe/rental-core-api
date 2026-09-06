package com.cognix.rentalcoreapi.modules.agreements.model;

import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;
import com.cognix.rentalcoreapi.modules.units.model.RentalUnit;
import com.cognix.rentalcoreapi.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rental_agreements")
public class RentalAgreement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landlord_id", nullable = false)
    private User landlord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private RentalUnit unit;

    @Column
    private LocalDate startDate;

    @Column
    private LocalDate moveOutDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal rentAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal depositAmount;

    // How the security deposit was settled at move-out. Null until settled;
    // when set, the three parts sum to depositAmount.
    @Column(precision = 12, scale = 2)
    private BigDecimal depositApplied;

    @Column(precision = 12, scale = 2)
    private BigDecimal depositRefunded;

    @Column(precision = 12, scale = 2)
    private BigDecimal depositForfeited;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgreementStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TenantType tenantType = TenantType.NEW;

    /**
     * The effective opening balance — signed, negative meaning pre-existing
     * arrears and positive a credit. Derived, never set directly: it is
     * {@code openingBalanceEntered} plus every payment filed against a period
     * before cycle tracking begins, plus any deposit applied at move-out.
     * Recomputed by {@code PaymentAllocationService.reallocate}.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal openingBalance = BigDecimal.ZERO;

    /**
     * The arrears or credit the landlord entered for this agreement. The only
     * part of {@link #openingBalance} a person sets; kept apart from it so the
     * rest stays reversible when a payment is corrected.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal openingBalanceEntered = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Integer billingDay = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BillingModel billingModel = BillingModel.ADVANCE;
}