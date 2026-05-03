package com.cognix.rentalcoreapi.modules.settings.repository;

import com.cognix.rentalcoreapi.modules.settings.model.LandlordSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LandlordSettingsRepository extends JpaRepository<LandlordSettings, UUID> {
    Optional<LandlordSettings> findByLandlordId(UUID landlordId);
}