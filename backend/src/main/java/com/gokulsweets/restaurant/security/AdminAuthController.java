package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.config.EnhancementProperties;
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

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {
    private final StaffAuthorizationService staffAuthorizationService;
    private final StaffSessionService sessions;
    private final StaffMfaService mfa;
    private final EnhancementProperties flags;

    @GetMapping("/me")
    public ResponseEntity<AdminAuthResponse> me(HttpServletRequest request) {
        var result = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (flags.isSecureStaffSessions()) {
            String csrf = sessions.csrf(StaffSessionService.cookie(request));
            if (csrf == null) return ResponseEntity.status(401).build();
            result.header("X-Staff-CSRF", csrf);
        }
        return result.body(AdminAuthResponse.from(staffAuthorizationService.getCurrentStaff()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Login input, HttpServletRequest request) {
        if (!flags.isSecureStaffSessions()) return ResponseEntity.notFound().build();
        var signIn = sessions.login(input.username(), input.password(), input.code(),
                request.getRemoteAddr(), StaffSessionService.cookie(request));
        if (signIn.enrollmentRequired())
            return ResponseEntity.status(428).cacheControl(CacheControl.noStore())
                    .body(new EnrollmentResponse(signIn.token()));
        return signedIn(signIn, null);
    }

    @PostMapping("/mfa/setup")
    public ResponseEntity<StaffMfaService.Setup> setup(@RequestBody EnrollmentRequest input) {
        if (!flags.isSecureStaffSessions()) return ResponseEntity.notFound().build();
        long staffId = sessions.enrollment(input.token()).staffId();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mfa.setup(staffId,
                StaffSessionService.hash(input.token())));
    }

    @PostMapping("/mfa/confirm")
    public ResponseEntity<?> confirm(@RequestBody Confirmation input, HttpServletRequest request) {
        if (!flags.isSecureStaffSessions()) return ResponseEntity.notFound().build();
        var result = sessions.finishEnrollment(input.token(), input.code(), StaffSessionService.cookie(request));
        return signedIn(result.session(), result.recoveryCodes());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        if (!flags.isSecureStaffSessions()) return ResponseEntity.notFound().build();
        sessions.revoke(StaffSessionService.cookie(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    private ResponseEntity<LoginResponse> signedIn(StaffSessionService.SignIn session, List<String> recovery) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(session.token(), StaffSessionService.sessionLifetime(session.user())).toString())
                .header("X-Staff-CSRF", session.csrf())
                .body(new LoginResponse(AdminAuthResponse.from(session.user()), recovery));
    }
    static ResponseCookie cookie(String value, Duration age) {
        return ResponseCookie.from(StaffSessionService.COOKIE_NAME, value)
                .httpOnly(true).secure(true).sameSite("Lax").path("/api/admin")
                .maxAge(age).build();
    }
    public record Login(String username, String password, String code) {}
    public record EnrollmentRequest(String token) {}
    public record Confirmation(String token, String code) {}
    public record EnrollmentResponse(String enrollmentToken) {}
    public record LoginResponse(AdminAuthResponse profile, List<String> recoveryCodes) {}
}
