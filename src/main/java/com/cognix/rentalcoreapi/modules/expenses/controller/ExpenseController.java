package com.cognix.rentalcoreapi.modules.expenses.controller;

import com.cognix.rentalcoreapi.modules.expenses.dto.ExpenseRequest;
import com.cognix.rentalcoreapi.modules.expenses.dto.ExpenseResponse;
import com.cognix.rentalcoreapi.modules.expenses.service.ExpenseService;
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
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private static final Set<String> SORTABLE = Set.of(
            "expenseDate", "amount", "createdAt");

    // Writing an expense is a "record a money event" action, mirroring who
    // records payments (caretakers incur costs at the property too).
    private static final String WRITE_ROLES =
            "hasAnyRole('SUPER_ADMIN','ADMIN','PROPERTY_MANAGER','CARETAKER')";

    private final ExpenseService expenseService;

    @GetMapping
    public ResponseEntity<PagedResponse<ExpenseResponse>> getAllExpenses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "expenseDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {

        Pageable pageable = PageRequest.of(page, size,
                SortUtils.resolve(sortBy, sortDir, SORTABLE, "expenseDate"));
        return ResponseEntity.ok(expenseService.getAllExpenses(pageable, categoryId, search, from, to));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> getExpense(@PathVariable UUID id) {
        return ResponseEntity.ok(expenseService.getExpense(id));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<ExpenseResponse> recordExpense(
            @Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(expenseService.recordExpense(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<ExpenseResponse> updateExpense(
            @PathVariable UUID id, @Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.ok(expenseService.updateExpense(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<Void> deleteExpense(@PathVariable UUID id) {
        expenseService.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }
}
