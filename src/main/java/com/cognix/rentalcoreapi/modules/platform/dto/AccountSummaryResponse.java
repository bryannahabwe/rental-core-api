package com.cognix.rentalcoreapi.modules.platform.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Just enough to identify a customer account before opening a session against
 * it. This is the one endpoint that legitimately reads across accounts, so it
 * carries no tenant, payment or financial data whatsoever — support must open a
 * session, and leave a trace, to see any of that.
 */
public record AccountSummaryResponse(
        UUID accountId,
        String ownerName,
        String email,
        String phoneNumber,
        long userCount,
        long propertyCount,
        LocalDateTime createdAt
) {
}
