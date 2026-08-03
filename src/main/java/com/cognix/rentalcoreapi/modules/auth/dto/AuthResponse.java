package com.cognix.rentalcoreapi.modules.auth.dto;

import com.cognix.rentalcoreapi.modules.auth.model.UserRole;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UUID userId,
        String name,
        String phoneNumber,
        String email,
        String role,
        /** Properties a property-scoped user is limited to; empty for the
         *  account-wide roles. */
        List<UUID> assignedPropertyIds,
        /** The role held at each assigned property. Clients should resolve the
         *  role that applies as {@code propertyRoles[activePropertyId]},
         *  falling back to {@code role}. */
        Map<UUID, UserRole> propertyRoles,
        /** The property the client should activate on login (send as
         *  {@code X-Property-Id}); null for admins/owners, who default to
         *  the "All properties" aggregate. */
        UUID defaultPropertyId
) {
}
