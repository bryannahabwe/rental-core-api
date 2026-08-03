package com.cognix.rentalcoreapi.modules.users.model;

import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import com.cognix.rentalcoreapi.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Links a property-scoped user to a property they are allowed to work on, and to
 * the role they hold <em>there</em> — so one person can be a Property Manager at
 * one property and a Caretaker at another. Scoped staff with no assignments can
 * access nothing; account-wide roles ignore this table.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "user_properties",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "property_id"})
)
public class UserPropertyAssignment extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    /** The property-scoped role this user holds at this property. */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    public static List<UUID> propertyIds(List<UserPropertyAssignment> assignments) {
        return assignments.stream().map(UserPropertyAssignment::getPropertyId).toList();
    }

    /**
     * Assignments indexed by property, for the {@code propertyRoles} map clients
     * use to work out the role that applies to the property they have active.
     * Insertion-ordered so the JSON matches the assignment ordering.
     */
    public static Map<UUID, UserRole> rolesByProperty(List<UserPropertyAssignment> assignments) {
        return assignments.stream().collect(Collectors.toMap(
                UserPropertyAssignment::getPropertyId,
                UserPropertyAssignment::getRole,
                (first, duplicate) -> first,
                LinkedHashMap::new));
    }
}
