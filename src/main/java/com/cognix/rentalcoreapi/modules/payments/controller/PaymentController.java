package com.cognix.rentalcoreapi.modules.payments.controller;

import com.cognix.rentalcoreapi.modules.payments.dto.PaymentRequest;
import com.cognix.rentalcoreapi.modules.payments.dto.PaymentResponse;
import com.cognix.rentalcoreapi.modules.payments.service.PaymentService;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.util.SortUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final Set<String> SORTABLE = Set.of(
            "paymentDate", "amount", "expectedAmount", "periodStartDate", "createdAt");

    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<PagedResponse<PaymentResponse>> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "paymentDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID agreementId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {

        Pageable pageable = PageRequest.of(page, size,
                SortUtils.resolve(sortBy, sortDir, SORTABLE, "paymentDate"));
        return ResponseEntity.ok(
                paymentService.getAllPayments(pageable, tenantId, agreementId, search, from, to));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    // Caretakers collect rent at the property, so they record payments — but
    // nothing else that writes.
    private static final String RECORD_ROLES =
            "hasAnyRole('SUPER_ADMIN','ADMIN','PROPERTY_MANAGER','CARETAKER')";

    // Narrower than recording. Whoever took the money may write it down;
    // unwinding money already banked — and the tenant's cycle statuses with it
    // — is an account-level decision.
    private static final String AMEND_ROLES = "hasAnyRole('SUPER_ADMIN','ADMIN')";

    @PostMapping
    @PreAuthorize(RECORD_ROLES)
    public ResponseEntity<PaymentResponse> recordPayment(
            @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.recordPayment(request));
    }

    // Mirrors who may record a payment — a receipt number is only ever drawn as
    // part of issuing a receipt for one.
    @PostMapping("/{id}/receipt")
    @PreAuthorize(RECORD_ROLES)
    public ResponseEntity<String> issueReceipt(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.issueReceipt(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize(AMEND_ROLES)
    public ResponseEntity<PaymentResponse> updatePayment(
            @PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(paymentService.updatePayment(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(AMEND_ROLES)
    public ResponseEntity<Void> deletePayment(@PathVariable UUID id) {
        paymentService.deletePayment(id);
        return ResponseEntity.noContent().build();
    }
}