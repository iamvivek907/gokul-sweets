package com.gokulsweets.restaurant.customer.notification;

import lombok.RequiredArgsConstructor;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

/** Encrypt/sign with the maintained library; bounded Java HTTP transport never follows redirects. */
@Component
@RequiredArgsConstructor
public class WebPushTransport {
    private final WebPushProperties properties;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private static final Set<String> HOSTS = Set.of("fcm.googleapis.com", "updates.push.services.mozilla.com", "web.push.apple.com");
    static {
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
    }

    public boolean configured() {
        try {
            if (!properties.getSubject().matches("mailto:[^\\s@]+@[^\\s@]+|https://[^\\s]+")) return false;
            if (Base64.getUrlDecoder().decode(properties.getPublicKey()).length != 65
                    || Base64.getUrlDecoder().decode(properties.getPrivateKey()).length != 32) return false;
            new PushService(properties.getPublicKey(), properties.getPrivateKey(), properties.getSubject());
            return true;
        } catch (Exception invalid) {return false;}
    }

    public static boolean validEndpoint(String endpoint) {
        try {
            if (endpoint == null || endpoint.length() > 4096) return false;
            var uri = URI.create(endpoint);
            return "https".equals(uri.getScheme()) && HOSTS.contains(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getUserInfo() == null
                    && uri.getFragment() == null && uri.getRawQuery() == null
                    && uri.getRawPath() != null && uri.getRawPath().length() > 1;
        } catch (IllegalArgumentException invalid) {return false;}
    }

    public static boolean validRecipientKeys(String publicKey, String auth) {
        try {
            var key = Base64.getUrlDecoder().decode(publicKey);
            return key.length == 65 && key[0] == 4 && Base64.getUrlDecoder().decode(auth).length == 16
                    && nl.martijndwars.webpush.Utils.loadPublicKey(publicKey) != null;
        } catch (Exception invalid) {return false;}
    }

    public int send(String endpoint, String publicKey, String auth, long eventId, String title, String body, String url) throws Exception {
        return transmit(endpoint, publicKey, auth, String.valueOf(eventId), title, body, url);
    }
    public int sendStaff(String endpoint, String publicKey, String auth, long eventId, String title, String body, String url) throws Exception {
        return transmit(endpoint, publicKey, auth, "staff:" + eventId, title, body, url);
    }
    private int transmit(String endpoint, String publicKey, String auth, String eventId, String title, String body, String url) throws Exception {
        var encrypted = prepare(endpoint, publicKey, auth, eventId, title, body, url);
        var request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10));
        encrypted.getHeaders().forEach(request::header);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(encrypted.getBody())).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    nl.martijndwars.webpush.HttpRequest prepare(String endpoint, String publicKey, String auth, long eventId) throws Exception {
        return prepare(endpoint, publicKey, auth, eventId, "Gokul Sweets", "A new account update is waiting in your inbox.", "/profile#account-notifications");
    }

    nl.martijndwars.webpush.HttpRequest prepare(String endpoint, String publicKey, String auth, long eventId, String title, String body, String url) throws Exception {
        return prepare(endpoint, publicKey, auth, String.valueOf(eventId), title, body, url);
    }
    private nl.martijndwars.webpush.HttpRequest prepare(String endpoint, String publicKey, String auth, String eventId, String title, String body, String url) throws Exception {
        if (!validEndpoint(endpoint) || !configured()) throw new IllegalStateException("Push delivery unavailable");
        // Trusted event copy only: never customer phone, address, item contents or payment amounts.
        String payload = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(java.util.Map.of(
                "title", title, "body", body, "eventId", String.valueOf(eventId), "url", url));
        var service = new RequestBuilder(properties.getPublicKey(), properties.getPrivateKey(), properties.getSubject());
        var notification = new Notification(endpoint, publicKey, auth, payload.getBytes(StandardCharsets.UTF_8), 60);
        return service.encrypted(notification);
    }
    private static final class RequestBuilder extends PushService {
        RequestBuilder(String publicKey, String privateKey, String subject) throws java.security.GeneralSecurityException {
            super(publicKey, privateKey, subject);
        }
        nl.martijndwars.webpush.HttpRequest encrypted(Notification notification) throws Exception {
            return prepareRequest(notification, Encoding.AES128GCM);
        }
    }
}
