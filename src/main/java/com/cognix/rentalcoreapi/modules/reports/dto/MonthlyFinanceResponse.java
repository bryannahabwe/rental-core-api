package com.cognix.rentalcoreapi.modules.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One month's income vs expenses (and net) for the finances chart. */
public record MonthlyFinanceResponse(
        String label,
        LocalDate month,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal net
) {
}
