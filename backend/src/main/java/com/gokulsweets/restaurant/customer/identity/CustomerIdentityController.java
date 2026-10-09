package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentDecision;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.customer.consent.ConsentLedger;
import com.gokulsweets.restaurant.customer.consent.ConsentPurpose;
import com.gokulsweets.restaurant.customer.consent.CustomerPrivacyRequests;
import com.gokulsweets.restaurant.customer.consent.PrivacyRequestKind;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.CustomerOrderResponse;
import com.gokulsweets.restaurant.order.dto.CustomerOrderSummaryResponse;
import com.gokulsweets.restaurant.order.service.OrderQueryService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Identity only: no order or historical consent ownership is granted here. */
@RestController
@RequestMapping("/api/customer/identity")
@RequiredArgsConstructor
public class CustomerIdentityController {

    private static final String COOKIE = AppConstant.CUSTOMER_IDENTITY_CONTROLLER_COOKIE;

    private static final String DEVICE_COOKIE =
            AppConstant.CUSTOMER_IDENTITY_CONTROLLER_DEVICE_COOKIE;

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

    private final ReverseAddressLookup addressLookup;

    private final com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox
            notifications;

    private final com.gokulsweets.restaurant.customer.notification.CustomerAlertPreferences alerts;

    /**
     * Called before opening the widget. Source and device limits are shared across instances.
     *
     * @param request the request
     * @return the operation result
     */
    @PostMapping("/start")
    public ResponseEntity<Void> start(HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerIdentityController.class, "start(HttpServletRequest)");
        try {
            var environment = enabledEnvironment();
            var source = requireTrustedMutation(request);
            var now = Instant.now();
            rateLimiter.checkStartSource(environment, source, now);
            var device = cookie(request, DEVICE_COOKIE);
            boolean newDevice = !devices.recognized(environment, device, now);
            if (newDevice) device = devices.issue(environment, now);
            rateLimiter.checkStartDevice(environment, device, now);
            var response = ResponseEntity.noContent().cacheControl(CacheControl.noStore());
            if (newDevice)
                response.header(
                        HttpHeaders.SET_COOKIE,
                        ResponseCookie.from(DEVICE_COOKIE, device)
                                .httpOnly(true)
                                .secure(true)
                                .sameSite("Strict")
                                .path("/")
                                .maxAge(Duration.ofDays(30))
                                .build()
                                .toString());
            return response.build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "start(HttpServletRequest)");
        }
    }

    /**
     * Handles {@code POST /api/customer/identity/exchange} for customer identity.
     *
     * @param payload the payload supplied to this method
     * @param request the request supplied to this method
     * @return the {@code ResponseEntity<Map<String, Object>>} result
     * @throws ResponseStatusException when the method rejects the request with {@code Identity
     *     proof required}
     */
    @PostMapping(value = "/exchange", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> exchange(
            @RequestBody ExchangeRequest payload, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "exchange(ExchangeRequest,HttpServletRequest)");
        try {
            var environment = enabledEnvironment();
            var sourceAddress = requireTrustedMutation(request);
            if (payload == null || payload.accessToken() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Identity proof required");
            }
            var device = cookie(request, DEVICE_COOKIE);
            if (!devices.recognized(environment, device, Instant.now())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            var issued =
                    exchange.exchange(
                            environment,
                            sourceAddress,
                            device,
                            payload.accessToken(),
                            cookie(request),
                            Instant.now());
            var cookie =
                    ResponseCookie.from(COOKIE, issued.token())
                            .httpOnly(true)
                            .secure(true)
                            .sameSite("Strict")
                            .path("/")
                            .maxAge(Duration.between(Instant.now(), issued.expiresAt()))
                            .build();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(
                            Map.of(
                                    "authenticated",
                                    true,
                                    "expiresAt",
                                    issued.expiresAt().toString()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "exchange(ExchangeRequest,HttpServletRequest)");
        }
    }

    /**
     * Handles {@code GET /api/customer/identity/me} for customer identity.
     *
     * @param request the request supplied to this method
     * @return the {@code ResponseEntity<Map<String, Object>>} result
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerIdentityController.class, "me(HttpServletRequest)");
        try {
            var environment = enabledEnvironment();
            var token = cookie(request);
            var subject = sessions.subject(environment, token, Instant.now());
            if (subject.isEmpty()) {
                return ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(Map.of("authenticated", false));
            }
            if (!clientConnection.resolve(request).secure()
                    || !cors.effectiveAllowedOrigins(settings)
                            .contains(request.getHeader(HttpHeaders.ORIGIN))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            var phone =
                    subjects.verifiedPhone(environment, subject.get())
                            .orElseThrow(
                                    () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
            var name = subjects.displayName(environment, subject.get());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            name.<Map<String, Object>>map(
                                            value ->
                                                    Map.of(
                                                            "authenticated",
                                                            true,
                                                            "phone",
                                                            phone,
                                                            "name",
                                                            value))
                                    .orElseGet(
                                            () -> Map.of("authenticated", true, "phone", phone)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "me(HttpServletRequest)");
        }
    }

    /**
     * Updates name.
     *
     * @param payload the payload
     * @param request the request
     * @return the update name result
     */
    @PutMapping(value = "/me/name", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateName(
            @RequestBody NameRequest payload, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "updateName(NameRequest,HttpServletRequest)");
        try {
            var environment = enabledEnvironment();
            requireTrustedMutation(request);
            var subject = requiredSubject(request, environment);
            if (payload == null
                    || payload.name() == null
                    || !payload.name().trim().matches("[\\p{L}][\\p{L}\\p{M} .'-]{1,79}")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Enter a name of 2 to 80 letters");
            }
            subjects.updateDisplayName(environment, subject, payload.name());
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "updateName(NameRequest,HttpServletRequest)");
        }
    }

    /**
     * Immutable name request data contract.
     *
     * @param name the name
     */
    public record NameRequest(String name) {}

    /**
     * Handles {@code GET /api/customer/identity/orders} for customer identity.
     *
     * @param request the request supplied to this method
     * @return the value of {@code
     *     ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(orders.getCustomerOrderHistory(ownership.orderNumbers(environment.name(),
     *     subject)))}
     */
    @GetMapping("/orders")
    public ResponseEntity<List<CustomerOrderSummaryResponse>> orders(HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerIdentityController.class, "orders(HttpServletRequest)");
        try {
            var environment = enabledEnvironment();
            var subject = requiredSubject(request, environment);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            orders.getCustomerOrderHistory(
                                    ownership.orderNumbers(environment.name(), subject)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "orders(HttpServletRequest)");
        }
    }

    /**
     * Immutable order history page data contract.
     *
     * @param orders the orders
     * @param nextBefore the next before
     */
    public record OrderHistoryPage(List<CustomerOrderSummaryResponse> orders, String nextBefore) {}

    /**
     * Orders page.
     *
     * @param request the request
     * @param before the before
     * @param limit the limit
     * @return the order page result
     */
    @GetMapping("/orders/page")
    public ResponseEntity<OrderHistoryPage> orderPage(
            HttpServletRequest request,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "10") int limit) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "orderPage(HttpServletRequest,String,int)");
        try {
            var environment = enabledEnvironment();
            var subject = requiredSubject(request, environment);
            var references = ownership.orderNumberPage(environment.name(), subject, before, limit);
            var visible = references.stream().limit(limit).toList();
            var summaries = orders.getCustomerOrderHistory(visible);
            // Retain the cursor query's deterministic id tie-breaker when creation times match.
            var byReference =
                    summaries.stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            CustomerOrderSummaryResponse::orderNumber,
                                            java.util.function.Function.identity()));
            var ordered =
                    visible.stream()
                            .map(byReference::get)
                            .filter(java.util.Objects::nonNull)
                            .toList();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            new OrderHistoryPage(
                                    ordered, references.size() > limit ? visible.getLast() : null));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "orderPage(HttpServletRequest,String,int)");
        }
    }

    /**
     * Handles {@code GET /api/customer/identity/orders/{orderNumber}} for customer identity.
     *
     * @param orderNumber the order number supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code
     *     ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(orders.getCustomerOrder(orderNumber))}
     */
    @GetMapping("/orders/{orderNumber}")
    public ResponseEntity<CustomerOrderResponse> order(
            @PathVariable String orderNumber, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class, "order(String,HttpServletRequest)");
        try {
            var environment = enabledEnvironment();
            var subject = requiredSubject(request, environment);
            if (!ownership.owns(environment.name(), subject, orderNumber)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(orders.getCustomerOrder(orderNumber));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "order(String,HttpServletRequest)");
        }
    }

    /**
     * Locations available.
     *
     * @param request the request
     * @return the location available result
     */
    @GetMapping("/account/location")
    public ResponseEntity<Map<String, Boolean>> locationAvailable(HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class, "locationAvailable(HttpServletRequest)");
        try {
            var environment = enabledEnvironment();
            requiredSubject(request, environment);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("enabled", addressLookup.enabled()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "locationAvailable(HttpServletRequest)");
        }
    }

    /**
     * Suggests address.
     *
     * @param coordinates the coordinates
     * @param request the request
     * @return the suggest address result
     */
    @PostMapping("/account/location")
    public ResponseEntity<ReverseAddressLookup.Suggestion> suggestAddress(
            @RequestBody ReverseAddressLookup.Coordinates coordinates, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "suggestAddress(ReverseAddressLookup.Coordinates,HttpServletRequest)");
        try {
            var environment = enabledEnvironment();
            requireTrustedMutation(request);
            var subject = requiredSubject(request, environment);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(addressLookup.suggest(environment.name(), subject, coordinates));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "suggestAddress(ReverseAddressLookup.Coordinates,HttpServletRequest)");
        }
    }

    /**
     * Handles {@code GET /api/customer/identity/account} for customer identity.
     *
     * @param request the request supplied to this method
     * @return the value of {@code
     *     ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accountHub.snapshot(environment.name(),
     *     requiredSubject(request, environment)))}
     */
    @GetMapping("/account")
    public ResponseEntity<CustomerAccountHub.Snapshot> account(HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerIdentityController.class, "account(HttpServletRequest)");
        try {
            var environment = accountEnvironment();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            accountHub.snapshot(
                                    environment.name(), requiredSubject(request, environment)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "account(HttpServletRequest)");
        }
    }

    /**
     * Handles {@code PUT /api/customer/identity/account/preferences} for customer identity.
     *
     * @param preferences the preferences supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code
     *     ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()}
     */
    @PutMapping("/account/preferences")
    public ResponseEntity<Void> preferences(
            @RequestBody CustomerAccountHub.Preferences preferences, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "preferences(CustomerAccountHub.Preferences,HttpServletRequest)");
        try {
            var environment = accountEnvironment();
            requireTrustedMutation(request);
            accountHub.savePreferences(
                    environment.name(), requiredSubject(request, environment), preferences);
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "preferences(CustomerAccountHub.Preferences,HttpServletRequest)");
        }
    }

    /**
     * Handles {@code PUT /api/customer/identity/account/favourites/{productId}} for customer
     * identity.
     *
     * @param productId the product id supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code
     *     ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()}
     */
    @PutMapping("/account/favourites/{productId}")
    public ResponseEntity<Void> favourite(
            @PathVariable long productId, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class, "favourite(long,HttpServletRequest)");
        try {
            var environment = accountEnvironment();
            requireTrustedMutation(request);
            accountHub.setFavourite(
                    environment.name(), requiredSubject(request, environment), productId, true);
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "favourite(long,HttpServletRequest)");
        }
    }

    /**
     * Removes favourite.
     *
     * @param productId the product id
     * @param request the request
     * @return the remove favourite result
     */
    @DeleteMapping("/account/favourites/{productId}")
    public ResponseEntity<Void> removeFavourite(
            @PathVariable long productId, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "removeFavourite(long,HttpServletRequest)");
        try {
            var environment = accountEnvironment();
            requireTrustedMutation(request);
            accountHub.setFavourite(
                    environment.name(), requiredSubject(request, environment), productId, false);
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "removeFavourite(long,HttpServletRequest)");
        }
    }

    /**
     * Adds address.
     *
     * @param input the input
     * @param request the request
     * @return the add address result
     */
    @PostMapping("/account/addresses")
    public ResponseEntity<CustomerAccountHub.Address> addAddress(
            @RequestBody CustomerAccountHub.AddressInput input, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "addAddress(CustomerAccountHub.AddressInput,HttpServletRequest)");
        try {
            var environment = accountEnvironment();
            requireTrustedMutation(request);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            accountHub.addAddress(
                                    environment.name(),
                                    requiredSubject(request, environment),
                                    input));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "addAddress(CustomerAccountHub.AddressInput,HttpServletRequest)");
        }
    }

    /**
     * Updates address.
     *
     * @param addressId the address id
     * @param input the input
     * @param request the request
     * @return the update address result
     */
    @PutMapping("/account/addresses/{addressId}")
    public ResponseEntity<CustomerAccountHub.Address> updateAddress(
            @PathVariable long addressId,
            @RequestBody CustomerAccountHub.AddressInput input,
            HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "updateAddress(long,CustomerAccountHub.AddressInput,HttpServletRequest)");
        try {
            var environment = accountEnvironment();
            requireTrustedMutation(request);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            accountHub.updateAddress(
                                    environment.name(),
                                    requiredSubject(request, environment),
                                    addressId,
                                    input));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "updateAddress(long,CustomerAccountHub.AddressInput,HttpServletRequest)");
        }
    }

    /**
     * Deletes address.
     *
     * @param addressId the address id
     * @param request the request
     * @return the delete address result
     */
    @DeleteMapping("/account/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @PathVariable long addressId, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class, "deleteAddress(long,HttpServletRequest)");
        try {
            var environment = accountEnvironment();
            requireTrustedMutation(request);
            accountHub.deleteAddress(
                    environment.name(), requiredSubject(request, environment), addressId);
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "deleteAddress(long,HttpServletRequest)");
        }
    }

    /**
     * Handles {@code GET /api/customer/identity/rewards} for customer identity.
     *
     * @param request the request supplied to this method
     * @return the value of {@code
     *     ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(loyalty.wallet(environment.name(),
     *     requiredSubject(request, environment), null))}
     */
    @GetMapping("/rewards")
    public ResponseEntity<com.gokulsweets.restaurant.loyalty.LoyaltyService.Wallet> rewards(
            HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerIdentityController.class, "rewards(HttpServletRequest)");
        try {
            var environment = enabledEnvironment();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            loyalty.wallet(
                                    environment.name(),
                                    requiredSubject(request, environment),
                                    null));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "rewards(HttpServletRequest)");
        }
    }

    /**
     * Accounts environment.
     *
     * @return the account environment result
     */
    private ConsentEnvironment accountEnvironment() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerIdentityController.class, "accountEnvironment()");
        try {
            var environment = enabledEnvironment();
            if (!features.isCustomerAccountHub())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return environment;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "accountEnvironment()");
        }
    }

    /**
     * Handles {@code GET /api/customer/identity/consents} for customer identity.
     *
     * @param request the request supplied to this method
     * @return the value of {@code
     *     ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decisions)}
     */
    @GetMapping("/consents")
    public ResponseEntity<Map<ConsentPurpose, ConsentDecision>> consents(
            HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class, "consents(HttpServletRequest)");
        try {
            var environment = consentEnvironment();
            var subject = requiredSubject(request, environment);
            var policy = settings.getRequiredProperty("gokul.consent.policy-version");
            var decisions =
                    new java.util.EnumMap<ConsentPurpose, ConsentDecision>(ConsentPurpose.class);
            for (var purpose : ConsentPurpose.values()) {
                var decision = consents.current(environment, subject, purpose);
                decisions.put(
                        purpose,
                        decision.granted() && !policy.equals(decision.policyVersion())
                                ? new ConsentDecision(
                                        false, decision.policyVersion(), decision.recordedAt())
                                : decision);
            }
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decisions);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "consents(HttpServletRequest)");
        }
    }

    /**
     * Updates consent.
     *
     * @param purpose the purpose
     * @param choice the choice
     * @param request the request
     * @return the update consent result
     */
    @PutMapping("/consents/{purpose}")
    public ResponseEntity<ConsentDecision> updateConsent(
            @PathVariable ConsentPurpose purpose,
            @RequestBody ConsentChoice choice,
            HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "updateConsent(ConsentPurpose,ConsentChoice,HttpServletRequest)");
        try {
            var environment = consentEnvironment();
            requireTrustedMutation(request);
            var subject = requiredSubject(request, environment);
            if (choice == null || choice.granted() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Consent choice required");
            }
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            consents.record(
                                    environment,
                                    subject,
                                    purpose,
                                    settings.getRequiredProperty("gokul.consent.policy-version"),
                                    choice.granted()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "updateConsent(ConsentPurpose,ConsentChoice,HttpServletRequest)");
        }
    }

    /**
     * Immutable consent choice data contract.
     *
     * @param granted the granted
     */
    public record ConsentChoice(Boolean granted) {}

    /**
     * Privacy requests.
     *
     * @param request the request
     * @return the privacy requests result
     */
    @GetMapping("/privacy-requests")
    public ResponseEntity<List<CustomerPrivacyRequests.Request>> privacyRequests(
            HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class, "privacyRequests(HttpServletRequest)");
        try {
            var environment = consentEnvironment();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            privacyRequests.forSubject(
                                    environment, requiredSubject(request, environment)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "privacyRequests(HttpServletRequest)");
        }
    }

    /**
     * Submits privacy request.
     *
     * @param kind the kind
     * @param request the request
     * @return the submit privacy request result
     */
    @PostMapping("/privacy-requests/{kind}")
    public ResponseEntity<CustomerPrivacyRequests.Request> submitPrivacyRequest(
            @PathVariable PrivacyRequestKind kind, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "submitPrivacyRequest(PrivacyRequestKind,HttpServletRequest)");
        try {
            var environment = consentEnvironment();
            requireTrustedMutation(request);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            privacyRequests.submit(
                                    environment, requiredSubject(request, environment), kind));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "submitPrivacyRequest(PrivacyRequestKind,HttpServletRequest)");
        }
    }

    /**
     * Handles {@code GET /api/customer/identity/notifications} for customer identity.
     *
     * @param before the before supplied to this method
     * @param unreadOnly the unread only supplied to this method
     * @param search the search supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code
     *     ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(notifications.page(environment.name(),
     *     requiredSubject(request, environment), before, unreadOnly, search))}
     */
    @GetMapping("/notifications")
    public ResponseEntity<
                    com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox.Page>
            notifications(
                    @RequestParam(required = false) Long before,
                    @RequestParam(defaultValue = "false") boolean unreadOnly,
                    @RequestParam(defaultValue = "") String search,
                    HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "notifications(Long,boolean,String,HttpServletRequest)");
        try {
            var environment = notificationEnvironment();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            notifications.page(
                                    environment.name(),
                                    requiredSubject(request, environment),
                                    before,
                                    unreadOnly,
                                    search));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "notifications(Long,boolean,String,HttpServletRequest)");
        }
    }

    /**
     * Reads notification.
     *
     * @param id the id
     * @param request the request
     * @return the read notification result
     */
    @PutMapping("/notifications/{id}/read")
    public ResponseEntity<Void> readNotification(
            @PathVariable long id, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "readNotification(long,HttpServletRequest)");
        try {
            var environment = notificationEnvironment();
            requireTrustedMutation(request);
            notifications.markRead(environment.name(), requiredSubject(request, environment), id);
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "readNotification(long,HttpServletRequest)");
        }
    }

    /**
     * Immutable notification read target data contract.
     *
     * @param targetType the target type
     * @param targetId the target id
     * @param throughId the through id
     */
    public record NotificationReadTarget(String targetType, String targetId, Long throughId) {}

    /**
     * Immutable notification read all data contract.
     *
     * @param throughId the through id
     */
    public record NotificationReadAll(@jakarta.validation.constraints.Positive long throughId) {}

    /**
     * Reads all notifications.
     *
     * @param input the input
     * @param request the request
     * @return the read all notifications result
     */
    @PutMapping("/notifications/read-all")
    public ResponseEntity<Void> readAllNotifications(
            @Valid @RequestBody NotificationReadAll input, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "readAllNotifications(NotificationReadAll,HttpServletRequest)");
        try {
            var environment = notificationEnvironment();
            requireTrustedMutation(request);
            notifications.markAllRead(
                    environment.name(), requiredSubject(request, environment), input.throughId());
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "readAllNotifications(NotificationReadAll,HttpServletRequest)");
        }
    }

    /**
     * Reads notification target.
     *
     * @param input the input
     * @param request the request
     * @return the read notification target result
     */
    @PutMapping("/notifications/read-target")
    public ResponseEntity<Void> readNotificationTarget(
            @RequestBody NotificationReadTarget input, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "readNotificationTarget(NotificationReadTarget,HttpServletRequest)");
        try {
            var environment = notificationEnvironment();
            requireTrustedMutation(request);
            notifications.markTargetRead(
                    environment.name(),
                    requiredSubject(request, environment),
                    input.targetType(),
                    input.targetId(),
                    input.throughId());
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "readNotificationTarget(NotificationReadTarget,HttpServletRequest)");
        }
    }

    /**
     * Notifications preferences.
     *
     * @param request the request
     * @return the notification preferences result
     */
    @GetMapping("/notification-preferences")
    public ResponseEntity<
                    com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox
                            .Preferences>
            notificationPreferences(HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "notificationPreferences(HttpServletRequest)");
        try {
            var environment = notificationEnvironment();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            notifications.preferences(
                                    environment.name(), requiredSubject(request, environment)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "notificationPreferences(HttpServletRequest)");
        }
    }

    /**
     * Saves notification preferences.
     *
     * @param input the input
     * @param request the request
     * @return the save notification preferences result
     */
    @PutMapping("/notification-preferences")
    public ResponseEntity<
                    com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox
                            .Preferences>
            saveNotificationPreferences(
                    @RequestBody
                            com.gokulsweets.restaurant.customer.notification
                                            .CustomerNotificationInbox.PreferenceInput
                                    input,
                    HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "saveNotificationPreferences(com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox.PreferenceInput,HttpServletRequest)");
        try {
            var environment = notificationEnvironment();
            requireTrustedMutation(request);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            notifications.savePreferences(
                                    environment.name(),
                                    requiredSubject(request, environment),
                                    input));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "saveNotificationPreferences(com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox.PreferenceInput,HttpServletRequest)");
        }
    }

    /**
     * Alerts settings.
     *
     * @param request the request
     * @return the alert settings result
     */
    @GetMapping("/notification-alerts")
    public ResponseEntity<
                    com.gokulsweets.restaurant.customer.notification.CustomerAlertPreferences
                            .Settings>
            alertSettings(HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class, "alertSettings(HttpServletRequest)");
        try {
            var environment = alertEnvironment();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            alerts.settings(
                                    environment.name(), requiredSubject(request, environment)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "alertSettings(HttpServletRequest)");
        }
    }

    /**
     * Saves alert settings.
     *
     * @param input the input
     * @param request the request
     * @return the save alert settings result
     */
    @PutMapping("/notification-alerts")
    public ResponseEntity<
                    com.gokulsweets.restaurant.customer.notification.CustomerAlertPreferences
                            .Settings>
            saveAlertSettings(
                    @RequestBody
                            com.gokulsweets.restaurant.customer.notification
                                            .CustomerAlertPreferences.Input
                                    input,
                    HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "saveAlertSettings(com.gokulsweets.restaurant.customer.notification.CustomerAlertPreferences.Input,HttpServletRequest)");
        try {
            var environment = alertEnvironment();
            requireTrustedMutation(request);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            alerts.save(
                                    environment.name(),
                                    requiredSubject(request, environment),
                                    input));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "saveAlertSettings(com.gokulsweets.restaurant.customer.notification.CustomerAlertPreferences.Input,HttpServletRequest)");
        }
    }

    /**
     * Subscribes push.
     *
     * @param input the input
     * @param request the request
     * @return the subscribe push result
     */
    @PostMapping("/push-subscriptions")
    public ResponseEntity<
                    com.gokulsweets.restaurant.customer.notification.CustomerAlertPreferences
                            .SubscriptionResult>
            subscribePush(
                    @RequestBody
                            com.gokulsweets.restaurant.customer.notification
                                            .CustomerAlertPreferences.SubscriptionInput
                                    input,
                    HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "subscribePush(com.gokulsweets.restaurant.customer.notification.CustomerAlertPreferences.SubscriptionInput,HttpServletRequest)");
        try {
            var environment = alertEnvironment();
            requireTrustedMutation(request);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            alerts.subscribe(
                                    environment.name(),
                                    requiredSubject(request, environment),
                                    cookie(request),
                                    input));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "subscribePush(com.gokulsweets.restaurant.customer.notification.CustomerAlertPreferences.SubscriptionInput,HttpServletRequest)");
        }
    }

    /**
     * Unsubscribes push.
     *
     * @param id the id
     * @param request the request
     * @return the unsubscribe push result
     */
    @DeleteMapping("/push-subscriptions/{id}")
    public ResponseEntity<Void> unsubscribePush(@PathVariable UUID id, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "unsubscribePush(UUID,HttpServletRequest)");
        try {
            // Turning delivery off remains possible if the alert rollout is disabled.
            var environment = enabledEnvironment();
            requireTrustedMutation(request);
            alerts.unsubscribe(environment.name(), requiredSubject(request, environment), id);
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "unsubscribePush(UUID,HttpServletRequest)");
        }
    }

    /**
     * Alerts environment.
     *
     * @return the alert environment result
     */
    private ConsentEnvironment alertEnvironment() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerIdentityController.class, "alertEnvironment()");
        try {
            var environment = enabledEnvironment();
            if (!alerts.enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return environment;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "alertEnvironment()");
        }
    }

    /**
     * Notifications environment.
     *
     * @return the notification environment result
     */
    private ConsentEnvironment notificationEnvironment() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerIdentityController.class, "notificationEnvironment()");
        try {
            var environment = enabledEnvironment();
            if (!notifications.enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return environment;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "notificationEnvironment()");
        }
    }

    /**
     * Consents environment.
     *
     * @return the consent environment result
     */
    private ConsentEnvironment consentEnvironment() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerIdentityController.class, "consentEnvironment()");
        try {
            var environment = enabledEnvironment();
            var policy = settings.getProperty("gokul.consent.policy-version", "");
            if (!features.isCustomerConsentControls() || !policy.matches("[A-Za-z0-9._-]{1,40}")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            return environment;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "consentEnvironment()");
        }
    }

    /**
     * Requireds subject.
     *
     * @param request the request
     * @param environment the environment
     * @return the required subject result
     */
    private UUID requiredSubject(HttpServletRequest request, ConsentEnvironment environment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "requiredSubject(HttpServletRequest,ConsentEnvironment)");
        try {
            // An allowed browser Origin prevents unrelated sites reading authenticated data.
            if (!clientConnection.resolve(request).secure()
                    || !cors.effectiveAllowedOrigins(settings)
                            .contains(request.getHeader(HttpHeaders.ORIGIN))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            return sessions.subject(environment, cookie(request), Instant.now())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "requiredSubject(HttpServletRequest,ConsentEnvironment)");
        }
    }

    /**
     * Handles {@code POST /api/customer/identity/logout} for customer identity.
     *
     * @param request the request supplied to this method
     * @return the value of {@code
     *     ResponseEntity.noContent().cacheControl(CacheControl.noStore()).header(HttpHeaders.SET_COOKIE,
     *     expired.toString()).build()}
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerIdentityController.class, "logout(HttpServletRequest)");
        try {
            var environment = enabledEnvironment();
            requireTrustedMutation(request);
            sessions.revoke(environment, cookie(request), Instant.now());
            var expired =
                    ResponseCookie.from(COOKIE, "")
                            .httpOnly(true)
                            .secure(true)
                            .sameSite("Strict")
                            .path("/")
                            .maxAge(Duration.ZERO)
                            .build();
            return ResponseEntity.noContent()
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.SET_COOKIE, expired.toString())
                    .build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "logout(HttpServletRequest)");
        }
    }

    /**
     * Enableds environment.
     *
     * @return the enabled environment result
     */
    private ConsentEnvironment enabledEnvironment() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerIdentityController.class, "enabledEnvironment()");
        try {
            if (!features.isCustomerOtpIdentity()
                    || !settings.getProperty(
                            "gokul.environment-isolation.enabled", Boolean.class, false)
                    || !settings.getProperty(
                            "gokul.web.environment-cors-enabled", Boolean.class, false)
                    || !settings.getProperty(
                            "gokul.identity.provider-abuse-controls-verified",
                            Boolean.class,
                            false)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            return switch (settings.getProperty("gokul.environment-isolation.environment", "")) {
                case "DEV" -> ConsentEnvironment.DEV;
                case "PROD" -> ConsentEnvironment.PROD;
                default -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
            };
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "enabledEnvironment()");
        }
    }

    /**
     * Requires trusted mutation.
     *
     * @param request the request
     * @return the require trusted mutation result
     */
    private String requireTrustedMutation(HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class,
                        "requireTrustedMutation(HttpServletRequest)");
        try {
            var connection = clientConnection.resolve(request);
            if (!connection.secure()
                    || !cors.effectiveAllowedOrigins(settings)
                            .contains(request.getHeader(HttpHeaders.ORIGIN))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            return connection.sourceAddress();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "requireTrustedMutation(HttpServletRequest)");
        }
    }

    /**
     * Returns the requested cookie value, or null when it is absent; the single-argument overload
     * reads the identity cookie.
     *
     * @param request the request supplied to this method
     * @return the value of {@code cookie(request, COOKIE)}
     */
    private static String cookie(HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerIdentityController.class, "cookie(HttpServletRequest)");
        try {
            return cookie(request, COOKIE);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "cookie(HttpServletRequest)");
        }
    }

    /**
     * Returns the requested cookie value, or null when it is absent; the single-argument overload
     * reads the identity cookie.
     *
     * @param request the request supplied to this method
     * @param name the name supplied to this method
     * @return the {@code String} result
     */
    private static String cookie(HttpServletRequest request, String name) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIdentityController.class, "cookie(HttpServletRequest,String)");
        try {
            if (request.getCookies() == null) return null;
            return Arrays.stream(request.getCookies())
                    .filter(value -> name.equals(value.getName()))
                    .map(jakarta.servlet.http.Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIdentityController.class,
                    "cookie(HttpServletRequest,String)");
        }
    }

    /**
     * Immutable exchange request data contract.
     *
     * @param accessToken the access token
     */
    public record ExchangeRequest(String accessToken) {}

    /**
     * Returns limited information for customer identity.
     *
     * @return the value of {@code
     *     ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).cacheControl(CacheControl.noStore()).build()}
     */
    @ExceptionHandler(IdentityExchangeRateLimiter.Limited.class)
    public ResponseEntity<Void> limited() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerIdentityController.class, "limited()");
        try {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .cacheControl(CacheControl.noStore())
                    .build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerIdentityController.class, "limited()");
        }
    }

    /**
     * Returns unavailable information for customer identity.
     *
     * @return the value of {@code
     *     ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore()).build()}
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Void> unavailable() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerIdentityController.class, "unavailable()");
        try {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .cacheControl(CacheControl.noStore())
                    .build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerIdentityController.class, "unavailable()");
        }
    }
}
