package com.cognix.rentalcoreapi.modules.users.repository;

import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import com.cognix.rentalcoreapi.modules.users.model.UserPropertyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    /**
     * The role this user holds at this property, or empty if the property isn't
     * assigned to them. Drives the effective-role resolution on every request
     * made by property-scoped staff.
     */
    @Query("SELECT a.role FROM UserPropertyAssignment a "
            + "WHERE a.userId = :userId AND a.propertyId = :propertyId")
    Optional<UserRole> findRoleByUserIdAndPropertyId(@Param("userId") UUID userId,
                                                    @Param("propertyId") UUID propertyId);

    /**
     * The user's assignments in the same order as
     * {@link #findAssignedPropertyIdsOrdered}, so the ids and the per-property
     * roles handed to the client come from one round trip.
     */
    @Query("SELECT a FROM UserPropertyAssignment a, Property p "
            + "WHERE a.propertyId = p.id AND a.userId = :userId ORDER BY p.createdAt ASC")
    List<UserPropertyAssignment> findAssignmentsOrdered(@Param("userId") UUID userId);

    boolean existsByUserIdAndPropertyId(UUID userId, UUID propertyId);

    /** Distinct users assigned to any of the given properties — used to scope a
     *  property-scoped admin's view of staff to their own properties. */
    @Query("SELECT DISTINCT a.userId FROM UserPropertyAssignment a WHERE a.propertyId IN :propertyIds")
    List<UUID> findDistinctUserIdsByPropertyIdIn(@Param("propertyIds") Collection<UUID> propertyIds);

    void deleteByUserId(UUID userId);
}
