package com.cognix.rentalcoreapi.modules.properties.dto;

import com.cognix.rentalcoreapi.modules.properties.model.Property;

import java.time.LocalDateTime;
import java.util.UUID;

public record PropertyResponse(
        UUID id,
        String name,
        String address,
        String description,
        long unitCount,
        long tenantCount,
        LocalDateTime createdAt
) {
    public static PropertyResponse from(Property property, long unitCount, long tenantCount) {
        return new PropertyResponse(
                property.getId(),
                property.getName(),
                property.getAddress(),
                property.getDescription(),
                unitCount,
                tenantCount,
                property.getCreatedAt()
        );
    }
}
