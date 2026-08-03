package com.cognix.rentalcoreapi.shared.security;

import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.platform.model.PlatformUser;
import com.cognix.rentalcoreapi.modules.platform.repository.PlatformUserRepository;
import com.cognix.rentalcoreapi.modules.platform.repository.SupportSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Match {@code JwtService}'s purposes without exposing its constants. */
    private static final String PURPOSE_SUPPORT = "SUPPORT";
    private static final String PURPOSE_PLATFORM = "PLATFORM";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PropertyAccessGuard propertyAccessGuard;
    private final SupportSessionRepository supportSessionRepository;
    private final PlatformUserRepository platformUserRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Active-property context (X-Property-Id) applies for the whole request;
        // set it up front and always clear it in the finally so it can't leak
        // onto the next request served by this pooled thread.
        PropertyContextHolder.set(parsePropertyId(request.getHeader("X-Property-Id")));
        try {
            final String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            final String token = authHeader.substring(7);

            // A support token authenticates Cognix staff into a customer's
            // account. It carries purpose=SUPPORT, which isTokenValid refuses
            // by design, so it is admitted here on its own branch — deliberately,
            // rather than by loosening the check that guards every other token.
            if (jwtService.extractPurpose(token).filter(PURPOSE_SUPPORT::equals).isPresent()) {
                authenticateSupportSession(token, request);
                filterChain.doFilter(request, response);
                return;
            }

            // A platform token authenticates Cognix staff as themselves, for
            // /platform/** only. It yields a PlatformPrincipal with no account
            // anchor, so any customer service it somehow reached would throw
            // rather than quietly serve someone's data.
            if (jwtService.extractPurpose(token).filter(PURPOSE_PLATFORM::equals).isPresent()) {
                authenticatePlatformStaff(token, request);
                filterChain.doFilter(request, response);
                return;
            }

            // isTokenValid rejects expired tokens and special-purpose tokens
            // (e.g. INVITE, PLATFORM), so those can't be used to authenticate a
            // request. A PLATFORM token reaching here therefore gets no
            // authentication: platform staff see customer data only through a
            // support session.
            if (!jwtService.isTokenValid(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UUID userId = jwtService.extractUserId(token);

                // Reload the user each request so role changes and
                // deactivations take effect immediately (the token isn't
                // trusted for role/account — only for identity).
                userRepository.findById(userId)
                        .filter(User::isEnabled)
                        .ifPresent(user -> {
                            // Property-scoped staff hold a role per property, so
                            // the principal carries the role for the property
                            // active on THIS request. That makes every
                            // @PreAuthorize check property-aware for free, and
                            // means a demotion at one property takes effect on
                            // the next request with no token reissue.
                            UserRole effectiveRole = propertyAccessGuard.effectiveRoleFor(
                                    user.getId(), user.getRole(),
                                    PropertyContextHolder.get().orElse(null));

                            AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                                    user.getAccountOwnerId(), user.getId(),
                                    effectiveRole, user.getName(), user.getUsername());

                            UsernamePasswordAuthenticationToken authToken =
                                    new UsernamePasswordAuthenticationToken(
                                            authenticatedUser, null, authenticatedUser.getAuthorities());

                            authToken.setDetails(
                                    new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        });
            }

            filterChain.doFilter(request, response);
        } finally {
            PropertyContextHolder.clear();
        }
    }

    /**
     * Resolves an open support session into a principal scoped to the customer's
     * account.
     *
     * <p>Only the session id travels in the token — the account, the expiry and
     * whether the session is still open are re-read here on every request, so
     * ending a session or deactivating the staff member takes effect on the very
     * next call with no token revocation machinery. That mirrors how the
     * ordinary path reloads the user rather than trusting the token.
     *
     * <p>The principal presents as {@link UserRole#ADMIN}: support needs
     * account-wide reads (including the user list, to diagnose sign-in
     * problems), but never SUPER_ADMIN, whose extra power is deletion.
     * {@link SupportReadOnlyFilter} refuses every write regardless.
     */
    private void authenticateSupportSession(String token, HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        UUID sessionId;
        try {
            sessionId = jwtService.validateSupportToken(token).sessionId();
        } catch (RuntimeException e) {
            return; // malformed or wrong-purpose token — stay unauthenticated
        }

        supportSessionRepository.findById(sessionId)
                .filter(session -> session.isLive(LocalDateTime.now()))
                .ifPresent(session -> platformUserRepository.findById(session.getPlatformUserId())
                        .filter(PlatformUser::isActive)
                        .ifPresent(staff -> {
                            AuthenticatedUser principal = new AuthenticatedUser(
                                    session.getAccountId(),
                                    staff.getId(),
                                    UserRole.ADMIN,
                                    supportActorName(staff.getName()),
                                    staff.getEmail(),
                                    session.getId());

                            UsernamePasswordAuthenticationToken authToken =
                                    new UsernamePasswordAuthenticationToken(
                                            principal, null, principal.getAuthorities());
                            authToken.setDetails(
                                    new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        }));
    }

    /** Authenticates platform staff for {@code /platform/**}. Re-reads the staff
     *  row each request, so deactivating one takes effect immediately. */
    private void authenticatePlatformStaff(String token, HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        UUID platformUserId;
        try {
            platformUserId = jwtService.validatePlatformToken(token).platformUserId();
        } catch (RuntimeException e) {
            return;
        }

        platformUserRepository.findById(platformUserId)
                .filter(PlatformUser::isActive)
                .ifPresent(staff -> {
                    PlatformPrincipal principal = new PlatformPrincipal(
                            staff.getId(), staff.getName(), staff.getEmail());
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    principal, null, principal.getAuthorities());
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                });
    }

    /**
     * Audit sentences are built from the principal's name at ~40 call sites, so
     * prefixing it here makes every one of them self-identify as support without
     * touching a single caller.
     */
    private static String supportActorName(String staffName) {
        return "Cognix Support (%s)".formatted(staffName);
    }

    /**
     * Parses the X-Property-Id header into a UUID, tolerating a missing/blank
     * header (returns null → "All properties") and a malformed value (ignored
     * rather than failing the whole request).
     */
    private UUID parsePropertyId(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(headerValue.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring malformed X-Property-Id header: {}", headerValue);
            return null;
        }
    }
}
