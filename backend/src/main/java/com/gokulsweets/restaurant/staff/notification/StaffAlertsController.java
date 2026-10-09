package com.gokulsweets.restaurant.staff.notification;

import com.gokulsweets.restaurant.customer.notification.WebPushProperties;
import com.gokulsweets.restaurant.customer.notification.WebPushTransport;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.security.StaffSessionService;
import com.gokulsweets.restaurant.staff.PermissionName;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** HTTP endpoints for staff alerts operations. */
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
public class StaffAlertsController {

    private final StaffAuthorizationService authorization;

    private final StaffOrderAlerts alerts;

    private final StaffPushSubscriptions subscriptions;

    private final WebPushTransport push;

    private final WebPushProperties pushProperties;

    private final StaffAlertEmail email;

    /**
     * Immutable settings data contract.
     *
     * @param enabled the enabled
     * @param environment the environment
     * @param staffId the staff id
     * @param pushConfigured the push configured
     * @param applicationServerKey the application server key
     * @param deviceActive the device active
     * @param emailConfigured the email configured
     * @param reminderMinutes the reminder minutes
     * @param escalationMinutes the escalation minutes
     * @param emailTestRouting the email test routing
     */
    public record Settings(
            boolean enabled,
            String environment,
            long staffId,
            boolean pushConfigured,
            String applicationServerKey,
            boolean deviceActive,
            boolean emailConfigured,
            int reminderMinutes,
            int escalationMinutes,
            boolean emailTestRouting) {}

    /**
     * Returns staff information for staff alerts.
     *
     * <p>Authorization checks include {@code PermissionName.ORDER_VIEW}.
     *
     * @return the value of {@code authorization.getCurrentStaff().getId()}
     */
    private long staff() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertsController.class, "staff()");
        try {
            authorization.requirePermission(PermissionName.ORDER_VIEW);
            return authorization.getCurrentStaff().getId();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, StaffAlertsController.class, "staff()");
        }
    }

    /** Rejects access when the feature is disabled. */
    private void enabled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertsController.class, "enabled()");
        try {
            if (!alerts.enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffAlertsController.class, "enabled()");
        }
    }

    /**
     * Handles {@code GET /api/admin/notifications/settings} for staff alerts.
     *
     * <p>Delegates to {@code StaffSessionService.cookie(...)}.
     *
     * @param deviceId the device id supplied to this method
     * @param request the request supplied to this method
     * @return the {@code ResponseEntity<Settings>} result
     */
    @GetMapping("/settings")
    public ResponseEntity<Settings> settings(
            @RequestParam(required = false) UUID deviceId, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAlertsController.class, "settings(UUID,HttpServletRequest)");
        try {
            long staffId = staff();
            boolean enabled = alerts.enabled(), configured = enabled && push.configured();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            new Settings(
                                    enabled,
                                    alerts.scope(),
                                    staffId,
                                    configured,
                                    configured ? pushProperties.getPublicKey() : null,
                                    enabled
                                            && subscriptions.live(
                                                    staffId,
                                                    deviceId,
                                                    StaffSessionService.cookie(request)),
                                    enabled && email.recipient(staffId) != null,
                                    alerts.reminderMinutes(),
                                    alerts.escalationMinutes(),
                                    enabled && email.testRouting(staffId)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAlertsController.class,
                    "settings(UUID,HttpServletRequest)");
        }
    }

    /**
     * Handles {@code GET /api/admin/notifications} for staff alerts.
     *
     * @param before the before supplied to this method
     * @param unreadOnly the unread only supplied to this method
     * @param search the search supplied to this method
     * @return the value of {@code
     *     ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(alerts.page(staffId,
     *     before, unreadOnly, search))}
     */
    @GetMapping
    public ResponseEntity<StaffOrderAlerts.Page> page(
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "") String search) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertsController.class, "page(Long,boolean,String)");
        try {
            long staffId = staff();
            enabled();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(alerts.page(staffId, before, unreadOnly, search));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAlertsController.class,
                    "page(Long,boolean,String)");
        }
    }

    /**
     * Handles {@code PUT /api/admin/notifications/{id}/read} for staff alerts.
     *
     * @param id the id supplied to this method
     * @return the value of {@code
     *     ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()}
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertsController.class, "read(long)");
        try {
            long staffId = staff();
            enabled();
            alerts.markRead(staffId, id);
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffAlertsController.class, "read(long)");
        }
    }

    /**
     * Immutable read all data contract.
     *
     * @param throughId the through id
     */
    public record ReadAll(@jakarta.validation.constraints.Positive long throughId) {}

    /**
     * Reads all.
     *
     * @param input the input
     * @return the read all result
     */
    @PutMapping("/read-all")
    public ResponseEntity<Void> readAll(@jakarta.validation.Valid @RequestBody ReadAll input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertsController.class, "readAll(ReadAll)");
        try {
            long staffId = staff();
            enabled();
            alerts.markAllRead(staffId, input.throughId());
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffAlertsController.class, "readAll(ReadAll)");
        }
    }

    /**
     * Handles {@code POST /api/admin/notifications/push-subscriptions} for staff alerts.
     *
     * <p>Delegates to {@code StaffSessionService.cookie(...)}.
     *
     * @param input the input supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code
     *     ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(subscriptions.subscribe(staffId,
     *     StaffSessionService.cookie(request), input))}
     */
    @PostMapping("/push-subscriptions")
    public ResponseEntity<StaffPushSubscriptions.Result> subscribe(
            @RequestBody StaffPushSubscriptions.Input input, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAlertsController.class,
                        "subscribe(StaffPushSubscriptions.Input,HttpServletRequest)");
        try {
            long staffId = staff();
            enabled();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            subscriptions.subscribe(
                                    staffId, StaffSessionService.cookie(request), input));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAlertsController.class,
                    "subscribe(StaffPushSubscriptions.Input,HttpServletRequest)");
        }
    }

    /**
     * Handles {@code DELETE /api/admin/notifications/push-subscriptions/{id}} for staff alerts.
     *
     * @param id the id supplied to this method
     * @return the value of {@code
     *     ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()}
     */
    @DeleteMapping("/push-subscriptions/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertsController.class, "revoke(UUID)");
        try {
            subscriptions.revoke(authorization.getCurrentStaff().getId(), id);
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffAlertsController.class, "revoke(UUID)");
        }
    }
}
