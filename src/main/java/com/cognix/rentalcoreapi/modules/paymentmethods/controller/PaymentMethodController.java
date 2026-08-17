package com.cognix.rentalcoreapi.modules.paymentmethods.controller;

import com.cognix.rentalcoreapi.modules.paymentmethods.dto.PaymentMethodRequest;
import com.cognix.rentalcoreapi.modules.paymentmethods.dto.PaymentMethodResponse;
import com.cognix.rentalcoreapi.modules.paymentmethods.service.PaymentMethodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Payment-method options. Reads open to any authenticated user (the expense
 * picker needs them); managing the list is an admin-level "settings" action.
 */
@RestController
@RequestMapping("/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private static final String MANAGE = "hasAnyRole('SUPER_ADMIN','ADMIN')";

    private final PaymentMethodService methodService;

    @GetMapping
    public ResponseEntity<List<PaymentMethodResponse>> getAll() {
        return ResponseEntity.ok(methodService.getAll());
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<PaymentMethodResponse> create(
            @Valid @RequestBody PaymentMethodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(methodService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<PaymentMethodResponse> update(
            @PathVariable UUID id, @Valid @RequestBody PaymentMethodRequest request) {
        return ResponseEntity.ok(methodService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        methodService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
