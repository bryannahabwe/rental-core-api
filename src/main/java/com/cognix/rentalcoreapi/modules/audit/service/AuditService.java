package com.cognix.rentalcoreapi.modules.audit.service;

import com.cognix.rentalcoreapi.modules.audit.dto.AuditEntryResponse;
import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.repository.AuditTrailRepository;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditTrailRepository auditTrailRepository;

    public PagedResponse<AuditEntryResponse> getActivity(
            Pageable pageable, AuditModule module, AuditAction action,
            String search, LocalDate from, LocalDate to) {

        UUID accountId = JwtUtils.getCurrentLandlordId();
        // Scope to the property selected in the header; empty ("All properties")
        // means landlord-wide. Account-level events are included either way.
        UUID propertyId = JwtUtils.getCurrentPropertyId().orElse(null);
        // Substitute wide bounds when unset so the query needs no nullable-date
        // guard (Postgres can't type-infer a bare `:from IS NULL`).
        LocalDateTime fromTs = from != null ? from.atStartOfDay()
                : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime toTs = to != null ? to.atTime(23, 59, 59)
                : LocalDateTime.of(9999, 12, 31, 23, 59, 59);

        return PagedResponse.from(auditTrailRepository
                .findFeed(accountId, propertyId, module, action, search, fromTs, toTs, pageable)
                .map(AuditEntryResponse::from));
    }
}
