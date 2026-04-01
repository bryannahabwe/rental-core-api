package com.cognix.rentalcoreapi.modules.units.controller;

import com.cognix.rentalcoreapi.modules.units.dto.RentalUnitRequest;
import com.cognix.rentalcoreapi.modules.units.dto.RentalUnitResponse;
import com.cognix.rentalcoreapi.modules.units.service.RentalUnitService;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/units")
@RequiredArgsConstructor
public class RentalUnitController {

    private final RentalUnitService rentalUnitService;

    @GetMapping
    public ResponseEntity<PagedResponse<RentalUnitResponse>> getAllUnits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isAvailable) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(rentalUnitService.getAllUnits(pageable, search, isAvailable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RentalUnitResponse> getUnit(@PathVariable UUID id) {
        return ResponseEntity.ok(rentalUnitService.getUnit(id));
    }

    @PostMapping
    public ResponseEntity<RentalUnitResponse> createUnit(
            @Valid @RequestBody RentalUnitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rentalUnitService.createUnit(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RentalUnitResponse> updateUnit(
            @PathVariable UUID id,
            @Valid @RequestBody RentalUnitRequest request) {
        return ResponseEntity.ok(rentalUnitService.updateUnit(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUnit(@PathVariable UUID id) {
        rentalUnitService.deleteUnit(id);
        return ResponseEntity.noContent().build();
    }
}