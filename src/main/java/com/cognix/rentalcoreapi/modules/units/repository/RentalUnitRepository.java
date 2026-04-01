package com.cognix.rentalcoreapi.modules.units.repository;

import com.cognix.rentalcoreapi.modules.units.model.RentalUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RentalUnitRepository extends JpaRepository<RentalUnit, UUID> {

    Page<RentalUnit> findAllByLandlordId(UUID landlordId, Pageable pageable);

    Optional<RentalUnit> findByIdAndLandlordId(UUID id, UUID landlordId);

    boolean existsByRoomNumberAndLandlordId(String roomNumber, UUID landlordId);

    long countByLandlordId(UUID landlordId);

    long countByLandlordIdAndIsAvailable(UUID landlordId, boolean isAvailable);

    @Query("SELECT u FROM RentalUnit u WHERE u.landlord.id = :landlordId AND " +
            "(:search IS NULL OR LOWER(u.roomNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            "LOWER(u.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) AND " +
            "(:isAvailable IS NULL OR u.isAvailable = :isAvailable)")
    Page<RentalUnit> findAllByLandlordIdWithSearch(
            @Param("landlordId") UUID landlordId,
            @Param("search") String search,
            @Param("isAvailable") Boolean isAvailable,
            Pageable pageable
    );

}