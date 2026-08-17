package com.cognix.rentalcoreapi.modules.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Net-income view for a period: income (rent + other) minus expenses, plus a
 * month-by-month series for the chart.
 */
public record FinancesResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal totalRent,
        BigDecimal totalOtherIncome,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal net,
        List<MonthlyFinanceResponse> monthly
) {
}
