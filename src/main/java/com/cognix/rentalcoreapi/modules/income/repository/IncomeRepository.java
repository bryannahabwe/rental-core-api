package com.cognix.rentalcoreapi.modules.income.repository;

import com.cognix.rentalcoreapi.modules.income.model.IncomeEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only queries over the unified {@link IncomeEntry} ledger (rent +
 * other income). Property/search/date filters are each independently optional,
 * matching the other list repositories.
 */
public interface IncomeRepository extends JpaRepository<IncomeEntry, UUID> {

    @Query("SELECT i FROM IncomeEntry i WHERE i.landlordId = :landlordId AND " +
            "(:propertyId IS NULL OR i.propertyId = :propertyId) AND " +
            "(CAST(:from AS date) IS NULL OR i.incomeDate >= :from) AND " +
            "(CAST(:to AS date) IS NULL OR i.incomeDate <= :to) AND " +
            "(:search IS NULL OR " +
            "LOWER(i.category) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            "LOWER(i.tenantName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<IncomeEntry> findAllWithFilters(
            @Param("landlordId") UUID landlordId,
            @Param("propertyId") UUID propertyId,
            @Param("search") String search,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable
    );
}
