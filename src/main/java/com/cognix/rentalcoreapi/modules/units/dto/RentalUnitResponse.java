package com.cognix.rentalcoreapi.modules.units.dto;

import com.cognix.rentalcoreapi.modules.units.model.RentalUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record RentalUnitResponse(
        UUID id,
        String roomNumber,
        String description,
        BigDecimal rentAmount,
        boolean isAvailable,
        LocalDateTime createdAt
) {
    public static RentalUnitResponse from(RentalUnit unit) {
        return new RentalUnitResponse(
                unit.getId(),
                unit.getRoomNumber(),
                unit.getDescription(),
                unit.getRentAmount(),
                unit.isAvailable(),
                unit.getCreatedAt()
        );
    }
}