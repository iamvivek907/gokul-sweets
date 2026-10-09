package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.observability.MethodTiming;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Internal OTP policy. Never return a code through a customer HTTP response. */
public final class OtpChallengePolicy {

    /** The expiry value. */
    public static final Duration EXPIRY = Duration.ofMinutes(5);

    /** The resend cooldown value. */
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    /** The max attempts value. */
    public static final int MAX_ATTEMPTS = AppConstant.OTP_CHALLENGE_POLICY_MAX_ATTEMPTS;

    private final SecureRandom random;

    private final byte[] signingKey;

    /**
     * Creates a otp challenge policy instance.
     *
     * @param random the random
     * @param signingKey the signing key
     */
    public OtpChallengePolicy(SecureRandom random, byte[] signingKey) {
        if (signingKey == null || signingKey.length < 32) {
            throw new IllegalArgumentException("OTP signing key must be at least 32 bytes");
        }
        this.random = java.util.Objects.requireNonNull(random);
        this.signingKey = signingKey.clone();
    }

    /**
     * Issues otp challenge policy data and returns the {@code Issued} result.
     *
     * @param normalizedPhone the normalized phone supplied to this method
     * @param now the now supplied to this method
     * @return the {@code Issued} result
     * @throws IllegalArgumentException when the method rejects the request with {@code A normalized
     *     Indian mobile is required}
     */
    public Issued issue(String normalizedPhone, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OtpChallengePolicy.class, "issue(String,Instant)");
        try {
            if (normalizedPhone == null || !normalizedPhone.matches("\\+91[6-9][0-9]{9}")) {
                throw new IllegalArgumentException("A normalized Indian mobile is required");
            }
            var code = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000));
            var nonce = new byte[16];
            random.nextBytes(nonce);
            var challenge =
                    new Challenge(
                            normalizedPhone,
                            HexFormat.of().formatHex(nonce),
                            digest(normalizedPhone, nonce, code),
                            now.plus(EXPIRY),
                            now.plus(RESEND_COOLDOWN),
                            MAX_ATTEMPTS,
                            false);
            return new Issued(challenge, code);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OtpChallengePolicy.class, "issue(String,Instant)");
        }
    }

    /**
     * Verifies otp challenge policy data and returns the {@code Outcome} result.
     *
     * @param challenge the challenge supplied to this method
     * @param phone the phone supplied to this method
     * @param code the code supplied to this method
     * @param now the now supplied to this method
     * @return the {@code Outcome} result
     */
    public Outcome verify(Challenge challenge, String phone, String code, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OtpChallengePolicy.class, "verify(Challenge,String,String,Instant)");
        try {
            if (challenge.consumed()
                    || challenge.attemptsRemaining() <= 0
                    || !now.isBefore(challenge.expiresAt())) {
                return new Outcome(challenge, false);
            }
            // A changed phone is a failed attempt; it must not authorize another identity.
            boolean correct =
                    phone != null
                            && phone.equals(challenge.normalizedPhone())
                            && code != null
                            && code.matches("[0-9]{6}")
                            && MessageDigest.isEqual(
                                    challenge.digest(),
                                    digest(
                                            phone,
                                            HexFormat.of().parseHex(challenge.nonce()),
                                            code));
            return new Outcome(
                    new Challenge(
                            challenge.normalizedPhone(),
                            challenge.nonce(),
                            challenge.digest(),
                            challenge.expiresAt(),
                            challenge.resendAfter(),
                            challenge.attemptsRemaining() - 1,
                            correct),
                    correct);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OtpChallengePolicy.class,
                    "verify(Challenge,String,String,Instant)");
        }
    }

    /**
     * Reports whether resend.
     *
     * @param previous the previous
     * @param now the now
     * @return the can resend result
     */
    public boolean canResend(Challenge previous, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OtpChallengePolicy.class, "canResend(Challenge,Instant)");
        try {
            return previous == null || !now.isBefore(previous.resendAfter());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OtpChallengePolicy.class,
                    "canResend(Challenge,Instant)");
        }
    }

    /**
     * Returns digest information for otp challenge policy.
     *
     * @param phone the phone supplied to this method
     * @param nonce the nonce supplied to this method
     * @param code the code supplied to this method
     * @return the value of {@code mac.doFinal(code.getBytes(StandardCharsets.US_ASCII))}
     * @throws IllegalStateException when the method rejects the request with {@code OTP
     *     verification is unavailable}
     */
    private byte[] digest(String phone, byte[] nonce, String code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OtpChallengePolicy.class, "digest(String,byte[],String)");
        try {
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
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OtpChallengePolicy.class,
                    "digest(String,byte[],String)");
        }
    }

    /**
     * Immutable challenge data contract.
     *
     * @param normalizedPhone the normalized phone
     * @param nonce the nonce
     * @param digest the digest
     * @param expiresAt the expires at
     * @param resendAfter the resend after
     * @param attemptsRemaining the attempts remaining
     * @param consumed the consumed
     */
    public record Challenge(
            String normalizedPhone,
            String nonce,
            byte[] digest,
            Instant expiresAt,
            Instant resendAfter,
            int attemptsRemaining,
            boolean consumed) {

        public Challenge {
            digest = digest.clone();
        }

        /**
         * Returns digest information for challenge.
         *
         * @return the value of {@code digest.clone()}
         */
        @Override
        public byte[] digest() {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(OtpChallengePolicy.Challenge.class, "digest()");
            try {
                return digest.clone();
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos, OtpChallengePolicy.Challenge.class, "digest()");
            }
        }
    }

    /**
     * The code is for a trusted SMS transport only; never persist or log it.
     *
     * @param challenge the challenge
     * @param code the code
     */
    public record Issued(Challenge challenge, String code) {

        /**
         * Tos string.
         *
         * @return the to string result
         */
        @Override
        public String toString() {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(OtpChallengePolicy.Issued.class, "toString()");
            try {
                return "Issued[code=REDACTED]";
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos, OtpChallengePolicy.Issued.class, "toString()");
            }
        }
    }

    /**
     * Immutable outcome data contract.
     *
     * @param challenge the challenge
     * @param verified the verified
     */
    public record Outcome(Challenge challenge, boolean verified) {}
}
