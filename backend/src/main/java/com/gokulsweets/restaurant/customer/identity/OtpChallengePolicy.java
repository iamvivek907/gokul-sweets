package com.gokulsweets.restaurant.customer.identity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/** Internal OTP policy. Never return a code through a customer HTTP response. */
public final class OtpChallengePolicy {
    public static final Duration EXPIRY = Duration.ofMinutes(5);
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    public static final int MAX_ATTEMPTS = 5;

    private final SecureRandom random;
    private final byte[] signingKey;

    public OtpChallengePolicy(SecureRandom random, byte[] signingKey) {
        if (signingKey == null || signingKey.length < 32) {
            throw new IllegalArgumentException("OTP signing key must be at least 32 bytes");
        }
        this.random = java.util.Objects.requireNonNull(random);
        this.signingKey = signingKey.clone();
    }

    public Issued issue(String normalizedPhone, Instant now) {
        if (normalizedPhone == null || !normalizedPhone.matches("\\+91[6-9][0-9]{9}")) {
            throw new IllegalArgumentException("A normalized Indian mobile is required");
        }
        var code = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000));
        var nonce = new byte[16];
        random.nextBytes(nonce);
        var challenge = new Challenge(normalizedPhone, HexFormat.of().formatHex(nonce),
                digest(normalizedPhone, nonce, code), now.plus(EXPIRY),
                now.plus(RESEND_COOLDOWN), MAX_ATTEMPTS, false);
        return new Issued(challenge, code);
    }

    public Outcome verify(Challenge challenge, String phone, String code, Instant now) {
        if (challenge.consumed() || challenge.attemptsRemaining() <= 0 ||
                !now.isBefore(challenge.expiresAt())) {
            return new Outcome(challenge, false);
        }
        // A changed phone is a failed attempt; it must not authorize another identity.
        boolean correct = phone != null && phone.equals(challenge.normalizedPhone()) &&
                code != null && code.matches("[0-9]{6}") &&
                MessageDigest.isEqual(challenge.digest(), digest(phone,
                        HexFormat.of().parseHex(challenge.nonce()), code));
        return new Outcome(new Challenge(challenge.normalizedPhone(), challenge.nonce(),
                challenge.digest(), challenge.expiresAt(), challenge.resendAfter(),
                challenge.attemptsRemaining() - 1, correct), correct);
    }

    public boolean canResend(Challenge previous, Instant now) {
        return previous == null || !now.isBefore(previous.resendAfter());
    }

    private byte[] digest(String phone, byte[] nonce, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            mac.update(phone.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update(nonce);
            mac.update((byte) 0);
            return mac.doFinal(code.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException("OTP verification is unavailable", e);
        }
    }

    public record Challenge(String normalizedPhone, String nonce, byte[] digest, Instant expiresAt,
                            Instant resendAfter, int attemptsRemaining, boolean consumed) {
        public Challenge {
            digest = digest.clone();
        }
        @Override public byte[] digest() { return digest.clone(); }
    }

    /** The code is for a trusted SMS transport only; never persist or log it. */
    public record Issued(Challenge challenge, String code) {
        @Override public String toString() { return "Issued[code=REDACTED]"; }
    }
    public record Outcome(Challenge challenge, boolean verified) { }
}
