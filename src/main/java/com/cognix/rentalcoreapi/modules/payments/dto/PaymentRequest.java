package com.cognix.rentalcoreapi.modules.payments.dto;

import com.cognix.rentalcoreapi.modules.payments.model.PaymentMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentRequest(
        @NotNull UUID agreementId,
        @NotNull LocalDate paymentDate,
        @NotNull @Positive BigDecimal amount,
        @NotNull PaymentMethod method,
        @NotNull LocalDate periodStartDate,
        @NotNull LocalDate periodEndDate,
        String reference,
        String notes
) {
    @AssertTrue(message = "Period end date cannot be before period start date")
    public boolean isPeriodValid() {
        return periodStartDate == null || periodEndDate == null
                || !periodEndDate.isBefore(periodStartDate);
    }
}