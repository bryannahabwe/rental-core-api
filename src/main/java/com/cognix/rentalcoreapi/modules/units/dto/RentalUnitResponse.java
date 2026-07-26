package com.cognix.rentalcoreapi.modules.units.dto;

import com.cognix.rentalcoreapi.modules.units.model.RentalUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record RentalUnitResponse(
        UUID id,
        UUID propertyId,
        String propertyName,
        String roomNumber,
        String description,
        BigDecimal rentAmount,
        boolean isAvailable,
        LocalDateTime createdAt
) {
    public static RentalUnitResponse from(RentalUnit unit) {
        return new RentalUnitResponse(
                unit.getId(),
                unit.getProperty() != null ? unit.getProperty().getId() : null,
                unit.getProperty() != null ? unit.getProperty().getName() : null,
                unit.getRoomNumber(),
                unit.getDescription(),
                unit.getRentAmount(),
                unit.isAvailable(),
                unit.getCreatedAt()
        );
    }
}
