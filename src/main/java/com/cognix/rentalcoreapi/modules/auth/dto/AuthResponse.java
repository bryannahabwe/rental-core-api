package com.cognix.rentalcoreapi.modules.auth.dto;

import java.util.List;
import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UUID userId,
        String name,
        String phoneNumber,
        String email,
        String role,
        /** Properties a PROPERTY_MANAGER is scoped to; empty for admins/owners. */
        List<UUID> assignedPropertyIds,
        /** The property the client should activate on login (send as
         *  {@code X-Property-Id}); null for admins/owners, who default to
         *  the "All properties" aggregate. */
        UUID defaultPropertyId
) {
}
