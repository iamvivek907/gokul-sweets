package com.gokulsweets.restaurant.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.staff.Role;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.StaffUserRepository;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.User;

import java.time.Instant;
import java.util.Optional;

class StaffSessionFilterTest {
    private final EnhancementProperties flags = new EnhancementProperties();
    private final StaffSessionService sessions = mock(StaffSessionService.class);
    private final StaffUserDetailsService users = mock(StaffUserDetailsService.class);
    private final StaffUserRepository staff = mock(StaffUserRepository.class);
    private final StaffSessionFilter filter =
            new StaffSessionFilter(
                    flags,
                    sessions,
                    users,
                    staff,
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

    @Test
    void olderNonAdminSessionRequiresFreshSignInBeforeStaffWrites() throws Exception {
        flags.setSecureStaffSessions(true);
        var user = staffWithRole("MANAGER");
        when(staff.findDetailedById(1L)).thenReturn(Optional.of(user));
        when(sessions.verify("cookie"))
                .thenReturn(
                        new StaffSessionService.Verified(
                                1L, "staff", "csrf", Instant.now().minusSeconds(601)));

        for (var request : staffWrites()) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);

            assertEquals(403, response.getStatus());
            assertEquals("application/json", response.getContentType());
            assertEquals("no-store", response.getHeader("Cache-Control"));
            assertTrue(response.getContentAsString().contains("STAFF_REAUTH_REQUIRED"));
        }
        verifyNoInteractions(chain);
        verify(sessions, never()).auditSensitiveAction(anyLong());
    }

    @Test
    void staleCsrfTokenRejectsWriteBeforeAuditOrController() throws Exception {
        flags.setSecureStaffSessions(true);
        when(staff.findDetailedById(1L)).thenReturn(Optional.of(staffWithRole("OWNER_ADMIN")));
        var verified = new StaffSessionService.Verified(1L, "staff", "csrf", Instant.now());
        when(sessions.verify("cookie")).thenReturn(verified);
        doThrow(new org.springframework.security.access.AccessDeniedException("Invalid CSRF"))
                .when(sessions)
                .requireCsrf(verified, "csrf");

        for (var request : staffWrites()) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("STAFF_CSRF_INVALID"));
        }
        verifyNoInteractions(chain);
        verify(sessions, never()).auditSensitiveAction(anyLong());
    }

    @Test
    void adminNearTwelveHourExpiryCanCreateStaffAndResetPasswords() throws Exception {
        flags.setSecureStaffSessions(true);
        var user = staffWithRole("OWNER_ADMIN");
        when(staff.findDetailedById(1L)).thenReturn(Optional.of(user));
        when(sessions.verify("cookie"))
                .thenReturn(
                        new StaffSessionService.Verified(
                                1L,
                                "staff",
                                "csrf",
                                Instant.now().minusSeconds(11 * 3600 + 59 * 60)));
        when(users.loadUserByUsername("staff"))
                .thenReturn(
                        User.withUsername("staff")
                                .password("unused")
                                .authorities("STAFF_MANAGE")
                                .build());

        for (var request : staffWrites()) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus());
            verify(chain).doFilter(request, response);
        }
        verify(sessions, times(2)).requireCsrf(any(), eq("csrf"));
        verify(sessions, times(2)).auditSensitiveAction(1L);
        verify(sessions, never()).renewStaff(any(), any(), any());
    }

    private static StaffUser staffWithRole(String roleName) {
        var role = new Role();
        role.setName(roleName);
        var staff = new StaffUser();
        staff.setActive(true);
        staff.setRole(role);
        return staff;
    }

    private static java.util.List<MockHttpServletRequest> staffWrites() {
        var create = new MockHttpServletRequest("POST", "/api/admin/staff");
        var reset = new MockHttpServletRequest("PATCH", "/api/admin/staff/2/password");
        for (var request : java.util.List.of(create, reset)) {
            request.addHeader("Origin", "http://localhost:3000");
            request.addHeader("X-Staff-CSRF", "csrf");
            request.setCookies(
                    new jakarta.servlet.http.Cookie(StaffSessionService.COOKIE_NAME, "cookie"));
        }
        return java.util.List.of(create, reset);
    }
}
