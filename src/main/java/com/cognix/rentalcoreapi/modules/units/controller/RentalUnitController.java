package com.cognix.rentalcoreapi.modules.units.controller;

import com.cognix.rentalcoreapi.modules.units.dto.RentalUnitRequest;
import com.cognix.rentalcoreapi.modules.units.dto.RentalUnitResponse;
import com.cognix.rentalcoreapi.modules.units.service.RentalUnitService;
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
@RequestMapping("/units")
@RequiredArgsConstructor
public class RentalUnitController {

    private static final Set<String> SORTABLE =
            Set.of("createdAt", "roomNumber", "rentAmount", "isAvailable");

    private final RentalUnitService rentalUnitService;

    @GetMapping
    public ResponseEntity<PagedResponse<RentalUnitResponse>> getAllUnits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isAvailable) {

        Pageable pageable = PageRequest.of(page, size,
                SortUtils.resolve(sortBy, sortDir, SORTABLE, "createdAt"));
        return ResponseEntity.ok(rentalUnitService.getAllUnits(pageable, search, isAvailable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RentalUnitResponse> getUnit(@PathVariable UUID id) {
        return ResponseEntity.ok(rentalUnitService.getUnit(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PROPERTY_MANAGER')")
    public ResponseEntity<RentalUnitResponse> createUnit(
            @Valid @RequestBody RentalUnitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rentalUnitService.createUnit(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PROPERTY_MANAGER')")
    public ResponseEntity<RentalUnitResponse> updateUnit(
            @PathVariable UUID id,
            @Valid @RequestBody RentalUnitRequest request) {
        return ResponseEntity.ok(rentalUnitService.updateUnit(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Void> deleteUnit(@PathVariable UUID id) {
        rentalUnitService.deleteUnit(id);
        return ResponseEntity.noContent().build();
    }
}