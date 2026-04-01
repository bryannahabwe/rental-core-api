package com.cognix.rentalcoreapi.modules.tenants.repository;

import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Page<Tenant> findAllByLandlordId(UUID landlordId, Pageable pageable);

    Optional<Tenant> findByIdAndLandlordId(UUID id, UUID landlordId);

    boolean existsByPhoneAndLandlordId(String phone, UUID landlordId);

    boolean existsByEmailAndLandlordId(String email, UUID landlordId);

    long countByLandlordId(UUID landlordId);

    @Query("SELECT t FROM Tenant t WHERE t.landlord.id = :landlordId AND " +
            "(:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            "LOWER(t.phone) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            "LOWER(t.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Tenant> findAllByLandlordIdWithSearch(
            @Param("landlordId") UUID landlordId,
            @Param("search") String search,
            Pageable pageable
    );
}