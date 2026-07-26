package com.cognix.rentalcoreapi.modules.auth.model;

import com.cognix.rentalcoreapi.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity implements UserDetails {

    @Column(nullable = false)
    private String name;

    // Nullable: staff invited by email may not have a phone number.
    @Column(unique = true)
    private String phoneNumber;

    @Column(unique = true)
    private String email;

    // Nullable: an INVITED user has no password until they accept the invite.
    @Column
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.SUPER_ADMIN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    // The account this user belongs to = the owner user's id. For the owner
    // this equals their own id. This is the anchor every data row is scoped to
    // (persisted as landlord_id elsewhere), so a manager acting on data writes
    // the owner's id, not their own.
    @Column(nullable = false)
    private UUID accountOwnerId;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.withPrefix()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        // Phone is the primary username; invited staff log in by email instead.
        return phoneNumber != null ? phoneNumber : email;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
