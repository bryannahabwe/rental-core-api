package com.cognix.rentalcoreapi.shared.security;

import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
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
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PropertyAccessGuard propertyAccessGuard;

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

            // isTokenValid rejects expired tokens and special-purpose tokens
            // (e.g. INVITE), so those can't be used to authenticate a request.
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
