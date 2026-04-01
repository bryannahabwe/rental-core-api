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

        // nullable — for existing tenants where move-in date is unknown
        LocalDate startDate,

        // nullable — defaults to unit's rent amount if not provided
        @Positive(message = "Rent amount must be greater than zero")
        BigDecimal rentAmount,

        // nullable — for existing tenants where deposit was already paid
        @Positive(message = "Deposit amount must be greater than zero")
        BigDecimal depositAmount
) {}