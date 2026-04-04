package com.cognix.rentalcoreapi.modules.payments.dto;

import com.cognix.rentalcoreapi.modules.payments.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentRequest(
        @NotNull UUID agreementId,
        @NotNull LocalDate paymentDate,
        @NotNull BigDecimal amount,
        @NotNull PaymentMethod method,
        @NotNull LocalDate periodStartDate,
        @NotNull LocalDate periodEndDate,
        String reference,
        String notes
) {}