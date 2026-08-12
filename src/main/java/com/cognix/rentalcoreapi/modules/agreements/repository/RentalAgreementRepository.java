package com.cognix.rentalcoreapi.modules.agreements.repository;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface RentalAgreementRepository extends JpaRepository<RentalAgreement, UUID> {

    Page<RentalAgreement> findAllByLandlordId(UUID landlordId, Pageable pageable);

    Optional<RentalAgreement> findByIdAndLandlordId(UUID id, UUID landlordId);

    boolean existsByUnitIdAndStatus(UUID unitId, AgreementStatus status);

    // ── Reports — property filter is optional (:propertyId IS NULL → all) ──
    @Query("SELECT COUNT(a) FROM RentalAgreement a WHERE a.landlord.id = :landlordId AND " +
            "a.status = :status AND (:propertyId IS NULL OR a.property.id = :propertyId)")
    long countByLandlordIdAndStatus(
            @Param("landlordId") UUID landlordId,
            @Param("status") AgreementStatus status,
            @Param("propertyId") UUID propertyId);

    Optional<RentalAgreement> findFirstByTenantIdAndLandlordIdAndStatus(
            UUID tenantId, UUID landlordId, AgreementStatus status);

    // Forfeited security deposits are landlord income (kept for damages/penalties),
    // bucketed by move-out date like payments are by payment date. Read-only sum
    // used by reports; the balance calculator never reads depositForfeited, so this
    // cannot affect any tenant's outstanding balance.
    @Query("SELECT COALESCE(SUM(a.depositForfeited), 0) FROM RentalAgreement a " +
            "WHERE a.landlord.id = :landlordId " +
            "AND (:propertyId IS NULL OR a.property.id = :propertyId) " +
            "AND a.moveOutDate BETWEEN :from AND :to")
    BigDecimal sumForfeitedDepositByLandlordIdAndDateRange(
            @Param("landlordId") UUID landlordId,
            @Param("propertyId") UUID propertyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT a FROM RentalAgreement a WHERE a.landlord.id = :landlordId AND " +
            "(:propertyId IS NULL OR a.property.id = :propertyId) AND " +
            "(:search IS NULL OR LOWER(a.tenant.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            "LOWER(a.unit.roomNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<RentalAgreement> findAllByLandlordIdWithSearch(
            @Param("landlordId") UUID landlordId,
            @Param("propertyId") UUID propertyId,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT a FROM RentalAgreement a WHERE a.landlord.id = :landlordId AND " +
            "a.status = :status AND " +
            "(:propertyId IS NULL OR a.property.id = :propertyId) AND " +
            "(:search IS NULL OR LOWER(a.tenant.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            "LOWER(a.unit.roomNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<RentalAgreement> findAllByLandlordIdWithStatusAndSearch(
            @Param("landlordId") UUID landlordId,
            @Param("status") AgreementStatus status,
            @Param("propertyId") UUID propertyId,
            @Param("search") String search,
            Pageable pageable
    );
}