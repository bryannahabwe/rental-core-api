package com.cognix.rentalcoreapi.modules.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MonthlyCollectionResponse(
        String label,
        LocalDate monthStart,
        long totalPayments,
        BigDecimal totalAmount
) {
}
