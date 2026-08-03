package com.cognix.rentalcoreapi.shared.security;

import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * The read-only gate is the control that makes support access safe, so it is
 * tested directly rather than through a slice.
 */
class SupportReadOnlyFilterTest {

    private static final UUID CUSTOMER_ACCOUNT = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();

    private final SupportReadOnlyFilter filter = new SupportReadOnlyFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void refusesEveryWriteDuringASupportSession(String method) throws Exception {
        authenticateSupportSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest(method, "/tenants"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("read-only");
        verify(chain, never()).doFilter(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    void letsReadsThroughDuringASupportSession(String method) throws Exception {
        authenticateSupportSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest(method, "/tenants"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void refusesTheReceiptNumberEndpoint() throws Exception {
        // A write wearing a read's clothing — it consumes the customer's next
        // receipt number, so support must not be able to call it.
        authenticateSupportSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest("POST", "/settings/receipt-number"),
                response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void leavesOrdinaryUsersAlone() throws Exception {
        authenticateOrdinaryUser();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest("POST", "/tenants"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void leavesUnauthenticatedRequestsAlone() throws Exception {
        // Nothing in the context yet — the login endpoint, for instance. The
        // security chain, not this filter, decides those.
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest("POST", "/auth/login"), response, chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void leavesPlatformStaffAlone() throws Exception {
        // Ending a session is a POST made with a PLATFORM token, so it must not
        // be caught by the gate that the session itself is subject to.
        PlatformPrincipal staff = new PlatformPrincipal(
                UUID.randomUUID(), "Brian Nahabwe", "brian@cognix.example");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(staff, null, staff.getAuthorities()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest("POST", "/platform/support-sessions/x/end"),
                response, chain);

        verify(chain).doFilter(any(), any());
    }

    private void authenticateSupportSession() {
        AuthenticatedUser principal = new AuthenticatedUser(
                CUSTOMER_ACCOUNT, UUID.randomUUID(), UserRole.ADMIN,
                "Cognix Support (Brian Nahabwe)", "brian@cognix.example", SESSION);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }

    private void authenticateOrdinaryUser() {
        AuthenticatedUser principal = new AuthenticatedUser(
                CUSTOMER_ACCOUNT, UUID.randomUUID(), UserRole.ADMIN, "Amina", "+256700000000");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }
}
