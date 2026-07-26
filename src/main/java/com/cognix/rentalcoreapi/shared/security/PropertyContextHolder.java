package com.cognix.rentalcoreapi.shared.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Request-scoped holder for the "active property" a client is viewing, supplied
 * per request via the {@code X-Property-Id} header. When absent, callers treat
 * the request as landlord-wide (the "All properties" aggregate view).
 *
 * <p>Backed by a {@link ThreadLocal} set by {@link JwtAuthFilter} and cleared at
 * the end of each request to avoid leaking across pooled request threads.
 */
public final class PropertyContextHolder {

    private static final ThreadLocal<UUID> CURRENT_PROPERTY = new ThreadLocal<>();

    private PropertyContextHolder() {
    }

    public static void set(UUID propertyId) {
        CURRENT_PROPERTY.set(propertyId);
    }

    public static Optional<UUID> get() {
        return Optional.ofNullable(CURRENT_PROPERTY.get());
    }

    public static void clear() {
        CURRENT_PROPERTY.remove();
    }
}
