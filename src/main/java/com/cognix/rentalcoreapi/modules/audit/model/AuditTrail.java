package com.cognix.rentalcoreapi.modules.audit.model;

import com.cognix.rentalcoreapi.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * An immutable record of a single action. The human-readable {@link #statement}
 * is built at the call site and stored verbatim, so the UI just displays it.
 * INSERT-only: every column is {@code updatable = false} and a DB trigger blocks
 * UPDATE/DELETE (see V16 migration). Intentionally carries no foreign keys — an
 * audit row must survive deletion of whatever it references.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_trail")
public class AuditTrail extends BaseEntity {

    // Account anchor (the owner's user id) — every read is scoped to this.
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    // Optional property context (null for account-level events like login/invite).
    @Column(name = "property_id", updatable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditModule module;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditAction action;

    @Column(name = "acting_user_id", updatable = false)
    private UUID actingUserId;

    @Column(name = "acting_user_name", nullable = false, updatable = false)
    private String actingUserName;

    // A business label (tenant name, room number, email…), not the DB PK.
    @Column(name = "affected_record_id", updatable = false)
    private String affectedRecordId;

    @Column(nullable = false, updatable = false, length = 2000)
    private String statement;
}
