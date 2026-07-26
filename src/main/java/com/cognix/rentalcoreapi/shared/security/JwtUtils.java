package com.cognix.rentalcoreapi.shared.security;

import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

@Slf4j
public class JwtUtils {

    private JwtUtils() {
    }

    /** This authenticated user's own id (distinct from the account anchor). */
    public static UUID getCurrentUserId() {
        return principal().userId();
    }

    /** This authenticated user's role. */
    public static UserRole getCurrentRole() {
        return principal().role();
    }

    /** This authenticated user's display name (for audit sentences). */
    public static String getCurrentUserName() {
        return principal().name();
    }

    /**
     * The active property for the current request, supplied via the
     * {@code X-Property-Id} header. Empty means "All properties" — callers
     * should fall back to landlord-wide behaviour when this is absent.
     */
    public static Optional<UUID> getCurrentPropertyId() {
        return PropertyContextHolder.get();
    }

    public static UUID getCurrentLandlordId() {
        return principal().landlordId();
    }

    private static AuthenticatedUser principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found in security context");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }

        throw new IllegalStateException("Unexpected principal type in security context");
    }
}