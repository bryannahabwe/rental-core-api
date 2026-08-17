package com.cognix.rentalcoreapi.modules.expenses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code categoryId} references a managed category by id; {@code method} is a
 * payment-method name (normalized/created server-side). {@code unitId} and
 * {@code paidBy} are optional.
 */
public record ExpenseRequest(
        @NotNull UUID propertyId,
        UUID unitId,
        @NotNull UUID categoryId,
        @NotNull LocalDate expenseDate,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String method,
        String paidBy,
        String receiptUrl,
        String notes
) {
}
