package com.cognix.rentalcoreapi.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiry-ms}")
    private long expiryMs;

    @Value("${app.jwt.refresh-expiry-ms}")
    private long refreshExpiryMs;

    // Invite links are longer-lived than access tokens (default 72h).
    @Value("${app.jwt.invite-expiry-ms:259200000}")
    private long inviteExpiryMs;

    // Password-reset links are short-lived (default 1h).
    @Value("${app.jwt.reset-expiry-ms:3600000}")
    private long resetExpiryMs;

    private static final String PURPOSE_INVITE = "INVITE";

    private static final String PURPOSE_RESET = "PASSWORD_RESET";

    /** Cognix staff authenticated as themselves. Reaches {@code /platform/**}
     *  and no customer data whatsoever. */
    private static final String PURPOSE_PLATFORM = "PLATFORM";

    /** An open support session: platform staff acting read-only against one
     *  named customer account. */
    private static final String PURPOSE_SUPPORT = "SUPPORT";

    /** The identity carried by an invite token: who it's for and which issued
     *  version it represents (compared against the user's current version so
     *  superseded links can be rejected). */
    public record InviteToken(UUID userId, UUID version) {
    }

    /** The identity carried by a password-reset token: who it's for and which
     *  issued version it represents (compared against the user's current
     *  version so used or superseded links can be rejected). */
    public record ResetToken(UUID userId, UUID version) {
    }

    /** The identity carried by a platform token. */
    public record PlatformToken(UUID platformUserId) {
    }

    /** The identity carried by a support token. Only the session id travels in
     *  the token — the account, the expiry and whether it is still open are all
     *  re-read from the database each request, so ending a session takes effect
     *  immediately without any token revocation machinery. */
    public record SupportToken(UUID sessionId) {
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, String username) {
        return buildToken(userId, username, expiryMs);
    }

    public String generateRefreshToken(UUID userId, String username) {
        return buildToken(userId, username, refreshExpiryMs);
    }

    private String buildToken(UUID userId, String username, long expiry) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiry))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * A single-use invite token embedded in the acceptance link. Carries a
     * {@code purpose=INVITE} claim so it can't be used to authenticate a normal
     * request (see {@link #isTokenValid}), plus an {@code inviteVersion} claim
     * the caller compares against the user's current version to reject links
     * superseded by a resend.
     */
    public String generateInviteToken(UUID userId, String email, UUID tokenVersion) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .claim("purpose", PURPOSE_INVITE)
                .claim("inviteVersion", tokenVersion.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + inviteExpiryMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validates an invite token and returns its {@link InviteToken} identity.
     * Throws if expired, tampered, or not an INVITE-purpose token. The caller
     * must still check {@link InviteToken#version()} against the user's current
     * version to reject links that have been superseded by a resend.
     */
    public InviteToken validateInviteToken(String token) {
        Claims claims;
        try {
            claims = extractClaims(token);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or expired invite link");
        }
        if (!PURPOSE_INVITE.equals(claims.get("purpose", String.class))) {
            throw new IllegalArgumentException("Invalid invite link");
        }
        UUID userId = UUID.fromString(claims.get("userId", String.class));
        String versionClaim = claims.get("inviteVersion", String.class);
        UUID version = versionClaim != null ? UUID.fromString(versionClaim) : null;
        return new InviteToken(userId, version);
    }

    /**
     * A short-lived link for resetting a forgotten password. Carries
     * {@code purpose=PASSWORD_RESET} so {@link #isTokenValid} refuses it for
     * ordinary requests, and a {@code resetVersion} the caller compares against
     * the user's current version to reject used or superseded links.
     */
    public String generatePasswordResetToken(UUID userId, String email, UUID resetVersion) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .claim("purpose", PURPOSE_RESET)
                .claim("resetVersion", resetVersion.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + resetExpiryMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validates a password-reset token and returns its {@link ResetToken}
     * identity. Throws if expired, tampered, or not a reset-purpose token. The
     * caller must still check {@link ResetToken#version()} against the user's
     * current version to reject used or superseded links.
     */
    public ResetToken validatePasswordResetToken(String token) {
        Claims claims;
        try {
            claims = extractClaims(token);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or expired reset link");
        }
        if (!PURPOSE_RESET.equals(claims.get("purpose", String.class))) {
            throw new IllegalArgumentException("Invalid reset link");
        }
        UUID userId = UUID.fromString(claims.get("userId", String.class));
        String versionClaim = claims.get("resetVersion", String.class);
        UUID version = versionClaim != null ? UUID.fromString(versionClaim) : null;
        return new ResetToken(userId, version);
    }

    // ── Platform support ──────────────────────────────────────────────────

    /**
     * A token for Cognix staff authenticated as themselves. Carries
     * {@code purpose=PLATFORM}, so {@link #isTokenValid} refuses it for ordinary
     * requests: holding one grants access to {@code /platform/**} and to no
     * customer data at all. Customer data requires opening a support session.
     */
    public String generatePlatformToken(UUID platformUserId, String email, long expiry) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", platformUserId.toString())
                .claim("purpose", PURPOSE_PLATFORM)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiry))
                .signWith(getSigningKey())
                .compact();
    }

    public PlatformToken validatePlatformToken(String token) {
        Claims claims = claimsForPurpose(token, PURPOSE_PLATFORM, "Invalid or expired platform session");
        return new PlatformToken(UUID.fromString(claims.get("userId", String.class)));
    }

    /**
     * A token for an open support session. Expires with the session, and carries
     * {@code purpose=SUPPORT} so it can never authenticate as an ordinary user.
     */
    public String generateSupportToken(UUID sessionId, String email, long expiry) {
        return Jwts.builder()
                .subject(email)
                .claim("sessionId", sessionId.toString())
                .claim("purpose", PURPOSE_SUPPORT)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiry))
                .signWith(getSigningKey())
                .compact();
    }

    public SupportToken validateSupportToken(String token) {
        Claims claims = claimsForPurpose(token, PURPOSE_SUPPORT, "Invalid or expired support session");
        return new SupportToken(UUID.fromString(claims.get("sessionId", String.class)));
    }

    /** The purpose a token declares, or empty for an ordinary access/refresh
     *  token. Lets the auth filter route a request to the right identity
     *  resolution without first trying (and failing) the ordinary path. */
    public Optional<String> extractPurpose(String token) {
        try {
            return Optional.ofNullable(extractClaims(token).get("purpose", String.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Claims claimsForPurpose(String token, String purpose, String message) {
        Claims claims;
        try {
            claims = extractClaims(token);
        } catch (Exception e) {
            throw new IllegalArgumentException(message);
        }
        if (!purpose.equals(claims.get("purpose", String.class))) {
            throw new IllegalArgumentException(message);
        }
        return claims;
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaims(token).get("userId", String.class));
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);
            // Special-purpose tokens (e.g. INVITE) must never authenticate a
            // normal request — only access/refresh tokens (no purpose) may.
            if (claims.get("purpose", String.class) != null) {
                return false;
            }
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}