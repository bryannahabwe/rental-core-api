package com.cognix.rentalcoreapi.modules.users.dto;

import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * One property a user may work on, and the role they hold there. Only the
 * property-scoped roles are valid here — an account-wide role reaches every
 * property and carries no assignments.
 */
public record PropertyAssignmentRequest(

        @NotNull(message = "Property is required")
        UUID propertyId,

        @NotNull(message = "Role is required")
        UserRole role
) {
}
