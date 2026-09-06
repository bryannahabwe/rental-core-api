package com.cognix.rentalcoreapi.modules.payments.model;

import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;
import com.cognix.rentalcoreapi.modules.units.model.RentalUnit;
import com.cognix.rentalcoreapi.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landlord_id", nullable = false)
    private User landlord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private RentalUnit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_id", nullable = false)
    private RentalAgreement agreement;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Column(nullable = false)
    private LocalDate periodStartDate;

    @Column(nullable = false)
    private LocalDate periodEndDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal overpayment = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentSource source = PaymentSource.CASH;

    /**
     * For a ROLLOVER row, the CASH payment whose surplus funded it. Null on
     * CASH rows. Lets a refused edit on derived credit name the payment to
     * correct instead, and the FK's ON DELETE CASCADE keeps a chain from
     * outliving its funder.
     */
    @Column(name = "funded_by_payment_id")
    private UUID fundedByPaymentId;

    /**
     * The receipt number issued for this payment, or null if none has been.
     * Drawn once and kept, so re-printing a receipt reproduces the copy the
     * tenant holds instead of drawing a fresh number, and so a payment removed
     * in error can still say which receipt is out there.
     */
    @Column(name = "receipt_no")
    private String receiptNo;

    @Column
    private String reference;

    @Column(columnDefinition = "TEXT")
    private String notes;
}