package com.cognix.rentalcoreapi.modules.settings.model;

import com.cognix.rentalcoreapi.modules.auth.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "landlord_settings")
@EntityListeners(AuditingEntityListener.class)
public class LandlordSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landlord_id", nullable = false, unique = true)
    private User landlord;

    @Column
    private String companyName;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column
    private String logoUrl;

    @Column(nullable = false)
    @Builder.Default
    private String receiptPrefix = "RCP";

    @Column(nullable = false)
    @Builder.Default
    private Integer nextReceiptNo = 1;

    @Column(nullable = false)
    @Builder.Default
    private String receiptNumbering = "AUTO"; // AUTO or MANUAL

    @Column(columnDefinition = "TEXT")
    @Builder.Default
    private String receiptFooter = "Thank you for your business";

    @Column(nullable = false)
    @Builder.Default
    private String receiptStyle = "DIGITAL"; // DIGITAL or FORMAL

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}