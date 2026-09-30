package com.gokulsweets.restaurant.customer.notification;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class WebPushTransportTest {
    @Test
    void preparesEncryptedVapidRequestWithoutNetworkOrPlaintextDetails() throws Exception {
        var properties = new WebPushProperties();
        new WebPushTransport(properties); // Registers the crypto provider.
        var generator = java.security.KeyPairGenerator.getInstance("EC", "BC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var sender = generator.generateKeyPair();
        var recipient = generator.generateKeyPair();
        var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
        properties.setPublicKey(encoder.encodeToString(((org.bouncycastle.jce.interfaces.ECPublicKey) sender.getPublic()).getQ().getEncoded(false)));
        byte[] scalar = ((org.bouncycastle.jce.interfaces.ECPrivateKey) sender.getPrivate()).getD().toByteArray();
        byte[] privateKey = new byte[32];
        System.arraycopy(scalar, Math.max(0, scalar.length - 32), privateKey, Math.max(0, 32 - scalar.length), Math.min(32, scalar.length));
        properties.setPrivateKey(encoder.encodeToString(privateKey));
        properties.setSubject("mailto:push-test@example.invalid");
        var transport = new WebPushTransport(properties);
        assertThat(transport.configured()).isTrue();
        var request = transport.prepare("https://fcm.googleapis.com/fcm/send/test-token",
                encoder.encodeToString(((org.bouncycastle.jce.interfaces.ECPublicKey) recipient.getPublic()).getQ().getEncoded(false)),
                encoder.encodeToString(new byte[16]), 42);
        assertThat(request.getHeaders()).containsEntry("Content-Encoding", "aes128gcm");
        assertThat(request.getHeaders().get("Authorization")).startsWith("vapid ");
        assertThat(request.getBody()).isNotEmpty();
        assertThat(new String(request.getBody(), java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("account update", "eventId", "profile");
    }

    @Test
    void preventsUntrustedEndpointsRedirectTargetsAndInvalidBrowserKeys() {
        assertThat(WebPushTransport.validEndpoint("https://fcm.googleapis.com/fcm/send/browser-token")).isTrue();
        for (String endpoint : new String[]{"http://fcm.googleapis.com/fcm/send/token", "https://127.0.0.1/a",
                "https://fcm.googleapis.com.attacker.example/a", "https://fcm.googleapis.com:444/a",
                "https://attacker@fcm.googleapis.com/a", "https://web.push.apple.com/a#fragment", "https://web.push.apple.com/a?redirect=http://localhost"})
            assertThat(WebPushTransport.validEndpoint(endpoint)).isFalse();
        assertThat(WebPushTransport.validRecipientKeys("bad", "bad")).isFalse();
        assertThat(new WebPushTransport(new WebPushProperties()).configured()).isFalse();
    }

    @Test
    void quietHoursAreIndiaTimeWithInclusiveStartExclusiveEndAndMidnightWrap() {
        assertThat(CustomerAlertPreferences.quiet(true, 1320, 480, Instant.parse("2026-09-30T16:29:59Z"))).isFalse();
        assertThat(CustomerAlertPreferences.quiet(true, 1320, 480, Instant.parse("2026-09-30T16:30:00Z"))).isTrue();
        assertThat(CustomerAlertPreferences.quiet(true, 1320, 480, Instant.parse("2026-09-30T18:30:00Z"))).isTrue();
        assertThat(CustomerAlertPreferences.quiet(true, 1320, 480, Instant.parse("2026-10-01T02:29:59Z"))).isTrue();
        assertThat(CustomerAlertPreferences.quiet(true, 1320, 480, Instant.parse("2026-10-01T02:30:00Z"))).isFalse();
        assertThat(CustomerAlertPreferences.quiet(false, 1320, 480, Instant.parse("2026-09-30T18:30:00Z"))).isFalse();
        assertThat(CustomerAlertPreferences.quiet(true, 600, 660, Instant.parse("2026-09-30T04:45:00Z"))).isTrue();
    }
}
