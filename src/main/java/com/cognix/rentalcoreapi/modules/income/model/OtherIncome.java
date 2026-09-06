package com.cognix.rentalcoreapi.modules.income.model;

import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;
import com.cognix.rentalcoreapi.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Non-rent income: forfeited/settled deposits at move-out, and any custom
 * money-in a landlord records. Rent itself is NOT stored here — it lives in
 * {@code payments} and is unioned in through the {@code income_ledger} view.
 *
 * <p>{@code tenant}/{@code agreement} are optional: a move-out forfeiture links
 * both, while an arbitrary custom entry may link neither. {@code category} is
 * free-form ("Deposit forfeiture" for auto-generated move-out rows).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "other_income")
public class OtherIncome extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landlord_id", nullable = false)
    private User landlord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_id")
    private RentalAgreement agreement;

    @Column(nullable = false)
    private LocalDate incomeDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String category;

    /** Payment-method name (from the managed list, normalized on write). */
    @Column(nullable = false)
    private String method;

    /** Free text — who received the money (e.g. a staff member or "front desk"). */
    @Column
    private String receivedBy;

    @Column
    private String reference;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
