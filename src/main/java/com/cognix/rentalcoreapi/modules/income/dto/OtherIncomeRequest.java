package com.cognix.rentalcoreapi.modules.income.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A manually-recorded non-rent income entry. {@code propertyId} is explicit;
 * {@code tenantId} is optional (a custom entry may not relate to a tenant).
 *
 * <p>{@code method} is a payment-method name from the account's managed list,
 * normalized on write (an unknown name creates the option) — the same contract
 * as {@code ExpenseRequest}. {@code receivedBy} is free text: who took the
 * money, which is a different question from what it was paid in.
 */
public record OtherIncomeRequest(
        @NotNull UUID propertyId,
        UUID tenantId,
        @NotNull LocalDate incomeDate,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String category,
        @NotBlank String method,
        String receivedBy,
        String reference,
        String notes
) {
}
