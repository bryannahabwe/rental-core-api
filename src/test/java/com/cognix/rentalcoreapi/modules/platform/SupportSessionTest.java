package com.cognix.rentalcoreapi.modules.platform;

import com.cognix.rentalcoreapi.modules.platform.model.SupportSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Session liveness is re-evaluated on every request by the auth filter, so it
 * is the thing standing between an ended session and continued access.
 */
class SupportSessionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 12, 0);

    @Test
    void anOpenUnexpiredSessionIsLive() {
        assertThat(session(NOW.plusMinutes(30), null).isLive(NOW)).isTrue();
    }

    @Test
    void anEndedSessionIsNotLiveEvenBeforeItsExpiry() {
        // Ending a session must take effect immediately, not at expiry.
        assertThat(session(NOW.plusMinutes(30), NOW.minusMinutes(1)).isLive(NOW)).isFalse();
    }

    @Test
    void anExpiredSessionIsNotLiveEvenThoughItWasNeverEnded() {
        assertThat(session(NOW.minusMinutes(1), null).isLive(NOW)).isFalse();
    }

    @Test
    void expiryIsExclusiveAtTheBoundary() {
        assertThat(session(NOW, null).isLive(NOW)).isFalse();
    }

    private static SupportSession session(LocalDateTime expiresAt, LocalDateTime endedAt) {
        return SupportSession.builder()
                .platformUserId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .reason("investigating a ticket")
                .expiresAt(expiresAt)
                .endedAt(endedAt)
                .build();
    }
}
