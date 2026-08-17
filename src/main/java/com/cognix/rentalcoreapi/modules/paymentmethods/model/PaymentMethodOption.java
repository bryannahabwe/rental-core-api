package com.cognix.rentalcoreapi.modules.paymentmethods.model;

import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * An account-managed payment-method option (Cash, Mobile Money, …). Expenses
 * store the method by *name* string; this table just supplies the editable
 * option list (like expense_categories). Named …Option to avoid clashing with
 * the rent {@code payments.model.PaymentMethod} enum.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_methods")
public class PaymentMethodOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landlord_id", nullable = false)
    private User landlord;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
