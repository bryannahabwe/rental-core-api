package com.cognix.rentalcoreapi.modules.units.repository;

import com.cognix.rentalcoreapi.modules.units.model.RentalUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RentalUnitRepository extends JpaRepository<RentalUnit, UUID> {

    Page<RentalUnit> findAllByLandlordId(UUID landlordId, Pageable pageable);

    Optional<RentalUnit> findByIdAndLandlordId(UUID id, UUID landlordId);

    boolean existsByRoomNumberAndLandlordId(String roomNumber, UUID landlordId);

    long countByLandlordId(UUID landlordId);

    long countByLandlordIdAndIsAvailable(UUID landlordId, boolean isAvailable);
}