package com.cognix.rentalcoreapi.modules.units.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record RentalUnitRequest(

        @NotNull(message = "Property is required")
        UUID propertyId,

        @NotBlank(message = "Room number is required")
        String roomNumber,

        String description,

        @NotNull(message = "Rent amount is required")
        @Positive(message = "Rent amount must be greater than zero")
        BigDecimal rentAmount,

        boolean isAvailable
) {
}