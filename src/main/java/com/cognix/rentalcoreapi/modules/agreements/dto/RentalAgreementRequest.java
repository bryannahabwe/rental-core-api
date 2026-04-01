package com.cognix.rentalcoreapi.modules.agreements.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RentalAgreementRequest(

        @NotNull(message = "Tenant is required")
        UUID tenantId,

        @NotNull(message = "Unit is required")
        UUID unitId,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "Rent amount is required")
        @Positive(message = "Rent amount must be greater than zero")
        BigDecimal rentAmount,

        @NotNull(message = "Deposit amount is required")
        @Positive(message = "Deposit amount must be greater than zero")
        BigDecimal depositAmount
) {}