package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.staff.StaffUserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class StaffSessionFilterTest {
    private final EnhancementProperties flags = new EnhancementProperties();
    private final StaffSessionService sessions = mock(StaffSessionService.class);
    private final StaffSessionFilter filter = new StaffSessionFilter(flags, sessions,
            mock(StaffUserDetailsService.class), mock(StaffUserRepository.class), mock(StaffMfaService.class),
            new WebCorsProperties(), new MockEnvironment());
    private final FilterChain chain = mock(FilterChain.class);

    @Test void existingPolicyRemainsUntilCoordinatedRollout() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/admin/auth/me");
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(any(), any());
    }
    @Test void basicAndCustomerCookieCannotBypassStaffSession() throws Exception {
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
    @Test void stateChangesRejectForeignOriginBeforeCredentialCheck() throws Exception {
        flags.setSecureStaffSessions(true);
        var request = new MockHttpServletRequest("POST", "/api/admin/orders/123/status");
        request.addHeader("Origin", "https://attacker.invalid");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        verifyNoInteractions(chain);
    }
}
