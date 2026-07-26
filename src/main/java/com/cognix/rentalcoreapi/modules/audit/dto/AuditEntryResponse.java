package com.cognix.rentalcoreapi.modules.audit.dto;

import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.model.AuditTrail;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id,
        AuditModule module,
        AuditAction action,
        String actingUserName,
        String affectedRecordId,
        String statement,
        LocalDateTime createdAt
) {
    public static AuditEntryResponse from(AuditTrail a) {
        return new AuditEntryResponse(
                a.getId(),
                a.getModule(),
                a.getAction(),
                a.getActingUserName(),
                a.getAffectedRecordId(),
                a.getStatement(),
                a.getCreatedAt()
        );
    }
}
