package com.cognix.rentalcoreapi.modules.income.dto;

import com.cognix.rentalcoreapi.modules.income.model.OtherIncome;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record OtherIncomeResponse(
        UUID id,
        UUID propertyId,
        String propertyName,
        UUID tenantId,
        String tenantName,
        UUID agreementId,
        LocalDate incomeDate,
        BigDecimal amount,
        String category,
        String method,
        String receivedBy,
        String reference,
        String notes,
        LocalDateTime createdAt
) {
    public static OtherIncomeResponse from(OtherIncome o) {
        return new OtherIncomeResponse(
                o.getId(),
                o.getProperty() != null ? o.getProperty().getId() : null,
                o.getProperty() != null ? o.getProperty().getName() : null,
                o.getTenant() != null ? o.getTenant().getId() : null,
                o.getTenant() != null ? o.getTenant().getName() : null,
                o.getAgreement() != null ? o.getAgreement().getId() : null,
                o.getIncomeDate(),
                o.getAmount(),
                o.getCategory(),
                o.getMethod(),
                o.getReceivedBy(),
                o.getReference(),
                o.getNotes(),
                o.getCreatedAt()
        );
    }
}
