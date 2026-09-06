package com.cognix.rentalcoreapi.modules.agreements.repository;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RentalAgreementRepository extends JpaRepository<RentalAgreement, UUID> {

    Page<RentalAgreement> findAllByLandlordId(UUID landlordId, Pageable pageable);

    Optional<RentalAgreement> findByIdAndLandlordId(UUID id, UUID landlordId);

    /**
     * The same lookup, holding a row lock for the rest of the transaction.
     *
     * <p>Every payment write replays the agreement's whole allocation from its
     * CASH rows, so two concurrent writes would each replay from a snapshot
     * missing the other's row and the second would rebuild a chain that does
     * not know about the first. The lock is what makes replay-from-scratch a
     * safe primitive. Same read-modify-write reason as
     * {@code LandlordSettingsRepository.findByLandlordIdForUpdate}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM RentalAgreement a WHERE a.id = :id AND a.landlord.id = :landlordId")
    Optional<RentalAgreement> findByIdAndLandlordIdForUpdate(@Param("id") UUID id,
                                                             @Param("landlordId") UUID landlordId);

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