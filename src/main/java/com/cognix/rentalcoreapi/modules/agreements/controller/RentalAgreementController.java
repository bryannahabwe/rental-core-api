package com.cognix.rentalcoreapi.modules.agreements.controller;

import com.cognix.rentalcoreapi.modules.agreements.dto.CycleStatusResponse;
import com.cognix.rentalcoreapi.modules.agreements.dto.MoveOutRequest;
import com.cognix.rentalcoreapi.modules.agreements.dto.RentalAgreementRequest;
import com.cognix.rentalcoreapi.modules.agreements.dto.RentalAgreementResponse;
import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.service.RentalAgreementService;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/agreements")
@RequiredArgsConstructor
public class RentalAgreementController {

    private static final Set<String> SORTABLE =
            Set.of("createdAt", "startDate", "moveOutDate", "rentAmount", "status");

    private final RentalAgreementService agreementService;

    @GetMapping
    public ResponseEntity<PagedResponse<RentalAgreementResponse>> getAllAgreements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) AgreementStatus status) {

        Pageable pageable = PageRequest.of(page, size,
                SortUtils.resolve(sortBy, sortDir, SORTABLE, "createdAt"));
        return ResponseEntity.ok(agreementService.getAllAgreements(pageable, search, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RentalAgreementResponse> getAgreement(@PathVariable UUID id) {
        return ResponseEntity.ok(agreementService.getAgreement(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PROPERTY_MANAGER')")
    public ResponseEntity<RentalAgreementResponse> createAgreement(
            @Valid @RequestBody RentalAgreementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(agreementService.createAgreement(request));
    }

    @PatchMapping("/{id}/moveout")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PROPERTY_MANAGER')")
    public ResponseEntity<RentalAgreementResponse> recordMoveOut(
            @PathVariable UUID id,
            @Valid @RequestBody MoveOutRequest request) {
        return ResponseEntity.ok(agreementService.recordMoveOut(id, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PROPERTY_MANAGER')")
    public ResponseEntity<RentalAgreementResponse> updateAgreement(
            @PathVariable UUID id,
            @Valid @RequestBody RentalAgreementRequest request) {
        return ResponseEntity.ok(agreementService.updateAgreement(id, request));
    }

    @GetMapping("/{id}/cycles")
    public ResponseEntity<List<CycleStatusResponse>> getCycleStatuses(
            @PathVariable UUID id) {
        return ResponseEntity.ok(agreementService.getCycleStatuses(id));
    }
}