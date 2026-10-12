package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.StaffUser;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 TOTP with encrypted secrets, counter replay protection and one-use recovery codes. */
@Service
@RequiredArgsConstructor
public class StaffMfaService {

    private final EnhancementProperties flags;

    private final JdbcTemplate jdbc;

    private final Clock inventoryClock;

    private final SecureRandom random = new SecureRandom();

    @Value("${staff.mfa.encryption-key:}")
    private String encryptionKey;

    /** Validates key. */
    @PostConstruct
    public void validateKey() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffMfaService.class, "validateKey()");
        try {
            if (flags.isSecureStaffSessions()) key();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, StaffMfaService.class, "validateKey()");
        }
    }

    /**
     * Returns whether MFA is mandatory for an owner or an account with staff-management,
     * payroll-management or refund-creation permission.
     *
     * @param staff the staff account with its loaded role and permissions
     * @return true when MFA is mandatory; false otherwise
     */
    public boolean required(StaffUser staff) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffMfaService.class, "required(StaffUser)");
        try {
            return "OWNER_ADMIN".equals(staff.getRole().getName())
                    || staff.getRole().getPermissions().stream()
                            .anyMatch(
                                    permission ->
                                            java.util.Set.of(
                                                            "STAFF_MANAGE",
                                                            "PAYROLL_MANAGE",
                                                            "REFUND_CREATE")
                                                    .contains(permission.getName().name()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffMfaService.class, "required(StaffUser)");
        }
    }

    /**
     * Returns whether the staff account has a persisted MFA secret; an unfinished enrollment
     * challenge does not count as enrollment.
     *
     * <p>Reads {@code staff_mfa}.
     *
     * @param staffId the staff id supplied to this method
     * @return true when a persisted MFA secret exists for the account
     */
    public boolean enrolled(long staffId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffMfaService.class, "enrolled(long)");
        try {
            return Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM staff_mfa WHERE staff_id = ?)",
                            Boolean.class,
                            staffId));
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, StaffMfaService.class, "enrolled(long)");
        }
    }

    /**
     * Immutable setup data contract.
     *
     * @param secret the secret
     * @param uri the uri
     */
    public record Setup(String secret, String uri) {}

    /**
     * Locks an unexpired enrollment challenge and creates or reuses its encrypted secret, returning
     * the authenticator secret and enrollment URI.
     *
     * <p>Reads {@code staff_mfa_enrollments}.
     *
     * <p>Writes {@code staff_mfa_enrollments}.
     *
     * @param staffId the staff id supplied to this method
     * @param enrollmentHash the enrollment hash supplied to this method
     * @return the {@code Setup} result
     * @throws IllegalStateException when the method rejects the request with {@code Enrollment
     *     expired or already complete.}
     */
    @Transactional
    public Setup setup(long staffId, String enrollmentHash) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffMfaService.class, "setup(long,String)");
        try {
            var allowed =
                    jdbc.query(
                            """
                            SELECT staff_id, secret_ciphertext FROM staff_mfa_enrollments
                            WHERE token_hash = ? AND staff_id = ? AND expires_at > ? FOR UPDATE
                            """,
                            (rs, index) -> rs.getString(2),
                            enrollmentHash,
                            staffId,
                            Timestamp.from(Instant.now(inventoryClock)));
            if (allowed.isEmpty() || enrolled(staffId))
                throw new IllegalStateException("Enrollment expired or already complete.");
            String encoded = allowed.getFirst();
            if (encoded == null) {
                encoded = encrypt(randomBytes(20));
                jdbc.update(
                        "UPDATE staff_mfa_enrollments SET secret_ciphertext = ? WHERE token_hash ="
                                + " ?",
                        encoded,
                        enrollmentHash);
            }
            String secret = base32(decrypt(encoded));
            return new Setup(
                    secret,
                    "otpauth://totp/Gokul%20Sweets:staff?secret="
                            + secret
                            + "&issuer=Gokul%20Sweets&algorithm=SHA1&digits=6&period=30");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffMfaService.class, "setup(long,String)");
        }
    }

    /**
     * Completes an unexpired MFA enrollment after validating an authenticator code, consumes the
     * challenge and returns eight newly issued recovery codes.
     *
     * <p>Reads {@code staff_mfa_enrollments}.
     *
     * <p>Writes {@code staff_mfa}, {@code staff_mfa_enrollments}, {@code staff_mfa_recovery}.
     *
     * <p>Delegates to {@code StaffSessionService.hash(...)}.
     *
     * @param staffId the staff id supplied to this method
     * @param enrollmentHash the enrollment hash supplied to this method
     * @param code the code supplied to this method
     * @return the value of {@code recovery}
     * @throws IllegalArgumentException when the method rejects the request with {@code Invalid
     *     authenticator code.}
     * @throws IllegalStateException when the method rejects the request with {@code Enrollment
     *     expired or already complete.}
     */
    @Transactional
    public List<String> confirm(long staffId, String enrollmentHash, String code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffMfaService.class, "confirm(long,String,String)");
        try {
            var secrets =
                    jdbc.query(
                            """
                            SELECT secret_ciphertext FROM staff_mfa_enrollments
                            WHERE token_hash = ? AND staff_id = ? AND expires_at > ? FOR UPDATE
                            """,
                            (rs, index) -> rs.getString(1),
                            enrollmentHash,
                            staffId,
                            Timestamp.from(Instant.now(inventoryClock)));
            if (secrets.isEmpty() || secrets.getFirst() == null || enrolled(staffId))
                throw new IllegalStateException("Enrollment expired or already complete.");
            if (counter(decrypt(secrets.getFirst()), code) < 0)
                throw new IllegalArgumentException("Invalid authenticator code.");
            jdbc.update(
                    "INSERT INTO staff_mfa(staff_id, secret_ciphertext, last_counter) VALUES (?, ?,"
                            + " ?)",
                    staffId,
                    secrets.getFirst(),
                    counter(decrypt(secrets.getFirst()), code));
            List<String> recovery = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String value =
                        Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(12));
                jdbc.update(
                        "INSERT INTO staff_mfa_recovery(staff_id, code_hash) VALUES (?, ?)",
                        staffId,
                        StaffSessionService.hash(value));
                recovery.add(value);
            }
            jdbc.update("DELETE FROM staff_mfa_enrollments WHERE token_hash = ?", enrollmentHash);
            return recovery;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffMfaService.class,
                    "confirm(long,String,String)");
        }
    }

    /**
     * Accepts a fresh authenticator counter or consumes one unused recovery code; returns false for
     * malformed, missing or replayed credentials.
     *
     * <p>Reads {@code staff_mfa}.
     *
     * <p>Writes {@code staff_mfa}, {@code staff_mfa_recovery}.
     *
     * <p>Delegates to {@code StaffSessionService.hash(...)}.
     *
     * @param staffId the staff id supplied to this method
     * @param code the code supplied to this method
     * @return the {@code boolean} result
     */
    @Transactional
    public boolean verify(long staffId, String code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffMfaService.class, "verify(long,String)");
        try {
            if (code == null || code.length() > 64) return false;
            var secrets =
                    jdbc.query(
                            "SELECT secret_ciphertext, last_counter FROM staff_mfa WHERE staff_id ="
                                    + " ? FOR UPDATE",
                            (rs, index) -> new Object[] {rs.getString(1), rs.getLong(2)},
                            staffId);
            if (secrets.isEmpty()) return false;
            long counter = counter(decrypt((String) secrets.getFirst()[0]), code);
            if (counter >= 0 && counter > (long) secrets.getFirst()[1]) {
                jdbc.update(
                        "UPDATE staff_mfa SET last_counter = ? WHERE staff_id = ?",
                        counter,
                        staffId);
                return true;
            }
            return jdbc.update(
                            "DELETE FROM staff_mfa_recovery WHERE staff_id = ? AND code_hash = ?"
                                    + " AND used_at IS NULL",
                            staffId,
                            StaffSessionService.hash(code))
                    == 1;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffMfaService.class, "verify(long,String)");
        }
    }

    /**
     * Decodes the configured Base64 key and requires exactly 32 bytes for AES encryption.
     *
     * @return the {@code SecretKeySpec} result
     * @throws IllegalStateException when the method rejects the request with {@code
     *     STAFF_MFA_ENCRYPTION_KEY must be a base64 encoded 32-byte key.}
     */
    private SecretKeySpec key() {
        final long __gokulMethodStartedNanos = MethodTiming.start(StaffMfaService.class, "key()");
        try {
            try {
                byte[] bytes = Base64.getDecoder().decode(encryptionKey);
                if (bytes.length != 32) throw new IllegalArgumentException();
                return new SecretKeySpec(bytes, "AES");
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                        "STAFF_MFA_ENCRYPTION_KEY must be a base64 encoded 32-byte key.");
            }
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, StaffMfaService.class, "key()");
        }
    }

    /**
     * Encrypts an MFA secret with AES-GCM and a random 12-byte nonce, returning the Base64-encoded
     * nonce and ciphertext.
     *
     * @param secret the secret supplied to this method
     * @return the value of {@code
     *     Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length +
     *     encrypted.length).put(nonce).put(encrypted).array())}
     * @throws IllegalStateException when the method rejects the request with {@code MFA encryption
     *     failed.}
     */
    private String encrypt(byte[] secret) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffMfaService.class, "encrypt(byte[])");
        try {
            try {
                byte[] nonce = randomBytes(12);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
                byte[] encrypted = cipher.doFinal(secret);
                return Base64.getEncoder()
                        .encodeToString(
                                ByteBuffer.allocate(nonce.length + encrypted.length)
                                        .put(nonce)
                                        .put(encrypted)
                                        .array());
            } catch (Exception error) {
                throw new IllegalStateException("MFA encryption failed.", error);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffMfaService.class, "encrypt(byte[])");
        }
    }

    /**
     * Decodes the stored nonce and ciphertext, authenticates them with AES-GCM and returns the
     * original MFA secret bytes.
     *
     * @param encoded the encoded supplied to this method
     * @return the value of {@code cipher.doFinal(encrypted)}
     * @throws IllegalStateException when the method rejects the request with {@code MFA secret
     *     cannot be decrypted.}
     */
    private byte[] decrypt(String encoded) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffMfaService.class, "decrypt(String)");
        try {
            try {
                byte[] bytes = Base64.getDecoder().decode(encoded);
                ByteBuffer data = ByteBuffer.wrap(bytes);
                byte[] nonce = new byte[12];
                data.get(nonce);
                byte[] encrypted = new byte[data.remaining()];
                data.get(encrypted);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
                return cipher.doFinal(encrypted);
            } catch (Exception error) {
                throw new IllegalStateException("MFA secret cannot be decrypted.", error);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffMfaService.class, "decrypt(String)");
        }
    }

    /**
     * Randoms bytes.
     *
     * @param size the size
     * @return the random bytes result
     */
    private byte[] randomBytes(int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffMfaService.class, "randomBytes(int)");
        try {
            byte[] bytes = new byte[size];
            random.nextBytes(bytes);
            return bytes;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffMfaService.class, "randomBytes(int)");
        }
    }

    /**
     * Encodes secret bytes as unpadded Base32 for authenticator enrollment.
     *
     * @param data the data supplied to this method
     * @return the value of {@code value.toString()}
     */
    private static String base32(byte[] data) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffMfaService.class, "base32(byte[])");
        try {
            String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
            StringBuilder value = new StringBuilder();
            int bits = 0, buffer = 0;
            for (byte b : data) {
                buffer = (buffer << 8) | (b & 255);
                bits += 8;
                while (bits >= 5) {
                    value.append(alphabet.charAt((buffer >> (bits -= 5)) & 31));
                }
            }
            if (bits > 0) value.append(alphabet.charAt((buffer << (5 - bits)) & 31));
            return value.toString();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, StaffMfaService.class, "base32(byte[])");
        }
    }

    /**
     * Returns the matching 30-second TOTP counter within the previous, current or next window, or
     * minus one when no six-digit code matches.
     *
     * @param secret the secret supplied to this method
     * @param code the code supplied to this method
     * @return the {@code long} result
     * @throws IllegalStateException when the method rejects the request with {@code MFA check
     *     failed.}
     */
    private long counter(byte[] secret, String code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffMfaService.class, "counter(byte[],String)");
        try {
            if (code == null || !code.matches("[0-9]{6}")) return -1;
            long now = Instant.now(inventoryClock).getEpochSecond() / 30;
            for (long candidate = now - 1; candidate <= now + 1; candidate++) {
                try {
                    Mac mac = Mac.getInstance("HmacSHA1");
                    mac.init(new SecretKeySpec(secret, "HmacSHA1"));
                    byte[] digest = mac.doFinal(ByteBuffer.allocate(8).putLong(candidate).array());
                    int offset = digest[digest.length - 1] & 15;
                    int binary =
                            ((digest[offset] & 127) << 24)
                                    | ((digest[offset + 1] & 255) << 16)
                                    | ((digest[offset + 2] & 255) << 8)
                                    | (digest[offset + 3] & 255);
                    String expected = String.format("%06d", binary % 1_000_000);
                    if (java.security.MessageDigest.isEqual(
                            expected.getBytes(StandardCharsets.US_ASCII),
                            code.getBytes(StandardCharsets.US_ASCII))) return candidate;
                } catch (Exception error) {
                    throw new IllegalStateException("MFA check failed.", error);
                }
            }
            return -1;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffMfaService.class, "counter(byte[],String)");
        }
    }
}
