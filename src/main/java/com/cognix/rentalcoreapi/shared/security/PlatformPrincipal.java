package com.cognix.rentalcoreapi.shared.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Cognix staff authenticated as themselves — <em>not</em> as anyone inside a
 * customer account.
 *
 * <p>Deliberately a different type from {@link AuthenticatedUser}: this
 * principal has no account anchor and no {@code UserRole}, so
 * {@link JwtUtils#getCurrentLandlordId()} throws rather than returning something
 * plausible if a platform request ever reaches a customer service. That failure
 * is the point — platform staff read customer data only through a support
 * session, which produces a proper {@link AuthenticatedUser}.
 */
public record PlatformPrincipal(
        UUID platformUserId,
        String name,
        String email
) implements UserDetails {

    public static final String ROLE = "ROLE_PLATFORM";

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROLE));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
