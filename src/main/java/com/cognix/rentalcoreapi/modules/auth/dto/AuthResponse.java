package com.cognix.rentalcoreapi.modules.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String name,
        String phoneNumber,
        String email
) {}