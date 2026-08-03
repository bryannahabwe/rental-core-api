package com.cognix.rentalcoreapi.modules.tenants.controller;

import com.cognix.rentalcoreapi.modules.payments.dto.PaymentResponse;
import com.cognix.rentalcoreapi.modules.tenants.dto.TenantLedgerResponse;
import com.cognix.rentalcoreapi.modules.tenants.dto.TenantRequest;
import com.cognix.rentalcoreapi.modules.tenants.dto.TenantResponse;
import com.cognix.rentalcoreapi.modules.tenants.service.TenantService;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.util.SortUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "name", "phone", "email");

    private final TenantService tenantService;

    @GetMapping
    public ResponseEntity<PagedResponse<TenantResponse>> getAllTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search) {

        Pageable pageable = PageRequest.of(page, size,
                SortUtils.resolve(sortBy, sortDir, SORTABLE, "createdAt"));
        return ResponseEntity.ok(tenantService.getAllTenants(pageable, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.getTenant(id));
    }

    @GetMapping("/{id}/ledger")
    public ResponseEntity<TenantLedgerResponse> getTenantLedger(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.getTenantLedger(id));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<PagedResponse<PaymentResponse>> getTenantTransactions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("paymentDate").descending());
        return ResponseEntity.ok(tenantService.getTenantTransactions(id, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PROPERTY_MANAGER')")
    public ResponseEntity<TenantResponse> createTenant(
            @Valid @RequestBody TenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantService.createTenant(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PROPERTY_MANAGER')")
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable UUID id,
            @Valid @RequestBody TenantRequest request) {
        return ResponseEntity.ok(tenantService.updateTenant(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteTenant(@PathVariable UUID id) {
        tenantService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }
}