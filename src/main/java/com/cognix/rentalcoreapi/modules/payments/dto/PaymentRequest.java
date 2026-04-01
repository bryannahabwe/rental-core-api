package com.cognix.rentalcoreapi.modules.payments.dto;

import com.cognix.rentalcoreapi.modules.payments.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentRequest(

        @NotNull UUID agreementId,
        @NotNull LocalDate paymentDate,
        @NotNull BigDecimal amount,
        @NotNull PaymentMethod method,

        // Period this payment covers
        @NotNull @Min(1) @Max(12) Integer periodMonth,
        @NotNull Integer periodYear,

        String reference,
        String notes
) {}