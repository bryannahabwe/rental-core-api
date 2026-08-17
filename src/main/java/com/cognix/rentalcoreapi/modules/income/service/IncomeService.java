package com.cognix.rentalcoreapi.modules.income.service;

import com.cognix.rentalcoreapi.modules.income.dto.IncomeResponse;
import com.cognix.rentalcoreapi.modules.income.model.IncomeEntry;
import com.cognix.rentalcoreapi.modules.income.repository.IncomeRepository;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import com.cognix.rentalcoreapi.shared.security.PropertyAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/** Read side of the unified income ledger (rent + other income). */
@Service
@RequiredArgsConstructor
public class IncomeService {

    private final IncomeRepository incomeRepository;
    private final PropertyAccessGuard propertyAccessGuard;

    public PagedResponse<IncomeResponse> getIncomeLedger(
            Pageable pageable, String search, LocalDate from, LocalDate to) {

        UUID landlordId = JwtUtils.getCurrentLandlordId();
        UUID propertyId = propertyAccessGuard.requireAccessibleProperty();

        Page<IncomeEntry> page = incomeRepository.findAllWithFilters(
                landlordId, propertyId, search, from, to, pageable);

        return PagedResponse.from(page.map(IncomeResponse::from));
    }
}
