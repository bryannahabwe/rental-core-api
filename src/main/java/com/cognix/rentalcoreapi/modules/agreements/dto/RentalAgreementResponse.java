package com.cognix.rentalcoreapi.modules.agreements.dto;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.BillingModel;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.agreements.model.TenantType;

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
        LocalDateTime createdAt,
        TenantType tenantType,
        BigDecimal openingBalance,
        Integer billingDay,
        BillingModel billingModel
) {
    public static RentalAgreementResponse from(RentalAgreement a) {
        return new RentalAgreementResponse(
                a.getId(),
                a.getTenant().getId(),
                a.getTenant().getName(),
                a.getUnit().getId(),
                a.getUnit().getRoomNumber(),
                a.getStartDate(),
                a.getMoveOutDate(),
                a.getRentAmount(),
                a.getDepositAmount(),
                a.getStatus(),
                a.getCreatedAt(),
                a.getTenantType(),
                a.getOpeningBalance(),
                a.getBillingDay(),
                a.getBillingModel()
        );
    }
}