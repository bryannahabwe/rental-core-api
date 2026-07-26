package com.cognix.rentalcoreapi.modules.users.model;

import com.cognix.rentalcoreapi.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Links a PROPERTY_MANAGER to a property they are allowed to work on. A manager
 * with no assignments can access nothing; admins/owners ignore this table.
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
}
