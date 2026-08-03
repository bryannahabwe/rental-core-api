package com.cognix.rentalcoreapi.modules.audit.controller;

import com.cognix.rentalcoreapi.modules.audit.dto.AuditEntryResponse;
import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditService;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/activity")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTANT')")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<PagedResponse<AuditEntryResponse>> getActivity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) AuditModule module,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                auditService.getActivity(pageable, module, action, search, from, to));
    }
}
