package com.cognix.rentalcoreapi.modules.tenants.dto;

import com.cognix.rentalcoreapi.modules.payments.dto.PaymentResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TenantLedgerResponse(
        UUID tenantId,
        String tenantName,
        UUID agreementId,
        String unit,
        BigDecimal rentAmount,
        String billingModel,
        BigDecimal openingArrears,
        BigDecimal openingCredit,
        BigDecimal totalExpected,
        BigDecimal totalPaid,
        BigDecimal outstanding,
        List<CycleEntry> cycles,
        List<PaymentResponse> transactions,
        long transactionsTotal
) {
    public record CycleEntry(
            LocalDate periodStartDate,
            LocalDate periodEndDate,
            BigDecimal expectedAmount,
            BigDecimal paidAmount,
            BigDecimal balance,
            String status,
            boolean due
    ) {
    }
}
