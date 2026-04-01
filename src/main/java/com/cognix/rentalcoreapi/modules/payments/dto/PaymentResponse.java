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
        Integer periodMonth,
        Integer periodYear,
        BigDecimal expectedAmount,
        BigDecimal overpayment,
        PaymentSource source,
        String periodStatus,
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
                payment.getPeriodMonth(),
                payment.getPeriodYear(),
                payment.getExpectedAmount(),
                payment.getOverpayment(),
                payment.getSource(),
                computePeriodStatus(payment),
                payment.getReference(),
                payment.getNotes(),
                payment.getCreatedAt()
        );
    }

    private static String computePeriodStatus(Payment payment) {
        if (payment.getSource() == PaymentSource.ROLLOVER) {
            return "ROLLOVER";
        }
        int cmp = payment.getAmount().compareTo(payment.getExpectedAmount());
        if (cmp >= 0) return "PAID";
        if (payment.getAmount().compareTo(BigDecimal.ZERO) == 0) return "UNPAID";
        return "PARTIAL";
    }
}