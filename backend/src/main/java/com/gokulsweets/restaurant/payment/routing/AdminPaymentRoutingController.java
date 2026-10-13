package com.gokulsweets.restaurant.payment.routing;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Owner-only routing endpoints protected by the existing staff session and CSRF pipeline. */
@RestController
@RequestMapping("/api/admin/payment-providers")
@RequiredArgsConstructor
public class AdminPaymentRoutingController {
    private final PaymentRoutingService routing;

    /**
     * Validated switch request.
     *
     * @param provider the requested route
     * @param expectedRevision the owner's observed revision
     * @param reason the audit reason
     */
    public record SwitchRequest(
            @NotNull PaymentProviderType provider,
            @NotNull @PositiveOrZero Long expectedRevision,
            @NotBlank @Size(max = 300) String reason) {}

    /**
     * Returns configuration readiness and recent switch history.
     *
     * @return a non-cacheable owner workspace
     */
    @GetMapping
    public ResponseEntity<PaymentRoutingService.Workspace> get() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPaymentRoutingController.class, "get()");
        try {

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(routing.workspace());

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminPaymentRoutingController.class, "get()");
        }
    }

    /**
     * Activates a configured provider for new payment attempts.
     *
     * @param request the validated switch request
     * @return the non-cacheable updated workspace
     */
    @PutMapping
    public ResponseEntity<PaymentRoutingService.Workspace> update(
            @Valid @RequestBody SwitchRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPaymentRoutingController.class,
                        "update(@Valid @RequestBody SwitchRequest)");
        try {

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            routing.activate(
                                    request.provider(),
                                    request.expectedRevision(),
                                    request.reason()));

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPaymentRoutingController.class,
                    "update(@Valid @RequestBody SwitchRequest)");
        }
    }
}
