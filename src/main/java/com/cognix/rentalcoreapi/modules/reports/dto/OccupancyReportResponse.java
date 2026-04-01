package com.cognix.rentalcoreapi.modules.reports.dto;

import java.math.BigDecimal;

public record OccupancyReportResponse(
        long totalUnits,
        long occupiedUnits,
        long availableUnits,
        BigDecimal occupancyRate
) {}