package com.cognix.rentalcoreapi.modules.users.dto;

import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UpdateUserRequest(

        @NotNull(message = "Role is required")
        UserRole role,

        // Replaces the manager's property assignments (ignored for admins).
        List<UUID> propertyIds
) {
}
