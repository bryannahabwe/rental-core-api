package com.cognix.rentalcoreapi.modules.categories.repository;

import com.cognix.rentalcoreapi.modules.categories.model.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {

    List<ExpenseCategory> findAllByLandlordIdOrderByNameAsc(UUID landlordId);

    Optional<ExpenseCategory> findByIdAndLandlordId(UUID id, UUID landlordId);

    Optional<ExpenseCategory> findByLandlordIdAndNameIgnoreCase(UUID landlordId, String name);

    boolean existsByLandlordIdAndNameIgnoreCase(UUID landlordId, String name);
}
