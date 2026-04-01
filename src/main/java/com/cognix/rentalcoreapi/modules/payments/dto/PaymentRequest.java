package com.cognix.rentalcoreapi.modules.payments.dto;

import com.cognix.rentalcoreapi.modules.payments.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentRequest(

        @NotNull(message = "Agreement is required")
        UUID agreementId,

        @NotNull(message = "Payment date is required")
        LocalDate paymentDate,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotNull(message = "Payment method is required")
        PaymentMethod method,

        String reference,

        String notes
) {}