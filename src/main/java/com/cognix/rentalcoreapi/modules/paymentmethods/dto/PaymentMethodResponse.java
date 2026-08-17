package com.cognix.rentalcoreapi.modules.paymentmethods.dto;

import com.cognix.rentalcoreapi.modules.paymentmethods.model.PaymentMethodOption;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentMethodResponse(
        UUID id,
        String name,
        boolean active,
        LocalDateTime createdAt
) {
    public static PaymentMethodResponse from(PaymentMethodOption m) {
        return new PaymentMethodResponse(m.getId(), m.getName(), m.isActive(), m.getCreatedAt());
    }
}
