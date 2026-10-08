package com.gokulsweets.restaurant.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.staff.StaffUserRepository;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class StaffSessionFilterTest {
    private final EnhancementProperties flags = new EnhancementProperties();
    private final StaffSessionService sessions = mock(StaffSessionService.class);
    private final StaffSessionFilter filter =
            new StaffSessionFilter(
                    flags,
                    sessions,
                    mock(StaffUserDetailsService.class),
                    mock(StaffUserRepository.class),
                    mock(StaffMfaService.class),
                    new WebCorsProperties(),
                    new MockEnvironment());
    private final FilterChain chain = mock(FilterChain.class);

    @Test
    void existingPolicyRemainsUntilCoordinatedRollout() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/admin/auth/me");
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void basicAndCustomerCookieCannotBypassStaffSession() throws Exception {
        flags.setSecureStaffSessions(true);
        var basic = new MockHttpServletRequest("GET", "/api/admin/auth/me");
        basic.addHeader("Authorization", "Basic aGVsbG8=");
        var response = new MockHttpServletResponse();
        filter.doFilter(basic, response, chain);
        assertEquals(401, response.getStatus());
        var customer = new MockHttpServletRequest("GET", "/api/admin/auth/me");
        customer.setCookies(new jakarta.servlet.http.Cookie("gokul_customer", "phone-session"));
        response = new MockHttpServletResponse();
        filter.doFilter(customer, response, chain);
        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void stateChangesRejectForeignOriginBeforeCredentialCheck() throws Exception {
        flags.setSecureStaffSessions(true);
        var request = new MockHttpServletRequest("POST", "/api/admin/orders/123/status");
        request.addHeader("Origin", "https://attacker.invalid");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void publicOwnerRoutesStillRequireTrustedOriginAndDoNotOpenProtectedRoutes() throws Exception {
        flags.setSecureStaffSessions(true);
        for (String path :
                java.util.List.of(
                        "/api/admin/auth/owner-setup", "/api/admin/auth/owner-recovery")) {
            for (String origin : java.util.List.of("https://attacker.invalid", "")) {
                var request = new MockHttpServletRequest("POST", path);
                if (!origin.isEmpty()) request.addHeader("Origin", origin);
                var response = new MockHttpServletResponse();
                filter.doFilter(request, response, chain);
                assertEquals(403, response.getStatus());
            }
            var trusted = new MockHttpServletRequest("POST", path);
            trusted.addHeader("Origin", "http://localhost:3000");
            filter.doFilter(trusted, new MockHttpServletResponse(), chain);
        }
        verify(chain, times(2)).doFilter(any(), any());
        var account = new MockHttpServletRequest("GET", "/api/admin/account-security");
        var response = new MockHttpServletResponse();
        filter.doFilter(account, response, chain);
        assertEquals(401, response.getStatus());
        verify(sessions).verify(null);
    }

    @Test
    void existingLoginAndProtectedMutationsRejectMissingOriginWithoutServerError()
            throws Exception {
        flags.setSecureStaffSessions(true);
        for (String path :
                java.util.List.of("/api/admin/auth/login", "/api/admin/orders/123/status")) {
            var response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("POST", path), response, chain);
            assertEquals(403, response.getStatus());
        }
        verifyNoInteractions(chain, sessions);
    }
}
