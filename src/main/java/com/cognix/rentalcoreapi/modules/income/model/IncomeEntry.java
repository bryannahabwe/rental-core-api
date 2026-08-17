package com.cognix.rentalcoreapi.modules.income.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The unified income ledger: rent (from {@code payments}, excluding ROLLOVER
 * rows) UNION non-rent income (from {@code other_income}). This is a read-only
 * projection — never inserted/updated directly.
 *
 * <p>Mapped with {@link Subselect} rather than a real table so Hibernate's
 * {@code ddl-auto: validate} treats it as a derived query (no schema
 * validation against a physical table/view). {@link Synchronize} lists the
 * underlying tables so a just-written payment/other-income is flushed and
 * visible before this is queried.
 */
@Entity
@Immutable
@Getter
@Synchronize({"payments", "other_income"})
@Subselect("""
        SELECT p.id            AS id,
               p.landlord_id   AS landlord_id,
               p.property_id   AS property_id,
               prop.name       AS property_name,
               p.tenant_id     AS tenant_id,
               t.name          AS tenant_name,
               p.unit_id       AS unit_id,
               u.room_number   AS room_number,
               p.agreement_id  AS agreement_id,
               p.payment_date  AS income_date,
               p.amount        AS amount,
               'RENT'          AS source,
               'Rent'          AS category,
               p.method        AS method,
               p.reference     AS reference,
               p.notes         AS notes,
               p.created_at    AS created_at
        FROM payments p
                 JOIN properties prop ON prop.id = p.property_id
                 LEFT JOIN tenants t ON t.id = p.tenant_id
                 LEFT JOIN rental_units u ON u.id = p.unit_id
        WHERE p.source <> 'ROLLOVER'
        UNION ALL
        SELECT o.id            AS id,
               o.landlord_id   AS landlord_id,
               o.property_id   AS property_id,
               prop.name       AS property_name,
               o.tenant_id     AS tenant_id,
               t.name          AS tenant_name,
               NULL::uuid      AS unit_id,
               NULL::varchar   AS room_number,
               o.agreement_id  AS agreement_id,
               o.income_date   AS income_date,
               o.amount        AS amount,
               'OTHER'         AS source,
               o.category      AS category,
               o.method        AS method,
               o.reference     AS reference,
               o.notes         AS notes,
               o.created_at    AS created_at
        FROM other_income o
                 JOIN properties prop ON prop.id = o.property_id
                 LEFT JOIN tenants t ON t.id = o.tenant_id
        """)
public class IncomeEntry {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "landlord_id")
    private UUID landlordId;

    @Column(name = "property_id")
    private UUID propertyId;

    @Column(name = "property_name")
    private String propertyName;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "tenant_name")
    private String tenantName;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "room_number")
    private String roomNumber;

    @Column(name = "agreement_id")
    private UUID agreementId;

    @Column(name = "income_date")
    private LocalDate incomeDate;

    @Column(name = "amount")
    private BigDecimal amount;

    /** {@code RENT} or {@code OTHER} — discriminates the source table. */
    @Column(name = "source")
    private String source;

    @Column(name = "category")
    private String category;

    @Column(name = "method")
    private String method;

    @Column(name = "reference")
    private String reference;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
