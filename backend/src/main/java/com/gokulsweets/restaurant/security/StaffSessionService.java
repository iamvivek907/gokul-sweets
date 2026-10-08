package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
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

/** Coordinates staff session operations. */
@Service
@RequiredArgsConstructor
public class StaffSessionService {

    public static final String COOKIE_NAME = AppConstant.STAFF_SESSION_SERVICE_COOKIE_NAME;

    private final EnhancementProperties flags;

    private final JdbcTemplate jdbc;

    private final Clock inventoryClock;

    private final StaffMfaService mfa;

    private final com.gokulsweets.restaurant.staff.StaffUserRepository staff;

    private final AuthenticationManager authenticationManager;

    private final org.springframework.security.crypto.password.PasswordEncoder passwords;

    private final SecureRandom random = new SecureRandom();

    @org.springframework.beans.factory.annotation.Value("${staff.mfa.encryption-key:}")
    private String encryptionKey;

    /** Immutable sign in data contract. */
    public record SignIn(String token, String csrf, StaffUser user, boolean enrollmentRequired) {}

    /** Immutable enrollment data contract. */
    public record Enrollment(String token, long staffId) {}

    /** Immutable completed data contract. */
    public record Completed(SignIn session, java.util.List<String> recoveryCodes) {}

    /** Immutable verified data contract. */
    public record Verified(long staffId, String username, String csrfHash, Instant createdAt) {}

    /**
     * Logins the operation.
     *
     * @param username the username
     * @param password the password
     * @param code the code
     * @param ip the ip
     * @param previousCookie the previous cookie
     * @return the login result
     */
    public SignIn login(
            String username, String password, String code, String ip, String previousCookie) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffSessionService.class, "login(String,String,String,String,String)");
        try {
            if (!flags.isSecureStaffSessions())
                throw new IllegalStateException("Secure staff sessions are disabled.");
            if (username == null
                    || !username.trim().matches("[A-Za-z0-9._@-]{2,100}")
                    || password == null
                    || password.isEmpty()
                    || password.length() > 512)
                throw new IllegalArgumentException("Invalid sign-in details.");
            String normalized = username.trim().toLowerCase(java.util.Locale.ROOT);
            String limitKey = hash(normalized + ":" + ip);
            Instant now = Instant.now(inventoryClock);
            var locked =
                    jdbc.query(
                            "SELECT locked_until FROM staff_login_limits WHERE username = ? FOR"
                                    + " UPDATE",
                            (rs, index) ->
                                    rs.getTimestamp(1) == null
                                            ? null
                                            : rs.getTimestamp(1).toInstant(),
                            limitKey);
            if (!locked.isEmpty() && locked.getFirst() != null && locked.getFirst().isAfter(now))
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "Too many sign-in attempts. Try again later.");
            try {
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(normalized, password));
                StaffUser user = staff.findByUsername(normalized).orElseThrow();
                // Authentication and the subsequent account read can race a credential reset.
                if (!passwords.matches(password, user.getPasswordHash()))
                    throw new IllegalArgumentException(
                            "Invalid staff credentials or authenticator code.");
                if (!user.isActive()) throw new IllegalStateException("Staff account is inactive.");
                if (mfa.required(user)) {
                    if (!mfa.enrolled(user.getId())) {
                        String token = randomToken();
                        jdbc.update(
                                "DELETE FROM staff_mfa_enrollments WHERE staff_id = ?",
                                user.getId());
                        jdbc.update(
                                "INSERT INTO staff_mfa_enrollments(token_hash, staff_id,"
                                        + " expires_at, staff_updated_at) VALUES (?, ?, ?, ?)",
                                hash(token),
                                user.getId(),
                                Timestamp.from(now.plusSeconds(300)),
                                Timestamp.valueOf(user.getUpdatedAt()));
                        jdbc.update("DELETE FROM staff_login_limits WHERE username = ?", limitKey);
                        audit(user.getId(), "MFA_ENROLLMENT_STARTED");
                        return new SignIn(token, null, user, true);
                    }
                    if (!mfa.verify(user.getId(), code))
                        throw new IllegalArgumentException("Invalid authenticator code.");
                }
                revoke(previousCookie);
                jdbc.update("DELETE FROM staff_login_limits WHERE username = ?", limitKey);
                audit(user.getId(), "SIGN_IN");
                return issue(user);
            } catch (AuthenticationException | IllegalArgumentException error) {
                jdbc.update(
                        """
INSERT INTO staff_login_limits(username, failures, locked_until, updated_at)
VALUES (?, 1, NULL, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO UPDATE SET
  failures = CASE WHEN staff_login_limits.updated_at < CURRENT_TIMESTAMP - INTERVAL '10 minutes'
    THEN 1 ELSE staff_login_limits.failures + 1 END,
  locked_until = CASE WHEN staff_login_limits.updated_at >= CURRENT_TIMESTAMP - INTERVAL '10 minutes'
    AND staff_login_limits.failures >= 4
    THEN CURRENT_TIMESTAMP + INTERVAL '10 minutes' ELSE NULL END,
  updated_at = CURRENT_TIMESTAMP
""",
                        limitKey);
                audit(null, "SIGN_IN_FAILED");
                throw new IllegalArgumentException(
                        "Invalid staff credentials or authenticator code.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffSessionService.class,
                    "login(String,String,String,String,String)");
        }
    }

    /**
     * Finishes enrollment.
     *
     * @param enrollmentToken the enrollment token
     * @param code the code
     * @param previousCookie the previous cookie
     * @return the finish enrollment result
     */
    @Transactional
    public Completed finishEnrollment(String enrollmentToken, String code, String previousCookie) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffSessionService.class, "finishEnrollment(String,String,String)");
        try {
            Enrollment enrollment = enrollment(enrollmentToken);
            var codes = mfa.confirm(enrollment.staffId(), hash(enrollmentToken), code);
            StaffUser user = staff.findDetailedById(enrollment.staffId()).orElseThrow();
            revoke(previousCookie);
            audit(user.getId(), "MFA_ENROLLED");
            return new Completed(issue(user), codes);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffSessionService.class,
                    "finishEnrollment(String,String,String)");
        }
    }

    /**
     * Enrollments the operation.
     *
     * @param token the token
     * @return the enrollment result
     */
    @Transactional(readOnly = true)
    public Enrollment enrollment(String token) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffSessionService.class, "enrollment(String)");
        try {
            if (token == null || token.length() != 43)
                throw new IllegalArgumentException("Enrollment expired.");
            return jdbc
                    .query(
                            """
SELECT e.staff_id FROM staff_mfa_enrollments e JOIN staff_users u ON u.id=e.staff_id
WHERE e.token_hash = ? AND e.expires_at > ? AND u.active AND e.staff_updated_at=u.updated_at
""",
                            (rs, index) -> new Enrollment(token, rs.getLong(1)),
                            hash(token),
                            Timestamp.from(Instant.now(inventoryClock)))
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Enrollment expired."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffSessionService.class, "enrollment(String)");
        }
    }

    /**
     * Issues the operation.
     *
     * @param user the user
     * @return the issue result
     */
    @Transactional
    public SignIn issue(StaffUser user) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffSessionService.class, "issue(StaffUser)");
        try {
            String token = randomToken(), csrf = derivedCsrf(token);
            Instant issuedAt = Instant.now(inventoryClock);
            jdbc.update(
                    "INSERT INTO staff_sessions(token_hash, staff_id, csrf_hash, staff_updated_at,"
                            + " expires_at,created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    hash(token),
                    user.getId(),
                    hash(csrf),
                    user.getUpdatedAt(),
                    Timestamp.from(issuedAt.plus(sessionLifetime(user))),
                    Timestamp.from(issuedAt));
            return new SignIn(token, csrf, user, false);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffSessionService.class, "issue(StaffUser)");
        }
    }

    /**
     * Reports whether administrator.
     *
     * @param user the user
     * @return the is administrator result
     */
    public static boolean isAdministrator(StaffUser user) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffSessionService.class, "isAdministrator(StaffUser)");
        try {
            return user.getRole().getName().toUpperCase(java.util.Locale.ROOT).contains("ADMIN");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffSessionService.class,
                    "isAdministrator(StaffUser)");
        }
    }

    /**
     * Sessions lifetime.
     *
     * @param user the user
     * @return the session lifetime result
     */
    public static java.time.Duration sessionLifetime(StaffUser user) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffSessionService.class, "sessionLifetime(StaffUser)");
        try {
            return isAdministrator(user)
                    ? java.time.Duration.ofHours(15)
                    : java.time.Duration.ofDays(365);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffSessionService.class,
                    "sessionLifetime(StaffUser)");
        }
    }

    /**
     * Renews staff.
     *
     * @param verified the verified
     * @param token the token
     * @param user the user
     * @return the renew staff result
     */
    @Transactional
    public boolean renewStaff(Verified verified, String token, StaffUser user) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffSessionService.class, "renewStaff(Verified,String,StaffUser)");
        try {
            if (isAdministrator(user)) return false;
            Instant now = Instant.now(inventoryClock);
            return jdbc.update(
                            "UPDATE staff_sessions SET expires_at=? WHERE token_hash=? AND"
                                + " staff_id=? AND revoked_at IS NULL AND expires_at>? AND"
                                + " staff_updated_at=? AND EXISTS(SELECT 1 FROM staff_users u JOIN"
                                + " roles r ON r.id=u.role_id WHERE u.id=staff_sessions.staff_id"
                                + " AND u.active AND u.updated_at=staff_sessions.staff_updated_at"
                                + " AND UPPER(r.name) NOT LIKE '%ADMIN%')",
                            Timestamp.from(now.plus(sessionLifetime(user))),
                            hash(token),
                            verified.staffId(),
                            Timestamp.from(now),
                            user.getUpdatedAt())
                    == 1;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffSessionService.class,
                    "renewStaff(Verified,String,StaffUser)");
        }
    }

    /**
     * Verify the operation.
     *
     * @param token the token
     * @return the verify result
     */
    @Transactional(readOnly = true)
    public Verified verify(String token) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffSessionService.class, "verify(String)");
        try {
            if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
            return jdbc
                    .query(
                            """
SELECT u.id, u.username, s.csrf_hash, s.created_at FROM staff_sessions s
JOIN staff_users u ON u.id = s.staff_id
JOIN roles r ON r.id = u.role_id
WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > ?
  AND s.staff_updated_at = u.updated_at AND u.active
  AND (UPPER(r.name) NOT LIKE '%ADMIN%' OR s.created_at > ?)
""",
                            (rs, row) ->
                                    new Verified(
                                            rs.getLong(1),
                                            rs.getString(2),
                                            rs.getString(3),
                                            rs.getTimestamp(4).toInstant()),
                            hash(token),
                            Timestamp.from(Instant.now(inventoryClock)),
                            Timestamp.from(Instant.now(inventoryClock).minusSeconds(15 * 3600)))
                    .stream()
                    .findFirst()
                    .orElse(null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffSessionService.class, "verify(String)");
        }
    }

    /**
     * Revokes the operation.
     *
     * @param token the token
     */
    @Transactional
    public void revoke(String token) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffSessionService.class, "revoke(String)");
        try {
            if (token != null && token.matches("[A-Za-z0-9_-]{43}"))
                jdbc.update(
                        "UPDATE staff_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash"
                                + " = ? AND revoked_at IS NULL",
                        hash(token));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffSessionService.class, "revoke(String)");
        }
    }

    /**
     * Requires csrf.
     *
     * @param session the session
     * @param supplied the supplied
     */
    public void requireCsrf(Verified session, String supplied) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffSessionService.class, "requireCsrf(Verified,String)");
        try {
            if (session == null
                    || supplied == null
                    || !supplied.matches("[A-Za-z0-9_-]{43}")
                    || !MessageDigest.isEqual(
                            session.csrfHash().getBytes(StandardCharsets.US_ASCII),
                            hash(supplied).getBytes(StandardCharsets.US_ASCII)))
                throw new org.springframework.security.access.AccessDeniedException(
                        "Staff CSRF token is invalid.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffSessionService.class,
                    "requireCsrf(Verified,String)");
        }
    }

    /**
     * Csrfs the operation.
     *
     * @param token the token
     * @return the csrf result
     */
    public String csrf(String token) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffSessionService.class, "csrf(String)");
        try {
            return verify(token) == null ? null : derivedCsrf(token);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffSessionService.class, "csrf(String)");
        }
    }

    /**
     * Deriveds csrf.
     *
     * @param token the token
     * @return the derived csrf result
     */
    private String derivedCsrf(String token) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffSessionService.class, "derivedCsrf(String)");
        try {
            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(
                        new javax.crypto.spec.SecretKeySpec(
                                Base64.getDecoder().decode(encryptionKey), "HmacSHA256"));
                return Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                mac.doFinal(
                                        ("staff-csrf:" + token).getBytes(StandardCharsets.UTF_8)));
            } catch (Exception error) {
                throw new IllegalStateException("Staff CSRF setup failed.", error);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffSessionService.class, "derivedCsrf(String)");
        }
    }

    /**
     * Audits sensitive action.
     *
     * @param staffId the staff id
     */
    public void auditSensitiveAction(long staffId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffSessionService.class, "auditSensitiveAction(long)");
        try {
            audit(staffId, "SENSITIVE_ACTION");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffSessionService.class,
                    "auditSensitiveAction(long)");
        }
    }

    /**
     * Audits the operation.
     *
     * @param staffId the staff id
     * @param event the event
     */
    private void audit(Long staffId, String event) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffSessionService.class, "audit(Long,String)");
        try {
            jdbc.update(
                    "INSERT INTO staff_auth_audit(staff_id, event) VALUES (?, ?)", staffId, event);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffSessionService.class, "audit(Long,String)");
        }
    }

    /**
     * Randoms token.
     *
     * @return the random token result
     */
    private String randomToken() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffSessionService.class, "randomToken()");
        try {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffSessionService.class, "randomToken()");
        }
    }

    /**
     * Hashes the operation.
     *
     * @param value the value
     * @return the hash result
     */
    public static String hash(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffSessionService.class, "hash(String)");
        try {
            try {
                return HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(value.getBytes(StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable.", e);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffSessionService.class, "hash(String)");
        }
    }

    /**
     * Cookies the operation.
     *
     * @param request the request
     * @return the cookie result
     */
    public static String cookie(HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffSessionService.class, "cookie(HttpServletRequest)");
        try {
            if (request.getCookies() == null) return null;
            for (Cookie cookie : request.getCookies())
                if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
            return null;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffSessionService.class,
                    "cookie(HttpServletRequest)");
        }
    }
}
