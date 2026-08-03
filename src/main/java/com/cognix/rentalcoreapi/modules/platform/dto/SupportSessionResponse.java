package com.cognix.rentalcoreapi.modules.platform.dto;

import com.cognix.rentalcoreapi.modules.platform.model.SupportSession;

import java.time.LocalDateTime;
import java.util.UUID;

public record SupportSessionResponse(
        UUID id,
        UUID accountId,
        String accountName,
        UUID platformUserId,
        String platformUserName,
        String reason,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        LocalDateTime endedAt,
        boolean live,
        /** Only returned when the session is opened — never on a later read, so
         *  a session's token cannot be recovered from the history. */
        String accessToken
) {
    public static SupportSessionResponse of(SupportSession session, String accountName,
                                            String platformUserName, String accessToken,
                                            LocalDateTime now) {
        return new SupportSessionResponse(
                session.getId(),
                session.getAccountId(),
                accountName,
                session.getPlatformUserId(),
                platformUserName,
                session.getReason(),
                session.getCreatedAt(),
                session.getExpiresAt(),
                session.getEndedAt(),
                session.isLive(now),
                accessToken
        );
    }
}
