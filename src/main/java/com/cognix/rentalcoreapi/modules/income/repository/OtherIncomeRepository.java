package com.cognix.rentalcoreapi.modules.income.repository;

import com.cognix.rentalcoreapi.modules.income.model.OtherIncome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface OtherIncomeRepository extends JpaRepository<OtherIncome, UUID> {

    Optional<OtherIncome> findByIdAndLandlordId(UUID id, UUID landlordId);

    // ── Reports — property filter optional (:propertyId IS NULL → all) ──
    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM OtherIncome o " +
            "WHERE o.landlord.id = :landlordId " +
            "AND (:propertyId IS NULL OR o.property.id = :propertyId) " +
            "AND o.incomeDate BETWEEN :from AND :to")
    BigDecimal sumAmountByLandlordIdAndDateRange(
            @Param("landlordId") UUID landlordId,
            @Param("propertyId") UUID propertyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
