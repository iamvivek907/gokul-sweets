package com.gokulsweets.restaurant.customer.notification;

import com.gokulsweets.restaurant.observability.MethodTiming;

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

/**
 * Encrypt/sign with the maintained library; bounded Java HTTP transport never follows redirects.
 */
@Component
@RequiredArgsConstructor
public class WebPushTransport {

    private final WebPushProperties properties;

    private final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    private static final Set<String> HOSTS =
            Set.of("fcm.googleapis.com", "updates.push.services.mozilla.com", "web.push.apple.com");

    static {
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Returns configured information for web push transport.
     *
     * @return the {@code boolean} result
     */
    public boolean configured() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(WebPushTransport.class, "configured()");
        try {
            try {
                if (!properties.getSubject().matches("mailto:[^\\s@]+@[^\\s@]+|https://[^\\s]+"))
                    return false;
                if (Base64.getUrlDecoder().decode(properties.getPublicKey()).length != 65
                        || Base64.getUrlDecoder().decode(properties.getPrivateKey()).length != 32)
                    return false;
                new PushService(
                        properties.getPublicKey(),
                        properties.getPrivateKey(),
                        properties.getSubject());
                return true;
            } catch (Exception invalid) {
                return false;
            }
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, WebPushTransport.class, "configured()");
        }
    }

    /**
     * Valids endpoint.
     *
     * @param endpoint the endpoint
     * @return the valid endpoint result
     */
    public static boolean validEndpoint(String endpoint) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(WebPushTransport.class, "validEndpoint(String)");
        try {
            try {
                if (endpoint == null || endpoint.length() > 4096) return false;
                var uri = URI.create(endpoint);
                return "https".equals(uri.getScheme())
                        && HOSTS.contains(uri.getHost())
                        && (uri.getPort() == -1 || uri.getPort() == 443)
                        && uri.getUserInfo() == null
                        && uri.getFragment() == null
                        && uri.getRawQuery() == null
                        && uri.getRawPath() != null
                        && uri.getRawPath().length() > 1;
            } catch (IllegalArgumentException invalid) {
                return false;
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, WebPushTransport.class, "validEndpoint(String)");
        }
    }

    /**
     * Valids recipient keys.
     *
     * @param publicKey the public key
     * @param auth the auth
     * @return the valid recipient keys result
     */
    public static boolean validRecipientKeys(String publicKey, String auth) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(WebPushTransport.class, "validRecipientKeys(String,String)");
        try {
            try {
                var key = Base64.getUrlDecoder().decode(publicKey);
                return key.length == 65
                        && key[0] == 4
                        && Base64.getUrlDecoder().decode(auth).length == 16
                        && nl.martijndwars.webpush.Utils.loadPublicKey(publicKey) != null;
            } catch (Exception invalid) {
                return false;
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    WebPushTransport.class,
                    "validRecipientKeys(String,String)");
        }
    }

    /**
     * Sends web push transport data and returns the {@code int} result.
     *
     * @param endpoint the endpoint supplied to this method
     * @param publicKey the public key supplied to this method
     * @param auth the auth supplied to this method
     * @param eventId the event id supplied to this method
     * @param title the title supplied to this method
     * @param body the body supplied to this method
     * @param url the url supplied to this method
     * @return the value of {@code transmit(endpoint, publicKey, auth, String.valueOf(eventId),
     *     title, body, url)}
     * @throws Exception if the underlying operation fails
     */
    public int send(
            String endpoint,
            String publicKey,
            String auth,
            long eventId,
            String title,
            String body,
            String url)
            throws Exception {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        WebPushTransport.class,
                        "send(String,String,String,long,String,String,String)");
        try {
            return transmit(endpoint, publicKey, auth, String.valueOf(eventId), title, body, url);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    WebPushTransport.class,
                    "send(String,String,String,long,String,String,String)");
        }
    }

    /**
     * Sends staff.
     *
     * @param endpoint the endpoint
     * @param publicKey the public key
     * @param auth the auth
     * @param eventId the event id
     * @param title the title
     * @param body the body
     * @param url the url
     * @return the send staff result
     * @throws Exception if the operation cannot complete
     */
    public int sendStaff(
            String endpoint,
            String publicKey,
            String auth,
            long eventId,
            String title,
            String body,
            String url)
            throws Exception {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        WebPushTransport.class,
                        "sendStaff(String,String,String,long,String,String,String)");
        try {
            return transmit(endpoint, publicKey, auth, "staff:" + eventId, title, body, url);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    WebPushTransport.class,
                    "sendStaff(String,String,String,long,String,String,String)");
        }
    }

    /**
     * Transmits web push transport data and returns the {@code int} result.
     *
     * <p>Delegates to {@code client.send(...)}.
     *
     * @param endpoint the endpoint supplied to this method
     * @param publicKey the public key supplied to this method
     * @param auth the auth supplied to this method
     * @param eventId the event id supplied to this method
     * @param title the title supplied to this method
     * @param body the body supplied to this method
     * @param url the url supplied to this method
     * @return the value of {@code
     *     client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(encrypted.getBody())).build(),
     *     HttpResponse.BodyHandlers.discarding()).statusCode()}
     * @throws Exception if the underlying operation fails
     */
    private int transmit(
            String endpoint,
            String publicKey,
            String auth,
            String eventId,
            String title,
            String body,
            String url)
            throws Exception {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        WebPushTransport.class,
                        "transmit(String,String,String,String,String,String,String)");
        try {
            var encrypted = prepare(endpoint, publicKey, auth, eventId, title, body, url);
            var request =
                    HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10));
            encrypted.getHeaders().forEach(request::header);
            return client.send(
                            request.POST(
                                            HttpRequest.BodyPublishers.ofByteArray(
                                                    encrypted.getBody()))
                                    .build(),
                            HttpResponse.BodyHandlers.discarding())
                    .statusCode();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    WebPushTransport.class,
                    "transmit(String,String,String,String,String,String,String)");
        }
    }

    /**
     * Prepares web push transport data and returns the {@code nl.martijndwars.webpush.HttpRequest}
     * result.
     *
     * @param endpoint the endpoint supplied to this method
     * @param publicKey the public key supplied to this method
     * @param auth the auth supplied to this method
     * @param eventId the event id supplied to this method
     * @return the {@code nl.martijndwars.webpush.HttpRequest} result
     * @throws Exception if the underlying operation fails
     */
    nl.martijndwars.webpush.HttpRequest prepare(
            String endpoint, String publicKey, String auth, long eventId) throws Exception {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(WebPushTransport.class, "prepare(String,String,String,long)");
        try {
            return prepare(
                    endpoint,
                    publicKey,
                    auth,
                    eventId,
                    "Gokul Sweets",
                    "A new account update is waiting in your inbox.",
                    "/profile#account-notifications");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    WebPushTransport.class,
                    "prepare(String,String,String,long)");
        }
    }

    /**
     * Prepares web push transport data and returns the {@code nl.martijndwars.webpush.HttpRequest}
     * result.
     *
     * @param endpoint the endpoint supplied to this method
     * @param publicKey the public key supplied to this method
     * @param auth the auth supplied to this method
     * @param eventId the event id supplied to this method
     * @param title the title supplied to this method
     * @param body the body supplied to this method
     * @param url the url supplied to this method
     * @return the value of {@code prepare(endpoint, publicKey, auth, String.valueOf(eventId),
     *     title, body, url)}
     * @throws Exception if the underlying operation fails
     */
    nl.martijndwars.webpush.HttpRequest prepare(
            String endpoint,
            String publicKey,
            String auth,
            long eventId,
            String title,
            String body,
            String url)
            throws Exception {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        WebPushTransport.class,
                        "prepare(String,String,String,long,String,String,String)");
        try {
            return prepare(endpoint, publicKey, auth, String.valueOf(eventId), title, body, url);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    WebPushTransport.class,
                    "prepare(String,String,String,long,String,String,String)");
        }
    }

    /**
     * Prepares web push transport data and returns the {@code nl.martijndwars.webpush.HttpRequest}
     * result.
     *
     * <p>Delegates to {@code service.encrypted(...)}.
     *
     * @param endpoint the endpoint supplied to this method
     * @param publicKey the public key supplied to this method
     * @param auth the auth supplied to this method
     * @param eventId the event id supplied to this method
     * @param title the title supplied to this method
     * @param body the body supplied to this method
     * @param url the url supplied to this method
     * @return the value of {@code service.encrypted(notification)}
     * @throws IllegalStateException when the method rejects the request with {@code Push delivery
     *     unavailable}
     * @throws Exception if the underlying operation fails
     */
    private nl.martijndwars.webpush.HttpRequest prepare(
            String endpoint,
            String publicKey,
            String auth,
            String eventId,
            String title,
            String body,
            String url)
            throws Exception {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        WebPushTransport.class,
                        "prepare(String,String,String,String,String,String,String)");
        try {
            if (!validEndpoint(endpoint) || !configured())
                throw new IllegalStateException("Push delivery unavailable");
            // Trusted event copy only: never customer phone, address, item contents or payment
            // amounts.
            String payload =
                    tools.jackson.databind.json.JsonMapper.builder()
                            .build()
                            .writeValueAsString(
                                    java.util.Map.of(
                                            "title",
                                            title,
                                            "body",
                                            body,
                                            "eventId",
                                            String.valueOf(eventId),
                                            "url",
                                            url));
            var service =
                    new RequestBuilder(
                            properties.getPublicKey(),
                            properties.getPrivateKey(),
                            properties.getSubject());
            var notification =
                    new Notification(
                            endpoint,
                            publicKey,
                            auth,
                            payload.getBytes(StandardCharsets.UTF_8),
                            60);
            return service.encrypted(notification);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    WebPushTransport.class,
                    "prepare(String,String,String,String,String,String,String)");
        }
    }

    /** Backend request builder contract and implementation. */
    private static final class RequestBuilder extends PushService {

        /**
         * Creates a request builder instance.
         *
         * @param publicKey the public key
         * @param privateKey the private key
         * @param subject the subject
         * @throws java.security.GeneralSecurityException if construction cannot complete
         */
        RequestBuilder(String publicKey, String privateKey, String subject)
                throws java.security.GeneralSecurityException {
            super(publicKey, privateKey, subject);
        }

        /**
         * Returns encrypted information for request builder.
         *
         * @param notification the notification supplied to this method
         * @return the value of {@code prepareRequest(notification, Encoding.AES128GCM)}
         * @throws Exception if the underlying operation fails
         */
        nl.martijndwars.webpush.HttpRequest encrypted(Notification notification) throws Exception {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(
                            WebPushTransport.RequestBuilder.class, "encrypted(Notification)");
            try {
                return prepareRequest(notification, Encoding.AES128GCM);
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        WebPushTransport.RequestBuilder.class,
                        "encrypted(Notification)");
            }
        }
    }
}
