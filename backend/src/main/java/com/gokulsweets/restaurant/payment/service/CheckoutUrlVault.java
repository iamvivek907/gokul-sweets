package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Checkout links contain bearer tokens. Never store/log them as plaintext. */
@Service
public class CheckoutUrlVault {

    private final String configuredKey;

    /**
     * Creates a checkout url vault instance.
     *
     * @param configuredKey the configured key
     */
    public CheckoutUrlVault(@Value("${payment.checkout-encryption-key:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    /**
     * Returns key information for checkout url vault.
     *
     * @return the {@code SecretKeySpec} result
     * @throws IllegalStateException when the method rejects the request with {@code Configure a
     *     base64 32-byte payment checkout encryption key.}
     */
    private SecretKeySpec key() {
        final long __gokulMethodStartedNanos = MethodTiming.start(CheckoutUrlVault.class, "key()");
        try {
            try {
                byte[] bytes = Base64.getDecoder().decode(configuredKey);
                if (bytes.length != 32) throw new IllegalArgumentException();
                return new SecretKeySpec(bytes, "AES");
            } catch (RuntimeException error) {
                throw new IllegalStateException(
                        "Configure a base64 32-byte payment checkout encryption key.");
            }
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CheckoutUrlVault.class, "key()");
        }
    }

    /**
     * Encrypts a valid HTTPS checkout URL as a versioned AES-GCM payload, returning null for a null
     * URL. URL validation and encryption failures are wrapped in IllegalStateException.
     *
     * @param url the url supplied to this method
     * @return the {@code String} result
     * @throws IllegalStateException when the method rejects the request with {@code Could not
     *     securely persist payment checkout.}
     */
    public String seal(String url) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CheckoutUrlVault.class, "seal(String)");
        try {
            if (url == null) return null;
            try {
                var uri = java.net.URI.create(url);
                if (!"https".equalsIgnoreCase(uri.getScheme())
                        || uri.getHost() == null
                        || uri.getUserInfo() != null
                        || url.length() > 2048)
                    throw new IllegalArgumentException("Invalid checkout URL.");
                byte[] nonce = new byte[12];
                new SecureRandom().nextBytes(nonce);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
                cipher.updateAAD("gokul-payment-checkout-v1".getBytes(StandardCharsets.UTF_8));
                byte[] encrypted = cipher.doFinal(url.getBytes(StandardCharsets.UTF_8));
                return "v1:"
                        + Base64.getEncoder()
                                .encodeToString(
                                        ByteBuffer.allocate(12 + encrypted.length)
                                                .put(nonce)
                                                .put(encrypted)
                                                .array());
            } catch (Exception error) {
                throw new IllegalStateException(
                        "Could not securely persist payment checkout.", error);
            }
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CheckoutUrlVault.class, "seal(String)");
        }
    }

    /**
     * Authenticates and decrypts a versioned AES-GCM checkout payload, returning null for a null
     * payload. Malformed or unauthenticated payloads are wrapped in IllegalStateException.
     *
     * @param encoded the encoded supplied to this method
     * @return the {@code String} result
     * @throws IllegalStateException when the method rejects the request with {@code Payment
     *     checkout could not be recovered. Check provider status before continuing.}
     */
    public String open(String encoded) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CheckoutUrlVault.class, "open(String)");
        try {
            if (encoded == null) return null;
            try {
                if (!encoded.startsWith("v1:")) throw new IllegalArgumentException();
                ByteBuffer data = ByteBuffer.wrap(Base64.getDecoder().decode(encoded.substring(3)));
                byte[] nonce = new byte[12];
                data.get(nonce);
                byte[] encrypted = new byte[data.remaining()];
                data.get(encrypted);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
                cipher.updateAAD("gokul-payment-checkout-v1".getBytes(StandardCharsets.UTF_8));
                return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            } catch (Exception error) {
                throw new IllegalStateException(
                        "Payment checkout could not be recovered. Check provider status before"
                                + " continuing.",
                        error);
            }
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CheckoutUrlVault.class, "open(String)");
        }
    }
}
