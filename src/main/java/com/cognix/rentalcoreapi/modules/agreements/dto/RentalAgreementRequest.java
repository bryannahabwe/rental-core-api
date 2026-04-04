package com.cognix.rentalcoreapi.modules.agreements.dto;

import com.cognix.rentalcoreapi.modules.agreements.model.BillingModel;
import com.cognix.rentalcoreapi.modules.agreements.model.TenantType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RentalAgreementRequest(
        @NotNull UUID tenantId,
        @NotNull UUID unitId,
        LocalDate startDate,
        BigDecimal rentAmount,
        BigDecimal depositAmount,
        TenantType tenantType,
        BigDecimal openingBalance,
        BillingModel billingModel  // ADVANCE or ARREARS, defaults to ADVANCE
) {}