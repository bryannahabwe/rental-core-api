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

    List<Payment> findAllByAgreementIdOrderByPaymentDateAscCreatedAtAsc(UUID agreementId);

    List<Payment> findAllByLandlordIdAndPaymentDateBetween(
            UUID landlordId, LocalDate from, LocalDate to);

    // ── Rollover deduplication — now uses periodStartDate ──
    boolean existsByAgreementIdAndPeriodStartDateAndSource(
            UUID agreementId,
            LocalDate periodStartDate,
            PaymentSource source
    );

    // ── Cumulative balance — sum ALL payments for an agreement ──
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.agreement.id = :agreementId")
    BigDecimal sumAllByAgreement(@Param("agreementId") UUID agreementId);

    // ── Same, but excludes payments recorded for a period before the
    // agreement's tracked cycle range starts. A payment dated/period-tagged
    // earlier than that (e.g. carried over from data entry, a prior
    // arrangement, or an import) isn't counted as satisfying a cycle
    // cyclesElapsed() ever counted as owed — so it must not be allowed to
    // silently cancel out arrears on a *later*, actually-due cycle. ──
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.agreement.id = :agreementId AND p.periodStartDate >= :cutoff")
    BigDecimal sumByAgreementFromDate(
            @Param("agreementId") UUID agreementId,
            @Param("cutoff") LocalDate cutoff);

    // ── Overpayment recorded on CASH payments that seeded a ROLLOVER chain.
    // A CASH payment keeps its full amount (incl. the excess) for the audit
    // trail, while that same excess is *also* recorded on the ROLLOVER rows
    // it generated — so sumAllByAgreement() double-counts it unless this is
    // subtracted back out. ──
    @Query("SELECT COALESCE(SUM(p.overpayment), 0) FROM Payment p " +
            "WHERE p.agreement.id = :agreementId AND p.source = :source " +
            "AND p.periodStartDate >= :cutoff")
    BigDecimal sumOverpaymentByAgreementAndSourceFromDate(
            @Param("agreementId") UUID agreementId,
            @Param("source") PaymentSource source,
            @Param("cutoff") LocalDate cutoff);

    // ── Reports — property filter is optional (:propertyId IS NULL → all).
    // ROLLOVER rows are excluded: they re-record cash already counted on the
    // originating CASH payment (same paymentDate), so including them would
    // double-count revenue and payment counts. ──
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.landlord.id = :landlordId " +
            "AND (:propertyId IS NULL OR p.property.id = :propertyId) " +
            "AND p.source <> com.cognix.rentalcoreapi.modules.payments.model.PaymentSource.ROLLOVER " +
            "AND p.paymentDate BETWEEN :from AND :to")
    BigDecimal sumAmountByLandlordIdAndDateRange(
            @Param("landlordId") UUID landlordId,
            @Param("propertyId") UUID propertyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    @Query("SELECT COUNT(p) FROM Payment p " +
            "WHERE p.landlord.id = :landlordId " +
            "AND (:propertyId IS NULL OR p.property.id = :propertyId) " +
            "AND p.source <> com.cognix.rentalcoreapi.modules.payments.model.PaymentSource.ROLLOVER " +
            "AND p.paymentDate BETWEEN :from AND :to")
    long countByLandlordIdAndPaymentDateBetween(
            @Param("landlordId") UUID landlordId,
            @Param("propertyId") UUID propertyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // ── Search + filter ─────────────────────────────────────
    // `from` and `to` are independently optional, like every other filter here.
    // They used to live in a separate findAllWithFiltersAndDates query that the
    // service only reached when BOTH bounds were supplied, so a one-sided range
    // was silently dropped and the caller got unfiltered results with no error.
    @Query("SELECT p FROM Payment p WHERE p.landlord.id = :landlordId AND " +
            "(:propertyId IS NULL OR p.property.id = :propertyId) AND " +
            "(:tenantId IS NULL OR p.tenant.id = :tenantId) AND " +
            "(:agreementId IS NULL OR p.agreement.id = :agreementId) AND " +
            // CAST(...) is required: comparing a bare parameter to NULL leaves
            // Hibernate unable to infer its type, which fails the query. Same
            // treatment :search already needs below.
            "(CAST(:from AS date) IS NULL OR p.paymentDate >= :from) AND " +
            "(CAST(:to AS date) IS NULL OR p.paymentDate <= :to) AND " +
            "(:search IS NULL OR LOWER(p.tenant.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            "LOWER(p.unit.roomNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Payment> findAllWithFilters(
            @Param("landlordId") UUID landlordId,
            @Param("propertyId") UUID propertyId,
            @Param("tenantId") UUID tenantId,
            @Param("agreementId") UUID agreementId,
            @Param("search") String search,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable
    );

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.agreement.id = :agreementId " +
            "AND p.periodStartDate = :periodStartDate " +
            "AND p.periodEndDate = :periodEndDate")
    BigDecimal sumByAgreementAndCycle(
            @Param("agreementId") UUID agreementId,
            @Param("periodStartDate") LocalDate periodStartDate,
            @Param("periodEndDate") LocalDate periodEndDate
    );
}