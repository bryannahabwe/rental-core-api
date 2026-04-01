package com.cognix.rentalcoreapi.modules.agreements.repository;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RentalAgreementRepository extends JpaRepository<RentalAgreement, UUID> {

    Page<RentalAgreement> findAllByLandlordId(UUID landlordId, Pageable pageable);

    Page<RentalAgreement> findAllByLandlordIdAndStatus(
            UUID landlordId, AgreementStatus status, Pageable pageable);

    Optional<RentalAgreement> findByIdAndLandlordId(UUID id, UUID landlordId);

    boolean existsByUnitIdAndStatus(UUID unitId, AgreementStatus status);

    long countByLandlordId(UUID landlordId);

    long countByLandlordIdAndStatus(UUID landlordId, AgreementStatus status);
}