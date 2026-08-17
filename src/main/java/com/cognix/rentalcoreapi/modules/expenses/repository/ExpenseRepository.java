package com.cognix.rentalcoreapi.modules.expenses.repository;

import com.cognix.rentalcoreapi.modules.expenses.model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    Optional<Expense> findByIdAndLandlordId(UUID id, UUID landlordId);

    long countByCategoryId(UUID categoryId);

    // ── Search + filter. Property, category, search and each date bound are
    // independently optional (:propertyId IS NULL → all). Search matches the
    // category name, who paid, or the method. CAST(... AS date/string) lets
    // Hibernate infer the type when the param is NULL. ──
    @Query("SELECT e FROM Expense e WHERE e.landlord.id = :landlordId AND " +
            "(:propertyId IS NULL OR e.property.id = :propertyId) AND " +
            "(:categoryId IS NULL OR e.category.id = :categoryId) AND " +
            "(CAST(:from AS date) IS NULL OR e.expenseDate >= :from) AND " +
            "(CAST(:to AS date) IS NULL OR e.expenseDate <= :to) AND " +
            "(:search IS NULL OR " +
            "LOWER(e.category.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            "LOWER(e.paidBy) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            "LOWER(e.method) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Expense> findAllWithFilters(
            @Param("landlordId") UUID landlordId,
            @Param("propertyId") UUID propertyId,
            @Param("categoryId") UUID categoryId,
            @Param("search") String search,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable
    );

    // ── Reports — property filter optional (:propertyId IS NULL → all) ──
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
            "WHERE e.landlord.id = :landlordId " +
            "AND (:propertyId IS NULL OR e.property.id = :propertyId) " +
            "AND e.expenseDate BETWEEN :from AND :to")
    BigDecimal sumAmountByLandlordIdAndDateRange(
            @Param("landlordId") UUID landlordId,
            @Param("propertyId") UUID propertyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
