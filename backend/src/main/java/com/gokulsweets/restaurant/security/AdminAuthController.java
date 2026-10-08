package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.dto.AdminAuthResponse;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

/** HTTP endpoints for admin auth operations. */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final StaffAuthorizationService staffAuthorizationService;

    private final StaffSessionService sessions;

    private final StaffMfaService mfa;

    private final EnhancementProperties flags;

    /**
     * Mes the operation.
     *
     * @param request the request
     * @return the me result
     */
    @GetMapping("/me")
    public ResponseEntity<AdminAuthResponse> me(HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminAuthController.class, "me(HttpServletRequest)");
        try {
            var result = ResponseEntity.ok().cacheControl(CacheControl.noStore());
            if (flags.isSecureStaffSessions()) {
                String csrf = sessions.csrf(StaffSessionService.cookie(request));
                if (csrf == null) return ResponseEntity.status(401).build();
                result.header("X-Staff-CSRF", csrf);
            }
            return result.body(AdminAuthResponse.from(staffAuthorizationService.getCurrentStaff()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminAuthController.class, "me(HttpServletRequest)");
        }
    }

    /**
     * Logins the operation.
     *
     * @param input the input
     * @param request the request
     * @return the login result
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Login input, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminAuthController.class, "login(Login,HttpServletRequest)");
        try {
            if (!flags.isSecureStaffSessions()) return ResponseEntity.notFound().build();
            var signIn =
                    sessions.login(
                            input.username(),
                            input.password(),
                            input.code(),
                            request.getRemoteAddr(),
                            StaffSessionService.cookie(request));
            if (signIn.enrollmentRequired())
                return ResponseEntity.status(428)
                        .cacheControl(CacheControl.noStore())
                        .body(new EnrollmentResponse(signIn.token()));
            return signedIn(signIn, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminAuthController.class,
                    "login(Login,HttpServletRequest)");
        }
    }

    /**
     * Setups the operation.
     *
     * @param input the input
     * @return the setup result
     */
    @PostMapping("/mfa/setup")
    public ResponseEntity<StaffMfaService.Setup> setup(@RequestBody EnrollmentRequest input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminAuthController.class, "setup(EnrollmentRequest)");
        try {
            if (!flags.isSecureStaffSessions()) return ResponseEntity.notFound().build();
            long staffId = sessions.enrollment(input.token()).staffId();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(mfa.setup(staffId, StaffSessionService.hash(input.token())));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminAuthController.class,
                    "setup(EnrollmentRequest)");
        }
    }

    /**
     * Confirms the operation.
     *
     * @param input the input
     * @param request the request
     * @return the confirm result
     */
    @PostMapping("/mfa/confirm")
    public ResponseEntity<?> confirm(@RequestBody Confirmation input, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminAuthController.class, "confirm(Confirmation,HttpServletRequest)");
        try {
            if (!flags.isSecureStaffSessions()) return ResponseEntity.notFound().build();
            var result =
                    sessions.finishEnrollment(
                            input.token(), input.code(), StaffSessionService.cookie(request));
            return signedIn(result.session(), result.recoveryCodes());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminAuthController.class,
                    "confirm(Confirmation,HttpServletRequest)");
        }
    }

    /**
     * Logouts the operation.
     *
     * @param request the request
     * @return the logout result
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminAuthController.class, "logout(HttpServletRequest)");
        try {
            if (!flags.isSecureStaffSessions()) return ResponseEntity.notFound().build();
            sessions.revoke(StaffSessionService.cookie(request));
            return ResponseEntity.noContent()
                    .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                    .build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminAuthController.class,
                    "logout(HttpServletRequest)");
        }
    }

    /**
     * Signeds in.
     *
     * @param session the session
     * @param recovery the recovery
     * @return the signed in result
     */
    private ResponseEntity<LoginResponse> signedIn(
            StaffSessionService.SignIn session, List<String> recovery) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminAuthController.class,
                        "signedIn(StaffSessionService.SignIn,List<String>)");
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(
                            HttpHeaders.SET_COOKIE,
                            cookie(
                                            session.token(),
                                            StaffSessionService.sessionLifetime(session.user()))
                                    .toString())
                    .header("X-Staff-CSRF", session.csrf())
                    .body(new LoginResponse(AdminAuthResponse.from(session.user()), recovery));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminAuthController.class,
                    "signedIn(StaffSessionService.SignIn,List<String>)");
        }
    }

    /**
     * Cookies the operation.
     *
     * @param value the value
     * @param age the age
     * @return the cookie result
     */
    static ResponseCookie cookie(String value, Duration age) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminAuthController.class, "cookie(String,Duration)");
        try {
            return ResponseCookie.from(StaffSessionService.COOKIE_NAME, value)
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Lax")
                    .path("/api/admin")
                    .maxAge(age)
                    .build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminAuthController.class,
                    "cookie(String,Duration)");
        }
    }

    /**
     * Immutable login data contract.
     *
     * @param username the username
     * @param password the password
     * @param code the code
     */
    public record Login(String username, String password, String code) {}

    /**
     * Immutable enrollment request data contract.
     *
     * @param token the token
     */
    public record EnrollmentRequest(String token) {}

    /**
     * Immutable confirmation data contract.
     *
     * @param token the token
     * @param code the code
     */
    public record Confirmation(String token, String code) {}

    /**
     * Immutable enrollment response data contract.
     *
     * @param enrollmentToken the enrollment token
     */
    public record EnrollmentResponse(String enrollmentToken) {}

    /**
     * Immutable login response data contract.
     *
     * @param profile the profile
     * @param recoveryCodes the recovery codes
     */
    public record LoginResponse(AdminAuthResponse profile, List<String> recoveryCodes) {}
}
