package com.cognix.rentalcoreapi.modules.agreements.dto;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record RentalAgreementResponse(
        UUID id,
        UUID tenantId,
        String tenantName,
        UUID unitId,
        String roomNumber,
        LocalDate startDate,
        LocalDate moveOutDate,
        BigDecimal rentAmount,
        BigDecimal depositAmount,
        AgreementStatus status,
        LocalDateTime createdAt
) {
    public static RentalAgreementResponse from(RentalAgreement agreement) {
        return new RentalAgreementResponse(
                agreement.getId(),
                agreement.getTenant().getId(),
                agreement.getTenant().getName(),
                agreement.getUnit().getId(),
                agreement.getUnit().getRoomNumber(),
                agreement.getStartDate(),
                agreement.getMoveOutDate(),
                agreement.getRentAmount(),
                agreement.getDepositAmount(),
                agreement.getStatus(),
                agreement.getCreatedAt()
        );
    }
}