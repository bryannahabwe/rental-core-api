package com.cognix.rentalcoreapi.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "Phone number or email is required")
        String username,

        @NotBlank(message = "Password is required")
        String password
) {}