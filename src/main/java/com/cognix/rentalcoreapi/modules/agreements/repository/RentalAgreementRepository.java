package com.cognix.rentalcoreapi.modules.agreements.repository;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RentalAgreementRepository extends JpaRepository<RentalAgreement, UUID> {

    Page<RentalAgreement> findAllByLandlordId(UUID landlordId, Pageable pageable);

    Optional<RentalAgreement> findByIdAndLandlordId(UUID id, UUID landlordId);

    boolean existsByUnitIdAndStatus(UUID unitId, AgreementStatus status);

    long countByLandlordId(UUID landlordId);

    long countByLandlordIdAndStatus(UUID landlordId, AgreementStatus status);

    Optional<RentalAgreement> findFirstByTenantIdAndLandlordIdAndStatus(
            UUID tenantId, UUID landlordId, AgreementStatus status);

    @Query("SELECT a FROM RentalAgreement a WHERE a.landlord.id = :landlordId AND " +
            "(:search IS NULL OR LOWER(a.tenant.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            "LOWER(a.unit.roomNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<RentalAgreement> findAllByLandlordIdWithSearch(
            @Param("landlordId") UUID landlordId,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT a FROM RentalAgreement a WHERE a.landlord.id = :landlordId AND " +
            "a.status = :status AND " +
            "(:search IS NULL OR LOWER(a.tenant.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            "LOWER(a.unit.roomNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<RentalAgreement> findAllByLandlordIdWithStatusAndSearch(
            @Param("landlordId") UUID landlordId,
            @Param("status") AgreementStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}