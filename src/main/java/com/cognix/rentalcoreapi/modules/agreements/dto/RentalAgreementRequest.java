package com.cognix.rentalcoreapi.modules.agreements.dto;

import com.cognix.rentalcoreapi.modules.agreements.model.TenantType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RentalAgreementRequest(

        @NotNull UUID tenantId,
        @NotNull UUID unitId,

        // Common fields
        LocalDate startDate,
        BigDecimal rentAmount,
        BigDecimal depositAmount,

        // Tenant type — NEW or EXISTING
        TenantType tenantType,

        // Only relevant for EXISTING tenants
        // Positive = tenant is ahead, Negative = tenant owes
        BigDecimal openingBalance
) {}