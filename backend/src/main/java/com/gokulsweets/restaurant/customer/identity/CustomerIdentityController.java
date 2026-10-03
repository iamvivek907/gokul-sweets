package com.gokulsweets.restaurant.customer.identity;

import jakarta.validation.Valid;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.customer.consent.ConsentDecision;
import com.gokulsweets.restaurant.customer.consent.ConsentLedger;
import com.gokulsweets.restaurant.customer.consent.ConsentPurpose;
import com.gokulsweets.restaurant.customer.consent.CustomerPrivacyRequests;
import com.gokulsweets.restaurant.customer.consent.PrivacyRequestKind;
import com.gokulsweets.restaurant.order.dto.CustomerOrderResponse;
import com.gokulsweets.restaurant.order.dto.CustomerOrderSummaryResponse;
import com.gokulsweets.restaurant.order.service.OrderQueryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** Identity only: no order or historical consent ownership is granted here. */
@RestController
@RequestMapping("/api/customer/identity")
@RequiredArgsConstructor
public class CustomerIdentityController {
    private static final String COOKIE = "__Host-gokul-customer";
    private static final String DEVICE_COOKIE = "__Host-gokul-device";
    private final com.gokulsweets.restaurant.loyalty.LoyaltyService loyalty;
    private final VerifiedIdentityExchange exchange;
    private final VerifiedCustomerSessionStore sessions;
    private final VerifiedCustomerPhoneLookup subjects;
    private final EnhancementProperties features;
    private final Environment settings;
    private final WebCorsProperties cors;
    private final IdentityClientConnection clientConnection;
    private final VerifiedOrderOwnership ownership;
    private final OrderQueryService orders;
    private final IdentityExchangeRateLimiter rateLimiter;
    private final IdentityDeviceRegistry devices;
    private final ConsentLedger consents;
    private final CustomerPrivacyRequests privacyRequests;
    private final CustomerAccountHub accountHub;
    private final com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox notifications;
    private final com.gokulsweets.restaurant.customer.notification.CustomerAlertPreferences alerts;

    /** Called before opening the widget. Source and device limits are shared across instances. */
    @PostMapping("/start")
    public ResponseEntity<Void> start(HttpServletRequest request) {
        var environment = enabledEnvironment();
        var source = requireTrustedMutation(request);
        var now = Instant.now();
        rateLimiter.checkStartSource(environment, source, now);
        var device = cookie(request, DEVICE_COOKIE);
        boolean newDevice = !devices.recognized(environment, device, now);
        if (newDevice) device = devices.issue(environment, now);
        rateLimiter.checkStartDevice(environment, device, now);
        var response = ResponseEntity.noContent().cacheControl(CacheControl.noStore());
        if (newDevice) response.header(HttpHeaders.SET_COOKIE, ResponseCookie.from(DEVICE_COOKIE, device)
                .httpOnly(true).secure(true).sameSite("Strict").path("/")
                .maxAge(Duration.ofDays(30)).build().toString());
        return response.build();
    }

    @PostMapping(value = "/exchange", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> exchange(@RequestBody ExchangeRequest payload,
                                                           HttpServletRequest request) {
        var environment = enabledEnvironment();
        var sourceAddress = requireTrustedMutation(request);
        if (payload == null || payload.accessToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identity proof required");
        }
        var device = cookie(request, DEVICE_COOKIE);
        if (!devices.recognized(environment, device, Instant.now())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var issued = exchange.exchange(environment, sourceAddress, device,
                payload.accessToken(), cookie(request), Instant.now());
        var cookie = ResponseCookie.from(COOKIE, issued.token())
                .httpOnly(true).secure(true).sameSite("Strict").path("/")
                .maxAge(Duration.between(Instant.now(), issued.expiresAt())).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("authenticated", true, "expiresAt", issued.expiresAt().toString()));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        var environment = enabledEnvironment();
        var token = cookie(request);
        var subject = sessions.subject(environment, token, Instant.now());
        if (subject.isEmpty()) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(Map.of("authenticated", false));
        }
        if (!clientConnection.resolve(request).secure()
                || !cors.effectiveAllowedOrigins(settings).contains(request.getHeader(HttpHeaders.ORIGIN))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var phone = subjects.verifiedPhone(environment, subject.get())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var name = subjects.displayName(environment, subject.get());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(name.<Map<String, Object>>map(value -> Map.of("authenticated", true, "phone", phone, "name", value))
                        .orElseGet(() -> Map.of("authenticated", true, "phone", phone)));
    }

    @PutMapping(value = "/me/name", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateName(@RequestBody NameRequest payload, HttpServletRequest request) {
        var environment = enabledEnvironment();
        requireTrustedMutation(request);
        var subject = requiredSubject(request, environment);
        if (payload == null || payload.name() == null || !payload.name().trim().matches("[\\p{L}][\\p{L}\\p{M} .'-]{1,79}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a name of 2 to 80 letters");
        }
        subjects.updateDisplayName(environment, subject, payload.name());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    public record NameRequest(String name) { }

    @GetMapping("/orders")
    public ResponseEntity<List<CustomerOrderSummaryResponse>> orders(HttpServletRequest request) {
        var environment = enabledEnvironment();
        var subject = requiredSubject(request, environment);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(orders.getCustomerOrderHistory(ownership.orderNumbers(environment.name(), subject)));
    }

    @GetMapping("/orders/{orderNumber}")
    public ResponseEntity<CustomerOrderResponse> order(@PathVariable String orderNumber,
                                                         HttpServletRequest request) {
        var environment = enabledEnvironment();
        var subject = requiredSubject(request, environment);
        if (!ownership.owns(environment.name(), subject, orderNumber)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(orders.getCustomerOrder(orderNumber));
    }

    @GetMapping("/account")
    public ResponseEntity<CustomerAccountHub.Snapshot> account(HttpServletRequest request) {
        var environment = accountEnvironment();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accountHub.snapshot(environment.name(), requiredSubject(request, environment)));
    }

    @PutMapping("/account/preferences")
    public ResponseEntity<Void> preferences(@RequestBody CustomerAccountHub.Preferences preferences,
                                             HttpServletRequest request) {
        var environment = accountEnvironment();
        requireTrustedMutation(request);
        accountHub.savePreferences(environment.name(), requiredSubject(request, environment), preferences);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PutMapping("/account/favourites/{productId}")
    public ResponseEntity<Void> favourite(@PathVariable long productId, HttpServletRequest request) {
        var environment = accountEnvironment();
        requireTrustedMutation(request);
        accountHub.setFavourite(environment.name(), requiredSubject(request, environment), productId, true);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/account/favourites/{productId}")
    public ResponseEntity<Void> removeFavourite(@PathVariable long productId, HttpServletRequest request) {
        var environment = accountEnvironment();
        requireTrustedMutation(request);
        accountHub.setFavourite(environment.name(), requiredSubject(request, environment), productId, false);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/account/addresses")
    public ResponseEntity<CustomerAccountHub.Address> addAddress(@RequestBody CustomerAccountHub.AddressInput input,
                                                                  HttpServletRequest request) {
        var environment = accountEnvironment();
        requireTrustedMutation(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accountHub.addAddress(environment.name(), requiredSubject(request, environment), input));
    }

    @PutMapping("/account/addresses/{addressId}")
    public ResponseEntity<CustomerAccountHub.Address> updateAddress(@PathVariable long addressId,
            @RequestBody CustomerAccountHub.AddressInput input, HttpServletRequest request) {
        var environment = accountEnvironment();
        requireTrustedMutation(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accountHub.updateAddress(environment.name(), requiredSubject(request, environment), addressId, input));
    }

    @DeleteMapping("/account/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable long addressId, HttpServletRequest request) {
        var environment = accountEnvironment();
        requireTrustedMutation(request);
        accountHub.deleteAddress(environment.name(), requiredSubject(request, environment), addressId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/rewards")
    public ResponseEntity<com.gokulsweets.restaurant.loyalty.LoyaltyService.Wallet> rewards(HttpServletRequest request) {
        var environment=enabledEnvironment();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(loyalty.wallet(environment.name(),requiredSubject(request,environment),null));
    }

    private ConsentEnvironment accountEnvironment() {
        var environment = enabledEnvironment();
        if (!features.isCustomerAccountHub()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return environment;
    }

    @GetMapping("/consents")
    public ResponseEntity<Map<ConsentPurpose, ConsentDecision>> consents(HttpServletRequest request) {
        var environment = consentEnvironment();
        var subject = requiredSubject(request, environment);
        var policy = settings.getRequiredProperty("gokul.consent.policy-version");
        var decisions = new java.util.EnumMap<ConsentPurpose, ConsentDecision>(ConsentPurpose.class);
        for (var purpose : ConsentPurpose.values()) {
            var decision = consents.current(environment, subject, purpose);
            decisions.put(purpose, decision.granted() && !policy.equals(decision.policyVersion())
                    ? new ConsentDecision(false, decision.policyVersion(), decision.recordedAt()) : decision);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decisions);
    }

    @PutMapping("/consents/{purpose}")
    public ResponseEntity<ConsentDecision> updateConsent(@PathVariable ConsentPurpose purpose,
                                                           @RequestBody ConsentChoice choice,
                                                           HttpServletRequest request) {
        var environment = consentEnvironment();
        requireTrustedMutation(request);
        var subject = requiredSubject(request, environment);
        if (choice == null || choice.granted() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consent choice required");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(consents.record(environment, subject, purpose,
                        settings.getRequiredProperty("gokul.consent.policy-version"), choice.granted()));
    }

    public record ConsentChoice(Boolean granted) { }

    @GetMapping("/privacy-requests")
    public ResponseEntity<List<CustomerPrivacyRequests.Request>> privacyRequests(HttpServletRequest request) {
        var environment = consentEnvironment();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(privacyRequests.forSubject(environment, requiredSubject(request, environment)));
    }

    @PostMapping("/privacy-requests/{kind}")
    public ResponseEntity<CustomerPrivacyRequests.Request> submitPrivacyRequest(
            @PathVariable PrivacyRequestKind kind, HttpServletRequest request) {
        var environment = consentEnvironment();
        requireTrustedMutation(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(privacyRequests.submit(environment, requiredSubject(request, environment), kind));
    }

    @GetMapping("/notifications")
    public ResponseEntity<com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox.Page> notifications(
            @RequestParam(required = false) Long before, @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "") String search, HttpServletRequest request) {
        var environment = notificationEnvironment();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(notifications.page(environment.name(), requiredSubject(request, environment), before, unreadOnly, search));
    }

    @PutMapping("/notifications/{id}/read")
    public ResponseEntity<Void> readNotification(@PathVariable long id, HttpServletRequest request) {
        var environment = notificationEnvironment();
        requireTrustedMutation(request);
        notifications.markRead(environment.name(), requiredSubject(request, environment), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    public record NotificationReadTarget(String targetType, String targetId, Long throughId) {}
    public record NotificationReadAll(@jakarta.validation.constraints.Positive long throughId) {}

    @PutMapping("/notifications/read-all")
    public ResponseEntity<Void> readAllNotifications(@Valid @RequestBody NotificationReadAll input, HttpServletRequest request) {
        var environment=notificationEnvironment(); requireTrustedMutation(request);
        notifications.markAllRead(environment.name(),requiredSubject(request,environment),input.throughId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PutMapping("/notifications/read-target")
    public ResponseEntity<Void> readNotificationTarget(@RequestBody NotificationReadTarget input, HttpServletRequest request) {
        var environment=notificationEnvironment(); requireTrustedMutation(request);
        notifications.markTargetRead(environment.name(),requiredSubject(request,environment),input.targetType(),input.targetId(),input.throughId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/notification-preferences")
    public ResponseEntity<com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox.Preferences> notificationPreferences(HttpServletRequest request) {
        var environment = notificationEnvironment();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(notifications.preferences(environment.name(), requiredSubject(request, environment)));
    }

    @PutMapping("/notification-preferences")
    public ResponseEntity<com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox.Preferences> saveNotificationPreferences(
            @RequestBody com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox.PreferenceInput input,
            HttpServletRequest request) {
        var environment = notificationEnvironment();
        requireTrustedMutation(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(notifications.savePreferences(environment.name(), requiredSubject(request, environment), input));
    }

    @GetMapping("/notification-alerts")
    public ResponseEntity<com.gokulsweets.restaurant.customer.notification.CustomerAlertPreferences.Settings> alertSettings(HttpServletRequest request) {
        var environment = alertEnvironment();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(alerts.settings(environment.name(), requiredSubject(request, environment)));
    }

    @PutMapping("/notification-alerts")
    public ResponseEntity<com.gokulsweets.restaurant.customer.notification.CustomerAlertPreferences.Settings> saveAlertSettings(
            @RequestBody com.gokulsweets.restaurant.customer.notification.CustomerAlertPreferences.Input input, HttpServletRequest request) {
        var environment = alertEnvironment();
        requireTrustedMutation(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(alerts.save(environment.name(), requiredSubject(request, environment), input));
    }

    @PostMapping("/push-subscriptions")
    public ResponseEntity<com.gokulsweets.restaurant.customer.notification.CustomerAlertPreferences.SubscriptionResult> subscribePush(
            @RequestBody com.gokulsweets.restaurant.customer.notification.CustomerAlertPreferences.SubscriptionInput input, HttpServletRequest request) {
        var environment = alertEnvironment();
        requireTrustedMutation(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(alerts.subscribe(environment.name(), requiredSubject(request, environment), cookie(request), input));
    }

    @DeleteMapping("/push-subscriptions/{id}")
    public ResponseEntity<Void> unsubscribePush(@PathVariable UUID id, HttpServletRequest request) {
        // Turning delivery off remains possible if the alert rollout is disabled.
        var environment = enabledEnvironment();
        requireTrustedMutation(request);
        alerts.unsubscribe(environment.name(), requiredSubject(request, environment), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ConsentEnvironment alertEnvironment() {
        var environment = enabledEnvironment();
        if (!alerts.enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return environment;
    }

    private ConsentEnvironment notificationEnvironment() {
        var environment = enabledEnvironment();
        if (!notifications.enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return environment;
    }

    private ConsentEnvironment consentEnvironment() {
        var environment = enabledEnvironment();
        var policy = settings.getProperty("gokul.consent.policy-version", "");
        if (!features.isCustomerConsentControls() || !policy.matches("[A-Za-z0-9._-]{1,40}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return environment;
    }

    private UUID requiredSubject(HttpServletRequest request, ConsentEnvironment environment) {
        // An allowed browser Origin prevents unrelated sites reading authenticated data.
        if (!clientConnection.resolve(request).secure()
                || !cors.effectiveAllowedOrigins(settings).contains(request.getHeader(HttpHeaders.ORIGIN))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return sessions.subject(environment, cookie(request), Instant.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var environment = enabledEnvironment();
        requireTrustedMutation(request);
        sessions.revoke(environment, cookie(request), Instant.now());
        var expired = ResponseCookie.from(COOKIE, "")
                .httpOnly(true).secure(true).sameSite("Strict").path("/")
                .maxAge(Duration.ZERO).build();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, expired.toString()).build();
    }

    private ConsentEnvironment enabledEnvironment() {
        if (!features.isCustomerOtpIdentity()
                || !settings.getProperty("gokul.environment-isolation.enabled", Boolean.class, false)
                || !settings.getProperty("gokul.web.environment-cors-enabled", Boolean.class, false)
                || !settings.getProperty("gokul.identity.provider-abuse-controls-verified", Boolean.class, false)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return switch (settings.getProperty("gokul.environment-isolation.environment", "")) {
            case "DEV" -> ConsentEnvironment.DEV;
            case "PROD" -> ConsentEnvironment.PROD;
            default -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        };
    }

    private String requireTrustedMutation(HttpServletRequest request) {
        var connection = clientConnection.resolve(request);
        if (!connection.secure() || !cors.effectiveAllowedOrigins(settings)
                .contains(request.getHeader(HttpHeaders.ORIGIN))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return connection.sourceAddress();
    }

    private static String cookie(HttpServletRequest request) {
        return cookie(request, COOKIE);
    }

    private static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(value -> name.equals(value.getName()))
                .map(jakarta.servlet.http.Cookie::getValue).findFirst().orElse(null);
    }

    public record ExchangeRequest(String accessToken) { }

    @ExceptionHandler(IdentityExchangeRateLimiter.Limited.class)
    public ResponseEntity<Void> limited() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .cacheControl(CacheControl.noStore()).build();
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Void> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore()).build();
    }
}
