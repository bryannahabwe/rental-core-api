package com.cognix.rentalcoreapi.modules.settings.repository;

import com.cognix.rentalcoreapi.modules.settings.model.LandlordSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LandlordSettingsRepository extends JpaRepository<LandlordSettings, UUID> {
    Optional<LandlordSettings> findByLandlordId(UUID landlordId);

    /**
     * Row-locked variant for the read-modify-write on {@code nextReceiptNo}.
     * Two receipts issued concurrently would otherwise read the same number and
     * collide; the {@code SELECT ... FOR UPDATE} serialises the increment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LandlordSettings s where s.landlord.id = :landlordId")
    Optional<LandlordSettings> findByLandlordIdForUpdate(@Param("landlordId") UUID landlordId);
}
