package com.cognix.rentalcoreapi.modules.users.repository;

import com.cognix.rentalcoreapi.modules.users.model.UserPropertyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserPropertyAssignmentRepository
        extends JpaRepository<UserPropertyAssignment, UUID> {

    @Query("SELECT a.propertyId FROM UserPropertyAssignment a WHERE a.userId = :userId")
    List<UUID> findPropertyIdsByUserId(@Param("userId") UUID userId);

    /**
     * The user's assigned property ids ordered by property creation time, so the
     * first element is a stable default (matches the {@code GET /properties}
     * ordering). Used to pick a manager's default active property.
     */
    @Query("SELECT a.propertyId FROM UserPropertyAssignment a, Property p "
            + "WHERE a.propertyId = p.id AND a.userId = :userId ORDER BY p.createdAt ASC")
    List<UUID> findAssignedPropertyIdsOrdered(@Param("userId") UUID userId);

    boolean existsByUserIdAndPropertyId(UUID userId, UUID propertyId);

    void deleteByUserId(UUID userId);
}
