package com.cognix.rentalcoreapi.modules.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentReportResponse(
        LocalDate from,
        LocalDate to,
        long totalPayments,
        BigDecimal totalAmount
) {}