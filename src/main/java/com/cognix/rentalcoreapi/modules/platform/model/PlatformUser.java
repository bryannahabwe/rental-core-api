package com.cognix.rentalcoreapi.modules.platform.model;

import com.cognix.rentalcoreapi.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * A member of Cognix staff, deliberately outside the customer user model.
 *
 * <p>Platform staff are not a sixth {@code UserRole}: every customer user must
 * belong to an account ({@code users.account_owner_id} is NOT NULL), so a
 * platform admin placed there would surface in some customer's user list, and
 * the role enum would need a value that must never be assignable. Keeping them
 * in their own table means no path exists for an account admin to hand out
 * platform access.
 *
 * <p>Holding one of these grants nothing on its own — customer data requires
 * opening a {@link SupportSession}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "platform_users")
public class PlatformUser extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PlatformUserStatus status = PlatformUserStatus.ACTIVE;

    public boolean isActive() {
        return status == PlatformUserStatus.ACTIVE;
    }
}
