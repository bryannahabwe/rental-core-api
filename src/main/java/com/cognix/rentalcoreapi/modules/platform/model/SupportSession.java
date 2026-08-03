package com.cognix.rentalcoreapi.modules.platform.model;

import com.cognix.rentalcoreapi.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One support engagement: a member of platform staff acting read-only inside a
 * single customer account, for a stated reason, until it expires or is ended.
 *
 * <p>Rows are never deleted — ending a session sets {@link #endedAt}, so "who
 * looked at this account, when, and why" stays answerable afterwards.
 *
 * <p>{@code createdAt} (from {@link BaseEntity}) is the session start; there is
 * no separate column to drift out of step with it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "support_sessions")
public class SupportSession extends BaseEntity {

    @Column(name = "platform_user_id", nullable = false)
    private UUID platformUserId;

    /** The customer account being supported — the owner user's id, the same
     *  anchor every other table scopes by as {@code landlord_id}. */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Why this session was opened. Mandatory, and surfaced to the customer in
     *  their own activity feed. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /** Whether this session still grants access. Re-evaluated on every request
     *  rather than trusted from the token, so ending a session takes effect
     *  immediately. */
    public boolean isLive(LocalDateTime now) {
        return endedAt == null && expiresAt.isAfter(now);
    }
}
