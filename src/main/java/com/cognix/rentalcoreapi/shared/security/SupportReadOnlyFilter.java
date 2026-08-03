package com.cognix.rentalcoreapi.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Refuses every write made inside a support session.
 *
 * <p>Support principals present as ADMIN so they can read what the customer
 * reads, which means method security alone would let them write too. Rather than
 * annotate every mutating endpoint — a list that would silently fall behind the
 * next feature — this is one chokepoint: if the request isn't a read, it doesn't
 * happen.
 *
 * <p>Note that {@code POST /settings/receipt-number} is a write wearing a read's
 * clothing (it increments a counter), and is correctly refused here: support has
 * no business consuming a customer's receipt number.
 *
 * <p>Ending a session is {@code POST /platform/support-sessions/{id}/end}, which
 * is called with a PLATFORM token rather than a SUPPORT one, so it is not
 * affected.
 */
@Slf4j
@Component
public class SupportReadOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (isSupportSession() && !READ_METHODS.contains(request.getMethod())) {
            log.warn("Refused {} {} — support sessions are read-only",
                    request.getMethod(), request.getRequestURI());
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"message\":\"Support sessions are read-only\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSupportSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user
                && user.isSupportSession();
    }
}
