package com.cognix.rentalcoreapi.modules.categories.controller;

import com.cognix.rentalcoreapi.modules.categories.dto.ExpenseCategoryRequest;
import com.cognix.rentalcoreapi.modules.categories.dto.ExpenseCategoryResponse;
import com.cognix.rentalcoreapi.modules.categories.service.ExpenseCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Expense categories. Reads are open to any authenticated user (the expense
 * picker needs them); managing the list is an admin-level "settings" action.
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private static final String MANAGE = "hasAnyRole('SUPER_ADMIN','ADMIN')";

    private final ExpenseCategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<ExpenseCategoryResponse>> getAll() {
        return ResponseEntity.ok(categoryService.getAll());
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ExpenseCategoryResponse> create(
            @Valid @RequestBody ExpenseCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ExpenseCategoryResponse> update(
            @PathVariable UUID id, @Valid @RequestBody ExpenseCategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
