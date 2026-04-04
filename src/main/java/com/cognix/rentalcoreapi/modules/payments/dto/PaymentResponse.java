package com.cognix.rentalcoreapi.modules.payments.dto;

import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentMethod;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID agreementId,
        UUID tenantId,
        String tenantName,
        UUID unitId,
        String roomNumber,
        LocalDate paymentDate,
        BigDecimal amount,
        PaymentMethod method,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        BigDecimal expectedAmount,
        BigDecimal overpayment,
        PaymentSource source,
        String periodStatus,
        String reference,
        String notes,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getAgreement().getId(),
                p.getTenant().getId(),
                p.getTenant().getName(),
                p.getUnit().getId(),
                p.getUnit().getRoomNumber(),
                p.getPaymentDate(),
                p.getAmount(),
                p.getMethod(),
                p.getPeriodStartDate(),
                p.getPeriodEndDate(),
                p.getExpectedAmount(),
                p.getOverpayment(),
                p.getSource(),
                computePeriodStatus(p),
                p.getReference(),
                p.getNotes(),
                p.getCreatedAt()
        );
    }

    private static String computePeriodStatus(Payment p) {
        if (p.getSource() == PaymentSource.ROLLOVER) return "ROLLOVER";
        int cmp = p.getAmount().compareTo(p.getExpectedAmount());
        if (cmp >= 0) return "PAID";
        if (p.getAmount().compareTo(BigDecimal.ZERO) == 0) return "UNPAID";
        return "PARTIAL";
    }
}