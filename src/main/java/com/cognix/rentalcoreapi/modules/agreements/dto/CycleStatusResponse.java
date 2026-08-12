package com.cognix.rentalcoreapi.modules.agreements.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CycleStatusResponse(
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        BigDecimal expectedAmount,
        BigDecimal paidAmount,
        String status,  // PAID / PARTIAL / UNPAID
        boolean due     // false = a future cycle offered only for paying ahead, not owed yet
) {
}