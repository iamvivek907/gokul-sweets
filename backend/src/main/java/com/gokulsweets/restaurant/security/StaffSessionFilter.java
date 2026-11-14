package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;

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

/** Backend staff session filter contract and implementation. */
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

    /**
     * Dos filter internal.
     *
     * @param request the request
     * @param response the response
     * @param chain the chain
     * @throws ServletException if the operation cannot complete
     * @throws IOException if the operation cannot complete
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffSessionFilter.class,
                        "doFilterInternal(HttpServletRequest,HttpServletResponse,FilterChain)");
        try {
            if (!flags.isSecureStaffSessions()
                    || !request.getRequestURI().startsWith("/api/admin/")) {
                chain.doFilter(request, response);
                return;
            }
            if (request.getHeader("Authorization") != null) {
                response.sendError(401);
                return;
            }
            boolean changing = !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
            String origin = request.getHeader("Origin");
            if (changing
                    && (origin == null
                            || !cors.effectiveAllowedOrigins(environment).contains(origin))) {
                response.sendError(403);
                return;
            }
            String path = request.getRequestURI();
            if (path.equals("/api/admin/auth/login")
                    || path.equals("/api/admin/auth/mfa/setup")
                    || path.equals("/api/admin/auth/mfa/confirm")
                    || path.equals("/api/admin/auth/owner-setup")
                    || path.equals("/api/admin/auth/owner-recovery")) {
                chain.doFilter(request, response);
                return;
            }
            var verified = sessions.verify(StaffSessionService.cookie(request));
            if (verified == null) {
                response.sendError(401);
                return;
            }
            var current = staff.findDetailedById(verified.staffId()).orElse(null);
            if (current == null
                    || !current.isActive()
                    || (mfa.required(current) && !mfa.enrolled(current.getId()))) {
                response.sendError(401);
                return;
            }
            if (changing) {
                try {
                    sessions.requireCsrf(verified, request.getHeader("X-Staff-CSRF"));
                } catch (org.springframework.security.access.AccessDeniedException error) {
                    rejectWrite(response, "STAFF_CSRF_INVALID");
                    return;
                }
            }
            boolean administrator = StaffSessionService.isAdministrator(current);
            if (changing
                    && (path.startsWith("/api/admin/staff")
                            || path.startsWith("/api/admin/payroll")
                            || path.contains("refund")
                            || path.startsWith("/api/admin/approvals"))) {
                if (!administrator
                        && verified.createdAt()
                                .plusSeconds(600)
                                .isBefore(java.time.Instant.now())) {
                    rejectWrite(response, "STAFF_REAUTH_REQUIRED");
                    return;
                }
                sessions.auditSensitiveAction(verified.staffId());
            }
            String token = StaffSessionService.cookie(request);
            if (!path.equals("/api/admin/auth/logout") && !administrator) {
                if (!sessions.renewStaff(verified, token, current)) {
                    response.sendError(401);
                    return;
                }
                response.addHeader(
                        "Set-Cookie",
                        AdminAuthController.cookie(
                                        token, StaffSessionService.sessionLifetime(current))
                                .toString());
            }
            UserDetails details = users.loadUserByUsername(verified.username());
            var auth =
                    UsernamePasswordAuthenticationToken.authenticated(
                            details, null, details.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                chain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffSessionFilter.class,
                    "doFilterInternal(HttpServletRequest,HttpServletResponse,FilterChain)");
        }
    }

    /**
     * Returns a structured rejection before an authenticated write reaches its handler.
     *
     * @param response the response for the rejected request
     * @param code the staff session error code recognized by the admin client
     * @throws IOException if the error response cannot be written
     */
    private static void rejectWrite(HttpServletResponse response, String code) throws IOException {
        final long started =
                MethodTiming.start(
                        StaffSessionFilter.class, "rejectWrite(HttpServletResponse,String)");
        try {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setHeader("Cache-Control", "no-store");
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"" + code + "\"}");
        } finally {
            MethodTiming.finish(
                    started, StaffSessionFilter.class, "rejectWrite(HttpServletResponse,String)");
        }
    }
}
