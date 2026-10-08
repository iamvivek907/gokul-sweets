package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class StaffSessionFilter extends OncePerRequestFilter {
    private final EnhancementProperties flags;
    private final StaffSessionService sessions;
    private final StaffUserDetailsService users;
    private final com.gokulsweets.restaurant.staff.StaffUserRepository staff;
    private final StaffMfaService mfa;
    private final WebCorsProperties cors;
    private final Environment environment;

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!flags.isSecureStaffSessions() || !request.getRequestURI().startsWith("/api/admin/")) {
            chain.doFilter(request, response); return;
        }
        if (request.getHeader("Authorization") != null) {response.sendError(401); return;}
        boolean changing = !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        String origin = request.getHeader("Origin");
        if (changing && (origin == null || !cors.effectiveAllowedOrigins(environment).contains(origin))) {
            response.sendError(403); return;
        }
        String path = request.getRequestURI();
        if (path.equals("/api/admin/auth/login") || path.equals("/api/admin/auth/mfa/setup")
                || path.equals("/api/admin/auth/mfa/confirm") || path.equals("/api/admin/auth/owner-setup")
                || path.equals("/api/admin/auth/owner-recovery")) {
            chain.doFilter(request, response); return;
        }
        var verified = sessions.verify(StaffSessionService.cookie(request));
        if (verified == null) {response.sendError(401); return;}
        var current = staff.findDetailedById(verified.staffId()).orElse(null);
        if (current == null || !current.isActive() || (mfa.required(current) && !mfa.enrolled(current.getId()))) {
            response.sendError(401); return;
        }
        if (changing) {
            try {sessions.requireCsrf(verified, request.getHeader("X-Staff-CSRF"));}
            catch (org.springframework.security.access.AccessDeniedException error) {response.sendError(403); return;}
        }
        if (changing && (path.startsWith("/api/admin/staff") || path.startsWith("/api/admin/payroll")
                || path.contains("refund") || path.startsWith("/api/admin/approvals"))) {
            if (verified.createdAt().plusSeconds(600).isBefore(java.time.Instant.now())) {
                response.sendError(401, "Sign in again before this action."); return;
            }
            sessions.auditSensitiveAction(verified.staffId());
        }
        String token=StaffSessionService.cookie(request);
        if(!path.equals("/api/admin/auth/logout") && !StaffSessionService.isAdministrator(current)) {
            if(!sessions.renewStaff(verified,token,current)){response.sendError(401);return;}
            response.addHeader("Set-Cookie",AdminAuthController.cookie(token,StaffSessionService.sessionLifetime(current)).toString());
        }
        UserDetails details = users.loadUserByUsername(verified.username());
        var auth = UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {chain.doFilter(request, response);}
        finally {SecurityContextHolder.clearContext();}
    }
}
