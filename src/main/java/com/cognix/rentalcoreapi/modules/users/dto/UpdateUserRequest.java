package com.cognix.rentalcoreapi.modules.users.dto;

import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record UpdateUserRequest(

        @NotNull(message = "Role is required")
        UserRole role,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number format")
        String phoneNumber,

        // Replaces the manager's property assignments (ignored for admins).
        List<UUID> propertyIds
) {
}
