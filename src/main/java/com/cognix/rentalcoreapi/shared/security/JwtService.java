package com.cognix.rentalcoreapi.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
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

    private static final String PURPOSE_INVITE = "INVITE";

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
     * request (see {@link #isTokenValid}).
     */
    public String generateInviteToken(UUID userId, String email) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .claim("purpose", PURPOSE_INVITE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + inviteExpiryMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validates an invite token and returns the invited user's id.
     * Throws if expired, tampered, or not an INVITE-purpose token.
     */
    public UUID validateInviteToken(String token) {
        Claims claims;
        try {
            claims = extractClaims(token);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or expired invite link");
        }
        if (!PURPOSE_INVITE.equals(claims.get("purpose", String.class))) {
            throw new IllegalArgumentException("Invalid invite link");
        }
        return UUID.fromString(claims.get("userId", String.class));
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