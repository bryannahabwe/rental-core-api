package com.cognix.rentalcoreapi.modules.tenants.dto;

import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        String phone,
        String email,
        String address,
        LocalDateTime createdAt,
        String currentUnit,
        BigDecimal monthlyRent,
        BigDecimal currentBalance,
        String periodStatus,
        LocalDate currentCycleStart,
        LocalDate currentCycleEnd,
        Boolean currentCyclePaid
) {
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(), tenant.getName(), tenant.getPhone(),
                tenant.getEmail(), tenant.getAddress(), tenant.getCreatedAt(),
                null, null, null, null, null, null, null
        );
    }

    public TenantResponse withBalance(
            String currentUnit,
            BigDecimal monthlyRent,
            BigDecimal currentBalance,
            String periodStatus,
            LocalDate currentCycleStart,
            LocalDate currentCycleEnd,
            Boolean currentCyclePaid) {

        return new TenantResponse(
                this.id, this.name, this.phone,
                this.email, this.address, this.createdAt,
                currentUnit, monthlyRent, currentBalance,
                periodStatus, currentCycleStart, currentCycleEnd,
                currentCyclePaid
        );
    }
}