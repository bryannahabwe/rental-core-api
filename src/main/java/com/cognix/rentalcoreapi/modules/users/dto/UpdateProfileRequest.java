package com.cognix.rentalcoreapi.modules.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** The fields a user may change on their own account (name and phone). */
public record UpdateProfileRequest(

        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number format")
        String phoneNumber
) {
}
