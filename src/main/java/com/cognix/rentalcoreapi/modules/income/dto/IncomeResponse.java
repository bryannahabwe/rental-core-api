package com.cognix.rentalcoreapi.modules.income.dto;

import com.cognix.rentalcoreapi.modules.income.model.IncomeEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of the unified income ledger. {@code source} is {@code RENT} or
 * {@code OTHER}, so the client can badge rent vs. other income.
 */
public record IncomeResponse(
        UUID id,
        String source,
        String category,
        UUID propertyId,
        String propertyName,
        UUID tenantId,
        String tenantName,
        UUID unitId,
        String roomNumber,
        UUID agreementId,
        LocalDate incomeDate,
        BigDecimal amount,
        String method,
        String receivedBy,
        String reference,
        String notes,
        LocalDateTime createdAt
) {
    public static IncomeResponse from(IncomeEntry i) {
        return new IncomeResponse(
                i.getId(),
                i.getSource(),
                i.getCategory(),
                i.getPropertyId(),
                i.getPropertyName(),
                i.getTenantId(),
                i.getTenantName(),
                i.getUnitId(),
                i.getRoomNumber(),
                i.getAgreementId(),
                i.getIncomeDate(),
                i.getAmount(),
                i.getMethod(),
                i.getReceivedBy(),
                i.getReference(),
                i.getNotes(),
                i.getCreatedAt()
        );
    }
}
