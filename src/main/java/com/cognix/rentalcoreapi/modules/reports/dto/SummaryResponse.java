package com.cognix.rentalcoreapi.modules.reports.dto;

import java.math.BigDecimal;

public record SummaryResponse(
        long totalUnits,
        long occupiedUnits,
        long availableUnits,
        long totalTenants,
        long activeAgreements,
        long terminatedAgreements,
        BigDecimal totalRevenueAllTime
) {}