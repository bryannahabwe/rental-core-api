package com.cognix.rentalcoreapi.modules.categories.model;

import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * An account-managed expense category (Repairs, Utilities, …). Account-scoped
 * (not per-property). Expenses reference it by id; deleting is a soft-retire
 * ({@code active=false}) so past expenses stay valid.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "expense_categories")
public class ExpenseCategory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landlord_id", nullable = false)
    private User landlord;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
