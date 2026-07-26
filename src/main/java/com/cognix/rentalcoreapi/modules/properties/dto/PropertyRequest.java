package com.cognix.rentalcoreapi.modules.properties.dto;

import jakarta.validation.constraints.NotBlank;

public record PropertyRequest(

        @NotBlank(message = "Property name is required")
        String name,

        String address,

        String description
) {
}
