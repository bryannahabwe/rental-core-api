package com.cognix.rentalcoreapi.modules.properties.repository;

import com.cognix.rentalcoreapi.modules.properties.model.Property;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    List<Property> findAllByLandlordIdOrderByCreatedAtAsc(UUID landlordId);

    Optional<Property> findByIdAndLandlordId(UUID id, UUID landlordId);

    boolean existsByIdAndLandlordId(UUID id, UUID landlordId);

    boolean existsByNameAndLandlordId(String name, UUID landlordId);

    long countByLandlordId(UUID landlordId);
}
