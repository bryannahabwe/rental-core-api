package com.cognix.rentalcoreapi.modules.platform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Opens a read-only support session against one customer account.
 *
 * <p>{@code reason} is mandatory and has no default: it is shown to the customer
 * in their own activity feed, so opening a session without saying why must be
 * impossible.
 */
public record StartSupportSessionRequest(

        @NotNull(message = "Account is required")
        UUID accountId,

        @NotBlank(message = "A reason is required — the customer will see it")
        String reason,

        /** Minutes until the session expires. Falls back to the configured
         *  default, and is capped by it regardless of what is asked for. */
        @Min(value = 1, message = "A session must last at least a minute")
        @Max(value = 480, message = "A session cannot last longer than 8 hours")
        Integer ttlMinutes
) {
}
