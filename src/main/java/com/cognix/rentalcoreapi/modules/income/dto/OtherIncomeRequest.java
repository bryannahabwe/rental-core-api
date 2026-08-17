package com.cognix.rentalcoreapi.modules.income.dto;

import com.cognix.rentalcoreapi.modules.income.model.IncomeMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A manually-recorded non-rent income entry. {@code propertyId} is explicit;
 * {@code tenantId} is optional (a custom entry may not relate to a tenant).
 */
public record OtherIncomeRequest(
        @NotNull UUID propertyId,
        UUID tenantId,
        @NotNull LocalDate incomeDate,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String category,
        @NotNull IncomeMethod method,
        String reference,
        String notes
) {
}
