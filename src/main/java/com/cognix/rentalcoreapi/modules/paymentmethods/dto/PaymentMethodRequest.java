package com.cognix.rentalcoreapi.modules.paymentmethods.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentMethodRequest(
        @NotBlank String name,
        Boolean active
) {
}
