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

    long countByPropertyId(UUID propertyId);

    // ── Reports — property filter is optional (:propertyId IS NULL → all) ──
    @Query("SELECT COUNT(u) FROM RentalUnit u WHERE u.landlord.id = :landlordId AND " +
            "(:propertyId IS NULL OR u.property.id = :propertyId)")
    long countByLandlordId(
            @Param("landlordId") UUID landlordId,
            @Param("propertyId") UUID propertyId);

    @Query("SELECT COUNT(u) FROM RentalUnit u WHERE u.landlord.id = :landlordId AND " +
            "u.isAvailable = :isAvailable AND " +
            "(:propertyId IS NULL OR u.property.id = :propertyId)")
    long countByLandlordIdAndIsAvailable(
            @Param("landlordId") UUID landlordId,
            @Param("isAvailable") boolean isAvailable,
            @Param("propertyId") UUID propertyId);

    @Query("SELECT u FROM RentalUnit u WHERE u.landlord.id = :landlordId AND " +
            "(:propertyId IS NULL OR u.property.id = :propertyId) AND " +
            "(:search IS NULL OR LOWER(u.roomNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            "LOWER(u.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) AND " +
            "(:isAvailable IS NULL OR u.isAvailable = :isAvailable)")
    Page<RentalUnit> findAllByLandlordIdWithSearch(
            @Param("landlordId") UUID landlordId,
            @Param("propertyId") UUID propertyId,
            @Param("search") String search,
            @Param("isAvailable") Boolean isAvailable,
            Pageable pageable
    );

}