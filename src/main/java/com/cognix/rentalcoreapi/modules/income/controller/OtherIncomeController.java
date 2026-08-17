package com.cognix.rentalcoreapi.modules.income.controller;

import com.cognix.rentalcoreapi.modules.income.dto.OtherIncomeRequest;
import com.cognix.rentalcoreapi.modules.income.dto.OtherIncomeResponse;
import com.cognix.rentalcoreapi.modules.income.service.OtherIncomeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Manual non-rent income entries. Writes mirror who records payments — adding
 * income is a "record a money event" action.
 */
@RestController
@RequestMapping("/other-income")
@RequiredArgsConstructor
public class OtherIncomeController {

    private static final String WRITE_ROLES =
            "hasAnyRole('SUPER_ADMIN','ADMIN','PROPERTY_MANAGER','CARETAKER')";

    private final OtherIncomeService otherIncomeService;

    @GetMapping("/{id}")
    public ResponseEntity<OtherIncomeResponse> getOtherIncome(@PathVariable UUID id) {
        return ResponseEntity.ok(otherIncomeService.getOtherIncome(id));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<OtherIncomeResponse> recordOtherIncome(
            @Valid @RequestBody OtherIncomeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(otherIncomeService.recordOtherIncome(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<OtherIncomeResponse> updateOtherIncome(
            @PathVariable UUID id, @Valid @RequestBody OtherIncomeRequest request) {
        return ResponseEntity.ok(otherIncomeService.updateOtherIncome(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<Void> deleteOtherIncome(@PathVariable UUID id) {
        otherIncomeService.deleteOtherIncome(id);
        return ResponseEntity.noContent().build();
    }
}
