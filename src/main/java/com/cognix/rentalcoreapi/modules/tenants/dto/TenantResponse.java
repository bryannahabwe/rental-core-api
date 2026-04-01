package com.cognix.rentalcoreapi.modules.tenants.dto;

import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        String phone,
        String email,
        String address,
        LocalDateTime createdAt
) {
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getPhone(),
                tenant.getEmail(),
                tenant.getAddress(),
                tenant.getCreatedAt()
        );
    }
}