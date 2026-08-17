package com.cognix.rentalcoreapi.modules.expenses.model;

import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.categories.model.ExpenseCategory;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.units.model.RentalUnit;
import com.cognix.rentalcoreapi.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A cost incurred against a property. {@code category} is a hard reference to a
 * managed {@link ExpenseCategory}; {@code method} stores the payment-method name
 * (from the managed option list, normalized on write) as free text — not an FK.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "expenses")
public class Expense extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landlord_id", nullable = false)
    private User landlord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    // Optional — an expense may be attributed to one unit, or left property-wide.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private RentalUnit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ExpenseCategory category;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Payment-method name (from the managed list, normalized on write). */
    @Column(nullable = false)
    private String method;

    /** Free text — who made the payment (e.g. a staff member or "petty cash"). */
    @Column
    private String paidBy;

    @Column
    private String receiptUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
