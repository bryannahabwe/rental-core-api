package com.cognix.rentalcoreapi.modules.payments.repository;

import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Page<Payment> findAllByLandlordId(UUID landlordId, Pageable pageable);

    Page<Payment> findAllByLandlordIdAndTenantId(
            UUID landlordId, UUID tenantId, Pageable pageable);

    Page<Payment> findAllByLandlordIdAndAgreementId(
            UUID landlordId, UUID agreementId, Pageable pageable);

    Optional<Payment> findByIdAndLandlordId(UUID id, UUID landlordId);

    List<Payment> findAllByLandlordIdAndPaymentDateBetween(
            UUID landlordId, LocalDate from, LocalDate to);

    // ── Rollover deduplication ──────────────────────────────
    boolean existsByAgreementIdAndPeriodMonthAndPeriodYearAndSource(
            UUID agreementId,
            Integer periodMonth,
            Integer periodYear,
            PaymentSource source
    );

    // ── Period balance calculation ──────────────────────────
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.agreement.id = :agreementId " +
            "AND p.periodMonth = :month " +
            "AND p.periodYear = :year")
    BigDecimal sumByAgreementAndPeriod(
            @Param("agreementId") UUID agreementId,
            @Param("month") Integer month,
            @Param("year") Integer year
    );

    // ── Reports ─────────────────────────────────────────────
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.landlord.id = :landlordId " +
            "AND p.paymentDate BETWEEN :from AND :to")
    BigDecimal sumAmountByLandlordIdAndDateRange(
            @Param("landlordId") UUID landlordId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    long countByLandlordIdAndPaymentDateBetween(
            UUID landlordId, LocalDate from, LocalDate to);

    // ── Search + filter ─────────────────────────────────────
    @Query("SELECT p FROM Payment p WHERE p.landlord.id = :landlordId AND " +
            "(:tenantId IS NULL OR p.tenant.id = :tenantId) AND " +
            "(:agreementId IS NULL OR p.agreement.id = :agreementId) AND " +
            "(:search IS NULL OR LOWER(p.tenant.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            "LOWER(p.unit.roomNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Payment> findAllWithFilters(
            @Param("landlordId") UUID landlordId,
            @Param("tenantId") UUID tenantId,
            @Param("agreementId") UUID agreementId,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT p FROM Payment p WHERE p.landlord.id = :landlordId AND " +
            "(:tenantId IS NULL OR p.tenant.id = :tenantId) AND " +
            "(:agreementId IS NULL OR p.agreement.id = :agreementId) AND " +
            "(:search IS NULL OR LOWER(p.tenant.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            "LOWER(p.unit.roomNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) AND " +
            "p.paymentDate >= :from AND p.paymentDate <= :to")
    Page<Payment> findAllWithFiltersAndDates(
            @Param("landlordId") UUID landlordId,
            @Param("tenantId") UUID tenantId,
            @Param("agreementId") UUID agreementId,
            @Param("search") String search,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable
    );
}