package com.cognix.rentalcoreapi.shared.security;

import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
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

    /**
     * True when this request is Cognix staff acting read-only inside a
     * customer's account rather than a user of that account. The account anchor
     * is the customer's either way, so data access is unaffected — this is for
     * the few places that need the actor themselves, such as
     * {@code /users/me}, which has no row in {@code users} to return.
     */
    public static boolean isSupportSession() {
        return principal().isSupportSession();
    }

    /** The open support session for this request, or empty for an ordinary user. */
    public static Optional<UUID> getCurrentSupportSessionId() {
        return Optional.ofNullable(principal().supportSessionId());
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

        // Authenticated, but not as anyone inside a customer account — a
        // PlatformPrincipal that reached an account-scoped endpoint. Denying is
        // correct and deliberate; it is a 403 rather than a 500 because nothing
        // has gone wrong, the caller simply has no account to be scoped to.
        throw new AccessDeniedException(
                "This endpoint requires an account user; platform staff must open a support session");
    }
}