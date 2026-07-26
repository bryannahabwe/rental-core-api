package com.cognix.rentalcoreapi.modules.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UUID userId,
        String name,
        String phoneNumber,
        String email,
        String role
) {
}
