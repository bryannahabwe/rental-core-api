package com.cognix.rentalcoreapi.modules.agreements.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MoveOutRequest(

        @NotNull(message = "Move out date is required")
        LocalDate moveOutDate,

        // Security-deposit settlement. All optional (null = 0). When any is
        // provided, applied + refunded + forfeited must equal the held deposit.
        @PositiveOrZero(message = "Applied amount cannot be negative")
        BigDecimal depositApplied,

        @PositiveOrZero(message = "Refunded amount cannot be negative")
        BigDecimal depositRefunded,

        @PositiveOrZero(message = "Forfeited amount cannot be negative")
        BigDecimal depositForfeited
) {
}
