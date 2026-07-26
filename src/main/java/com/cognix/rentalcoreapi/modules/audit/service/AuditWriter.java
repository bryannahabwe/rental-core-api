package com.cognix.rentalcoreapi.modules.audit.service;

import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.model.AuditTrail;
import com.cognix.rentalcoreapi.modules.audit.repository.AuditTrailRepository;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Writes audit records. Uses the default (REQUIRED) transaction propagation so
 * the write joins the caller's transaction — the business action and its audit
 * row commit or roll back together. Callers build the human-readable
 * {@code statement} at the call site; this service just persists it.
 */
@Service
@RequiredArgsConstructor
public class AuditWriter {

    private final AuditTrailRepository auditTrailRepository;

    /** Records an action performed by the current authenticated user. */
    public void record(AuditModule module, AuditAction action,
                       UUID propertyId, String affectedRecordId, String statement) {
        record(module, action,
                JwtUtils.getCurrentLandlordId(),
                JwtUtils.getCurrentUserId(),
                JwtUtils.getCurrentUserName(),
                propertyId, affectedRecordId, statement);
    }

    /**
     * Records an action with an explicit actor — for auth events (login,
     * accept-invite) where there is no security context yet.
     */
    public void record(AuditModule module, AuditAction action,
                       UUID accountId, UUID actingUserId, String actingUserName,
                       UUID propertyId, String affectedRecordId, String statement) {
        auditTrailRepository.save(AuditTrail.builder()
                .accountId(accountId)
                .propertyId(propertyId)
                .module(module)
                .action(action)
                .actingUserId(actingUserId)
                .actingUserName(actingUserName)
                .affectedRecordId(affectedRecordId)
                .statement(statement)
                .build());
    }
}
