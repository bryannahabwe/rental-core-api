package com.cognix.rentalcoreapi.modules.users.dto;

import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record InviteUserRequest(

        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number format")
        String phoneNumber,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotNull(message = "Role is required")
        UserRole role,

        // Only meaningful for PROPERTY_MANAGER — the properties they may access.
        List<UUID> propertyIds
) {
}
