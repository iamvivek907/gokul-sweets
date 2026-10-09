package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Coordinates owner account operations. */
@Service
public class OwnerAccountService {

    private final JdbcTemplate jdbc;

    private final Environment environment;

    private final EnhancementProperties flags;

    private final PasswordEncoder passwords;

    private final StaffMfaService mfa;

    private final TransactionTemplate tx;

    private final SecureRandom random = new SecureRandom();

    /**
     * Creates a owner account service instance.
     *
     * @param jdbc the jdbc
     * @param environment the environment
     * @param flags the flags
     * @param passwords the passwords
     * @param mfa the mfa
     * @param transactions the transactions
     */
    public OwnerAccountService(
            JdbcTemplate jdbc,
            Environment environment,
            EnhancementProperties flags,
            PasswordEncoder passwords,
            StaffMfaService mfa,
            PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.environment = environment;
        this.flags = flags;
        this.passwords = passwords;
        this.mfa = mfa;
        tx = new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(30);
    }

    /**
     * Immutable status data contract.
     *
     * @param available the available
     */
    public record Status(boolean available) {}

    /**
     * Immutable created data contract.
     *
     * @param username the username
     * @param recoveryKey the recovery key
     */
    public record Created(String username, String recoveryKey) {}

    /**
     * Immutable account data contract.
     *
     * @param username the username
     * @param hasRecoveryKey the has recovery key
     */
    public record Account(String username, boolean hasRecoveryKey) {}

    /**
     * Returns whether secure staff sessions are enabled in a recognized DEV or PROD environment.
     *
     * @return the {@code boolean} result
     */
    private boolean secure() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "secure()");
        try {
            return flags.isSecureStaffSessions()
                    && List.of("DEV", "PROD")
                            .contains(
                                    environment.getProperty(
                                            "gokul.environment-isolation.environment", ""));
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, OwnerAccountService.class, "secure()");
        }
    }

    /**
     * Returns whether secure owner setup is enabled and its configured setup-key hash is a
     * 64-character lowercase hexadecimal value.
     *
     * @return the {@code boolean} result
     */
    private boolean enabled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "enabled()");
        try {
            return secure()
                    && environment.getProperty("staff.owner-setup.enabled", Boolean.class, false)
                    && environment
                            .getProperty("staff.owner-setup.key-hash", "")
                            .matches("[0-9a-f]{64}");
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, OwnerAccountService.class, "enabled()");
        }
    }

    /** Requires secure. */
    private void requireSecure() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "requireSecure()");
        try {
            if (!secure()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OwnerAccountService.class, "requireSecure()");
        }
    }

    /**
     * Returns whether one-time owner setup is enabled and still open, checking closure under the
     * singleton setup-row lock.
     *
     * @return the {@code Status} result
     */
    public Status status() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "status()");
        try {
            if (!enabled()) return new Status(false);
            return tx.execute(
                    status -> {
                        deadlines();
                        return new Status(!closed());
                    });
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, OwnerAccountService.class, "status()");
        }
    }

    /**
     * Locks the setup latch and permanently closes it if setup already completed or any owner
     * account already exists.
     *
     * <p>Reads {@code roles}, {@code staff_owner_setup}, {@code staff_users}.
     *
     * <p>Writes {@code staff_owner_setup}.
     *
     * @return the {@code boolean} result
     */
    private boolean closed() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "closed()");
        try {
            Boolean done =
                    jdbc.queryForObject(
                            "SELECT completed_at IS NOT NULL FROM staff_owner_setup WHERE"
                                    + " singleton=TRUE FOR UPDATE",
                            Boolean.class);
            if (Boolean.TRUE.equals(done)) return true;
            if (Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM staff_users u JOIN roles r ON"
                                    + " r.id=u.role_id WHERE r.name='OWNER_ADMIN')",
                            Boolean.class))) {
                jdbc.update(
                        "UPDATE staff_owner_setup SET completed_at=CURRENT_TIMESTAMP WHERE"
                                + " singleton=TRUE");
                return true;
            }
            return false;
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, OwnerAccountService.class, "closed()");
        }
    }

    /**
     * Independently committed attempts survive invalid credentials and transaction rollback.
     *
     * @param action the action
     * @param source the source
     */
    public void limit(String action, String source) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "limit(String,String)");
        try {
            requireSecure();
            String bucket = "owner:" + StaffSessionService.hash(action + ":" + source);
            Integer attempts =
                    tx.execute(
                            status -> {
                                deadlines();
                                // Only this feature’s stale buckets; never alter existing login
                                // throttles.
                                jdbc.update(
                                        "DELETE FROM staff_login_limits WHERE username IN (SELECT"
                                            + " username FROM staff_login_limits WHERE username"
                                            + " LIKE 'owner:%' AND"
                                            + " updated_at<CURRENT_TIMESTAMP-INTERVAL '1 day' LIMIT"
                                            + " 200 FOR UPDATE SKIP LOCKED)");
                                return jdbc.queryForObject(
                                        """
INSERT INTO staff_login_limits(username,failures,updated_at) VALUES (?,1,CURRENT_TIMESTAMP)
ON CONFLICT(username) DO UPDATE SET failures=CASE WHEN staff_login_limits.updated_at<CURRENT_TIMESTAMP-INTERVAL '10 minutes'
  THEN 1 ELSE staff_login_limits.failures+1 END,updated_at=CURRENT_TIMESTAMP RETURNING failures
""",
                                        Integer.class,
                                        bucket);
                            });
            if (attempts != null && attempts > 10)
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too many account attempts. Wait ten minutes before trying again.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OwnerAccountService.class, "limit(String,String)");
        }
    }

    /**
     * Verifies the operator-held setup key and creates the first owner atomically, permanently
     * closing setup and returning a new recovery key.
     *
     * <p>Reads {@code roles}.
     *
     * <p>Writes {@code staff_owner_setup}, {@code staff_users}.
     *
     * <p>Delegates to {@code StaffSessionService.hash(...)}.
     *
     * @param key the key supplied to this method
     * @param username the username supplied to this method
     * @param password the password supplied to this method
     * @param fullName the full name supplied to this method
     * @return the {@code Created} result
     * @throws ResponseStatusException when the method rejects the request with {@code Enter an
     *     owner name of up to 150 characters.}; {@code Owner setup is already completed. Use admin
     *     login.}
     */
    public Created setup(String key, String username, String password, String fullName) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "setup(String,String,String,String)");
        try {
            if (!enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if (key == null
                    || !key.matches("[A-Za-z0-9_-]{43}")
                    || !MessageDigest.isEqual(
                            StaffSessionService.hash(key).getBytes(StandardCharsets.US_ASCII),
                            environment
                                    .getProperty("staff.owner-setup.key-hash", "")
                                    .getBytes(StandardCharsets.US_ASCII))) denied();
            String name = username(username);
            password(password);
            if (fullName == null || fullName.trim().isEmpty() || fullName.trim().length() > 150)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Enter an owner name of up to 150 characters.");
            Created result =
                    write(
                            () -> {
                                if (closed()) return null;
                                unique(name, 0);
                                Long id =
                                        jdbc.queryForObject(
                                                """
INSERT INTO staff_users(username,password_hash,full_name,role_id,active,created_at,updated_at)
SELECT ?,?,?,id,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM roles WHERE name='OWNER_ADMIN' RETURNING id
""",
                                                Long.class,
                                                name,
                                                passwords.encode(password),
                                                fullName.trim());
                                String recovery = rotate(id);
                                jdbc.update(
                                        "UPDATE staff_owner_setup SET"
                                                + " completed_at=CURRENT_TIMESTAMP,first_owner_id=?"
                                                + " WHERE singleton=TRUE",
                                        id);
                                audit(id, "FIRST_OWNER_CREATED");
                                return new Created(name, recovery);
                            });
            if (result == null)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Owner setup is already completed. Use admin login.");
            return result;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OwnerAccountService.class,
                    "setup(String,String,String,String)");
        }
    }

    /**
     * Consumes a matching owner recovery key, changes username and password, revokes sessions and
     * enrollment challenges, and returns a replacement key without changing MFA or roles.
     *
     * <p>Reads {@code staff_owner_recovery}.
     *
     * <p>Writes {@code staff_users}.
     *
     * <p>Delegates to {@code StaffSessionService.hash(...)}.
     *
     * @param key the key supplied to this method
     * @param username the username supplied to this method
     * @param password the password supplied to this method
     * @return the {@code Created} result
     */
    public Created recover(String key, String username, String password) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "recover(String,String,String)");
        try {
            requireSecure();
            String name = username(username);
            password(password);
            if (key == null || !key.matches("[A-Za-z0-9_-]{43}")) denied();
            String hash = StaffSessionService.hash(key);
            var owners =
                    jdbc.queryForList(
                            "SELECT staff_id FROM staff_owner_recovery WHERE key_hash=?",
                            Long.class,
                            hash);
            if (owners.isEmpty()) {
                denied();
                return null;
            }
            long id = owners.getFirst();
            return write(
                    () -> {
                        owner(id);
                        if (jdbc.queryForList(
                                        "SELECT staff_id FROM staff_owner_recovery WHERE staff_id=?"
                                                + " AND key_hash=? FOR UPDATE",
                                        Long.class,
                                        id,
                                        hash)
                                .isEmpty()) denied();
                        unique(name, id);
                        jdbc.update(
                                "UPDATE staff_users SET"
                                        + " username=?,password_hash=?,updated_at=CURRENT_TIMESTAMP"
                                        + " WHERE id=?",
                                name,
                                passwords.encode(password),
                                id);
                        invalidate(id);
                        String replacement = rotate(id);
                        audit(id, "OWNER_CREDENTIALS_RECOVERED");
                        return new Created(name, replacement);
                    });
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OwnerAccountService.class,
                    "recover(String,String,String)");
        }
    }

    /**
     * Returns the active owner's username and whether a recovery-key hash is stored, under the
     * owner account lock.
     *
     * <p>Reads {@code staff_owner_recovery}.
     *
     * @param id the id supplied to this method
     * @return the {@code Account} result
     */
    public Account account(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "account(long)");
        try {
            return write(
                    () -> {
                        var user = owner(id);
                        return new Account(
                                (String) user.get("username"),
                                Boolean.TRUE.equals(
                                        jdbc.queryForObject(
                                                "SELECT EXISTS(SELECT 1 FROM staff_owner_recovery"
                                                        + " WHERE staff_id=?)",
                                                Boolean.class,
                                                id)));
                    });
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OwnerAccountService.class, "account(long)");
        }
    }

    /**
     * Recovery key.
     *
     * @param id the id
     * @param password the password
     * @param code the code
     * @return the recovery key result
     */
    public Created recoveryKey(long id, String password, String code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "recoveryKey(long,String,String)");
        try {
            return write(
                    () -> {
                        reauthenticate(id, password, code);
                        String key = rotate(id);
                        audit(id, "OWNER_RECOVERY_KEY_ROTATED");
                        return new Created((String) owner(id).get("username"), key);
                    });
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OwnerAccountService.class,
                    "recoveryKey(long,String,String)");
        }
    }

    /**
     * Requires the owner's password and MFA, changes to an unused normalized username and revokes
     * existing sessions and enrollment challenges.
     *
     * <p>Writes {@code staff_users}.
     *
     * @param id the id supplied to this method
     * @param name the name supplied to this method
     * @param password the password supplied to this method
     * @param code the code supplied to this method
     */
    public void rename(long id, String name, String password, String code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "rename(long,String,String,String)");
        try {
            String normalized = username(name);
            write(
                    () -> {
                        reauthenticate(id, password, code);
                        unique(normalized, id);
                        jdbc.update(
                                "UPDATE staff_users SET username=?,updated_at=CURRENT_TIMESTAMP"
                                        + " WHERE id=?",
                                normalized,
                                id);
                        invalidate(id);
                        audit(id, "OWNER_USERNAME_CHANGED");
                        return true;
                    });
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OwnerAccountService.class,
                    "rename(long,String,String,String)");
        }
    }

    /**
     * Requires secure account handling and locks an active owner row, rejecting inactive, missing
     * or non-owner accounts.
     *
     * <p>Reads {@code roles}, {@code staff_users}.
     *
     * <p>Writes {@code OF}.
     *
     * @param id the id supplied to this method
     * @return the {@code Map<String, Object>} result
     */
    private Map<String, Object> owner(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "owner(long)");
        try {
            requireSecure();
            var rows =
                    jdbc.queryForList(
                            "SELECT u.username,u.password_hash FROM staff_users u JOIN roles r ON"
                                    + " r.id=u.role_id WHERE u.id=? AND u.active AND"
                                    + " r.name='OWNER_ADMIN' FOR UPDATE OF u",
                            id);
            if (rows.isEmpty()) {
                denied();
                return Map.of();
            }
            return rows.getFirst();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OwnerAccountService.class, "owner(long)");
        }
    }

    /**
     * Requires an active owner, a matching password and a valid fresh MFA or recovery code before a
     * sensitive account change.
     *
     * @param id the id supplied to this method
     * @param password the password supplied to this method
     * @param code the code supplied to this method
     */
    private void reauthenticate(long id, String password, String code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "reauthenticate(long,String,String)");
        try {
            var user = owner(id);
            if (password == null
                    || password.length() > 128
                    || !passwords.matches(password, (String) user.get("password_hash"))
                    || !mfa.verify(id, code)) denied();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OwnerAccountService.class,
                    "reauthenticate(long,String,String)");
        }
    }

    /**
     * Replaces the owner's stored recovery-key hash and returns a fresh random key; only the hash
     * is persisted.
     *
     * <p>Writes {@code staff_owner_recovery}.
     *
     * <p>Delegates to {@code StaffSessionService.hash(...)}.
     *
     * @param id the id supplied to this method
     * @return the value of {@code key}
     */
    private String rotate(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "rotate(long)");
        try {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            jdbc.update(
                    "INSERT INTO staff_owner_recovery(staff_id,key_hash) VALUES (?,?) ON"
                            + " CONFLICT(staff_id) DO UPDATE SET"
                            + " key_hash=EXCLUDED.key_hash,created_at=CURRENT_TIMESTAMP",
                    id,
                    StaffSessionService.hash(key));
            return key;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OwnerAccountService.class, "rotate(long)");
        }
    }

    /**
     * Revokes all active sessions and deletes unfinished MFA enrollment challenges for the staff
     * account.
     *
     * <p>Writes {@code staff_mfa_enrollments}, {@code staff_sessions}.
     *
     * @param id the id supplied to this method
     */
    private void invalidate(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "invalidate(long)");
        try {
            jdbc.update(
                    "UPDATE staff_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE staff_id=? AND"
                            + " revoked_at IS NULL",
                    id);
            jdbc.update("DELETE FROM staff_mfa_enrollments WHERE staff_id=?", id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OwnerAccountService.class, "invalidate(long)");
        }
    }

    /**
     * Persists the account identifier and authentication event name in the staff audit table.
     *
     * <p>Writes {@code staff_auth_audit}.
     *
     * @param id the id supplied to this method
     * @param event the event supplied to this method
     */
    private void audit(long id, String event) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "audit(long,String)");
        try {
            jdbc.update("INSERT INTO staff_auth_audit(staff_id,event) VALUES (?,?)", id, event);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OwnerAccountService.class, "audit(long,String)");
        }
    }

    /**
     * Rejects a username already used by another staff account, comparing normalized usernames
     * case-insensitively.
     *
     * <p>Reads {@code staff_users}.
     *
     * @param name the name supplied to this method
     * @param id the id supplied to this method
     * @throws ResponseStatusException when the method rejects the request with {@code That username
     *     is already in use.}
     */
    private void unique(String name, long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "unique(String,long)");
        try {
            if (Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM staff_users WHERE LOWER(username)=? AND"
                                    + " id<>?)",
                            Boolean.class,
                            name,
                            id)))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "That username is already in use.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OwnerAccountService.class, "unique(String,long)");
        }
    }

    /**
     * Requires a supported username of 2–100 characters, then trims and lowercases it with the root
     * locale.
     *
     * @param name the name supplied to this method
     * @return the value of {@code name.trim().toLowerCase(Locale.ROOT)}
     * @throws ResponseStatusException when the method rejects the request with {@code Use 2–100
     *     letters, numbers, dots, underscores, @ or hyphens for the username.}
     */
    private static String username(String name) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "username(String)");
        try {
            if (name == null || !name.trim().matches("[A-Za-z0-9._@-]{2,100}"))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Use 2–100 letters, numbers, dots, underscores, @ or hyphens for the"
                                + " username.");
            return name.trim().toLowerCase(Locale.ROOT);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OwnerAccountService.class, "username(String)");
        }
    }

    /**
     * Requires at least twelve password characters and at most 72 UTF-8 bytes for the configured
     * password hashing policy.
     *
     * @param password the password supplied to this method
     * @throws ResponseStatusException when the method rejects the request with {@code Use at least
     *     12 characters and a shorter password if it exceeds the supported length.}
     */
    private static void password(String password) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "password(String)");
        try {
            if (password == null
                    || password.length() < 12
                    || password.getBytes(StandardCharsets.UTF_8).length > 72)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Use at least 12 characters and a shorter password if it exceeds the"
                                + " supported length.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OwnerAccountService.class, "password(String)");
        }
    }

    /**
     * Raises a generic forbidden response that does not reveal which account verification check
     * failed.
     *
     * @throws ResponseStatusException when the method rejects the request with {@code Account
     *     verification failed.}
     */
    private static void denied() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "denied()");
        try {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account verification failed.");
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, OwnerAccountService.class, "denied()");
        }
    }

    /**
     * Applies transaction-local ten-second statement and two-second lock timeouts to owner account
     * writes.
     */
    private void deadlines() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountService.class, "deadlines()");
        try {
            jdbc.execute("SET LOCAL statement_timeout='10s'");
            jdbc.execute("SET LOCAL lock_timeout='2s'");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OwnerAccountService.class, "deadlines()");
        }
    }

    /**
     * Runs the supplied account mutation transactionally with local database timeouts and maps
     * integrity conflicts to a refresh-and-retry response.
     *
     * @param <T> the T type
     * @param action the action supplied to this method
     * @return the {@code T} result
     * @throws ResponseStatusException when the method rejects the request with {@code Account
     *     details changed. Refresh and try again.}
     */
    private <T> T write(java.util.function.Supplier<T> action) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OwnerAccountService.class, "write(java.util.function.Supplier<T>)");
        try {
            try {
                return tx.execute(
                        status -> {
                            deadlines();
                            return action.get();
                        });
            } catch (DataIntegrityViolationException duplicate) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Account details changed. Refresh and try again.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OwnerAccountService.class,
                    "write(java.util.function.Supplier<T>)");
        }
    }
}
