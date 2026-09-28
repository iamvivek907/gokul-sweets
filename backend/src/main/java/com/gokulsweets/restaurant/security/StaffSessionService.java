package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.staff.StaffUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class StaffSessionService {
    public static final String COOKIE_NAME = "gokul_staff";
    private final EnhancementProperties flags;
    private final JdbcTemplate jdbc;
    private final Clock inventoryClock;
    private final StaffMfaService mfa;
    private final com.gokulsweets.restaurant.staff.StaffUserRepository staff;
    private final AuthenticationManager authenticationManager;
    private final SecureRandom random = new SecureRandom();
    @org.springframework.beans.factory.annotation.Value("${staff.mfa.encryption-key:}")
    private String encryptionKey;

    public record SignIn(String token, String csrf, StaffUser user, boolean enrollmentRequired) {}
    public record Enrollment(String token, long staffId) {}
    public record Completed(SignIn session, java.util.List<String> recoveryCodes) {}
    public record Verified(long staffId, String username, String csrfHash, Instant createdAt) {}

    public SignIn login(String username, String password, String code, String ip, String previousCookie) {
        if (!flags.isSecureStaffSessions()) throw new IllegalStateException("Secure staff sessions are disabled.");
        if (username == null || !username.trim().matches("[A-Za-z0-9._@-]{2,100}")
                || password == null || password.isEmpty() || password.length() > 512)
            throw new IllegalArgumentException("Invalid sign-in details.");
        String normalized = username.trim().toLowerCase(java.util.Locale.ROOT);
        String limitKey = hash(normalized + ":" + ip);
        Instant now = Instant.now(inventoryClock);
        var locked = jdbc.query("SELECT locked_until FROM staff_login_limits WHERE username = ? FOR UPDATE",
                (rs, index) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(), limitKey);
        if (!locked.isEmpty() && locked.getFirst() != null && locked.getFirst().isAfter(now))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Too many sign-in attempts. Try again later.");
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(normalized, password));
            StaffUser user = staff.findByUsername(normalized).orElseThrow();
            if (!user.isActive()) throw new IllegalStateException("Staff account is inactive.");
            if (mfa.required(user)) {
                if (!mfa.enrolled(user.getId())) {
                    String token = randomToken();
                    jdbc.update("DELETE FROM staff_mfa_enrollments WHERE staff_id = ?", user.getId());
                    jdbc.update("INSERT INTO staff_mfa_enrollments(token_hash, staff_id, expires_at) VALUES (?, ?, ?)",
                            hash(token), user.getId(), Timestamp.from(now.plusSeconds(300)));
                    jdbc.update("DELETE FROM staff_login_limits WHERE username = ?", limitKey);
                    audit(user.getId(), "MFA_ENROLLMENT_STARTED");
                    return new SignIn(token, null, user, true);
                }
                if (!mfa.verify(user.getId(), code)) throw new IllegalArgumentException("Invalid authenticator code.");
            }
            revoke(previousCookie);
            jdbc.update("DELETE FROM staff_login_limits WHERE username = ?", limitKey);
            audit(user.getId(), "SIGN_IN");
            return issue(user);
        } catch (AuthenticationException | IllegalArgumentException error) {
            jdbc.update("""
                    INSERT INTO staff_login_limits(username, failures, locked_until, updated_at)
                    VALUES (?, 1, NULL, CURRENT_TIMESTAMP)
                    ON CONFLICT (username) DO UPDATE SET
                      failures = CASE WHEN staff_login_limits.updated_at < CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                        THEN 1 ELSE staff_login_limits.failures + 1 END,
                      locked_until = CASE WHEN staff_login_limits.updated_at >= CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                        AND staff_login_limits.failures >= 4
                        THEN CURRENT_TIMESTAMP + INTERVAL '10 minutes' ELSE NULL END,
                      updated_at = CURRENT_TIMESTAMP
                    """, limitKey);
            audit(null, "SIGN_IN_FAILED");
            throw new IllegalArgumentException("Invalid staff credentials or authenticator code.");
        }
    }

    @Transactional
    public Completed finishEnrollment(String enrollmentToken, String code, String previousCookie) {
        Enrollment enrollment = enrollment(enrollmentToken);
        var codes = mfa.confirm(enrollment.staffId(), hash(enrollmentToken), code);
        StaffUser user = staff.findDetailedById(enrollment.staffId()).orElseThrow();
        revoke(previousCookie);
        audit(user.getId(), "MFA_ENROLLED");
        return new Completed(issue(user), codes);
    }

    @Transactional(readOnly = true)
    public Enrollment enrollment(String token) {
        if (token == null || token.length() != 43) throw new IllegalArgumentException("Enrollment expired.");
        return jdbc.query("""
                SELECT staff_id FROM staff_mfa_enrollments WHERE token_hash = ? AND expires_at > ?
                """, (rs, index) -> new Enrollment(token, rs.getLong(1)), hash(token), Timestamp.from(Instant.now(inventoryClock)))
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Enrollment expired."));
    }

    @Transactional
    public SignIn issue(StaffUser user) {
        String token = randomToken(), csrf = derivedCsrf(token);
        jdbc.update("INSERT INTO staff_sessions(token_hash, staff_id, csrf_hash, staff_updated_at, expires_at) VALUES (?, ?, ?, ?, ?)",
                hash(token), user.getId(), hash(csrf), user.getUpdatedAt(),
                Timestamp.from(Instant.now(inventoryClock).plusSeconds(8 * 3600)));
        return new SignIn(token, csrf, user, false);
    }

    @Transactional(readOnly = true)
    public Verified verify(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        return jdbc.query("""
                SELECT u.id, u.username, s.csrf_hash, s.created_at FROM staff_sessions s
                JOIN staff_users u ON u.id = s.staff_id
                WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > ?
                  AND s.staff_updated_at = u.updated_at AND u.active
                """, (rs, row) -> new Verified(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()),
                hash(token), Timestamp.from(Instant.now(inventoryClock))).stream().findFirst().orElse(null);
    }

    @Transactional public void revoke(String token) {
        if (token != null && token.matches("[A-Za-z0-9_-]{43}"))
            jdbc.update("UPDATE staff_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ? AND revoked_at IS NULL",
                    hash(token));
    }
    public void requireCsrf(Verified session, String supplied) {
        if (session == null || supplied == null || !supplied.matches("[A-Za-z0-9_-]{43}")
                || !MessageDigest.isEqual(session.csrfHash().getBytes(StandardCharsets.US_ASCII),
                        hash(supplied).getBytes(StandardCharsets.US_ASCII)))
            throw new org.springframework.security.access.AccessDeniedException("Staff CSRF token is invalid.");
    }
    public String csrf(String token) {
        return verify(token) == null ? null : derivedCsrf(token);
    }
    private String derivedCsrf(String token) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(encryptionKey), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(("staff-csrf:" + token).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {throw new IllegalStateException("Staff CSRF setup failed.", error);}
    }
    public void auditSensitiveAction(long staffId) {audit(staffId, "SENSITIVE_ACTION");}
    private void audit(Long staffId, String event) {
        jdbc.update("INSERT INTO staff_auth_audit(staff_id, event) VALUES (?, ?)", staffId, event);
    }
    private String randomToken() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String hash(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch (java.security.NoSuchAlgorithmException e) {throw new IllegalStateException("SHA-256 unavailable.", e);}
    }
    public static String cookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
}
