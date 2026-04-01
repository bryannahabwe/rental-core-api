package com.cognix.rentalcoreapi.modules.agreements.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record MoveOutRequest(

        @NotNull(message = "Move out date is required")
        LocalDate moveOutDate
) {}
