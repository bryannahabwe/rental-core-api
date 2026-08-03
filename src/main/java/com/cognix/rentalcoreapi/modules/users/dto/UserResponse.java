package com.cognix.rentalcoreapi.modules.users.dto;

import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import com.cognix.rentalcoreapi.modules.auth.model.UserStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String phoneNumber,
        UserRole role,
        UserStatus status,
        List<UUID> assignedPropertyIds,
        /** The role this user holds at each assigned property; empty for an
         *  account-wide role, which reaches every property as {@code role}. */
        Map<UUID, UserRole> propertyRoles,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user,
                                    List<UUID> assignedPropertyIds,
                                    Map<UUID, UserRole> propertyRoles) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getStatus(),
                assignedPropertyIds,
                propertyRoles,
                user.getCreatedAt()
        );
    }
}
