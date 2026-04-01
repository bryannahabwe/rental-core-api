package com.cognix.rentalcoreapi.modules.tenants.repository;

import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Page<Tenant> findAllByLandlordId(UUID landlordId, Pageable pageable);

    Optional<Tenant> findByIdAndLandlordId(UUID id, UUID landlordId);

    boolean existsByPhoneAndLandlordId(String phone, UUID landlordId);

    boolean existsByEmailAndLandlordId(String email, UUID landlordId);

    long countByLandlordId(UUID landlordId);
}