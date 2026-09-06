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
        UUID propertyId,
        String propertyName,
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
        BigDecimal periodPaidAmount,
        String periodStatus,
        String receiptNo,
        String reference,
        String notes,
        LocalDateTime createdAt
) {
    /**
     * @param periodPaidAmount total retained by this row's billing cycle across
     *                         every payment filed against it — not just this
     *                         row's own contribution. A cycle is routinely
     *                         covered by several rows (a rollover tail plus a
     *                         cash top-up, say); judged row by row against the
     *                         full rent, each of them reads PARTIAL however
     *                         much the period actually holds.
     */
    public static PaymentResponse from(Payment p, BigDecimal periodPaidAmount) {
        return new PaymentResponse(
                p.getId(),
                p.getProperty() != null ? p.getProperty().getId() : null,
                p.getProperty() != null ? p.getProperty().getName() : null,
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
                periodPaidAmount,
                computePeriodStatus(p, periodPaidAmount),
                p.getReceiptNo(),
                p.getReference(),
                p.getNotes(),
                p.getCreatedAt()
        );
    }

    /**
     * The state of the PERIOD this payment belongs to, not of the payment. A
     * rollover row keeps its own label — on a payments table that is the only
     * marker distinguishing carried-forward credit from cash received.
     */
    private static String computePeriodStatus(Payment p, BigDecimal periodPaidAmount) {
        if (p.getSource() == PaymentSource.ROLLOVER) return "ROLLOVER";
        if (periodPaidAmount.compareTo(p.getExpectedAmount()) >= 0) return "PAID";
        if (periodPaidAmount.compareTo(BigDecimal.ZERO) <= 0) return "UNPAID";
        return "PARTIAL";
    }
}