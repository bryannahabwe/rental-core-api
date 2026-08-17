package com.cognix.rentalcoreapi.modules.income.controller;

import com.cognix.rentalcoreapi.modules.income.dto.IncomeResponse;
import com.cognix.rentalcoreapi.modules.income.service.IncomeService;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.util.SortUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;

/**
 * The unified income ledger (rent + other income). Read-only — rent is created
 * via {@code /payments}, other income via {@code /other-income}. Reads are open
 * to any authenticated user, like {@code /payments}.
 */
@RestController
@RequestMapping("/income")
@RequiredArgsConstructor
public class IncomeController {

    private static final Set<String> SORTABLE = Set.of(
            "incomeDate", "amount", "category", "createdAt");

    private final IncomeService incomeService;

    @GetMapping
    public ResponseEntity<PagedResponse<IncomeResponse>> getIncome(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "incomeDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {

        Pageable pageable = PageRequest.of(page, size,
                SortUtils.resolve(sortBy, sortDir, SORTABLE, "incomeDate"));
        return ResponseEntity.ok(incomeService.getIncomeLedger(pageable, search, from, to));
    }
}
