package com.cognix.rentalcoreapi.modules.payments.dto;

import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentMethod;

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
        String reference,
        String notes,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAgreement().getId(),
                payment.getTenant().getId(),
                payment.getTenant().getName(),
                payment.getUnit().getId(),
                payment.getUnit().getRoomNumber(),
                payment.getPaymentDate(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getReference(),
                payment.getNotes(),
                payment.getCreatedAt()
        );
    }
}