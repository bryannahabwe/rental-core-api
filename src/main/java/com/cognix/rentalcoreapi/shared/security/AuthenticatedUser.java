package com.cognix.rentalcoreapi.shared.security;

import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The authenticated principal placed in the security context each request.
 *
 * @param landlordId the account anchor (the owner user's id) every data row is
 *                   scoped to — for the owner this equals {@code userId}
 * @param userId     this authenticated user's own id
 * @param role       this user's role (drives {@code @PreAuthorize} checks)
 * @param name       this user's display name (used in audit sentences)
 * @param username   phone number or email
 */
public record AuthenticatedUser(
        UUID landlordId,
        UUID userId,
        UserRole role,
        String name,
        String username
) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.withPrefix()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
