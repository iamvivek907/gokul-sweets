package com.gokulsweets.restaurant.staff.notification;

import com.gokulsweets.restaurant.customer.notification.WebPushProperties;
import com.gokulsweets.restaurant.customer.notification.WebPushTransport;
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

@RestController @RequestMapping("/api/admin/notifications") @RequiredArgsConstructor
public class StaffAlertsController {
    private final StaffAuthorizationService authorization;
    private final StaffOrderAlerts alerts;
    private final StaffPushSubscriptions subscriptions;
    private final WebPushTransport push;
    private final WebPushProperties pushProperties;
    private final StaffAlertEmail email;
    public record Settings(boolean enabled, String environment, long staffId, boolean pushConfigured,
                           String applicationServerKey, boolean deviceActive, boolean emailConfigured,
                           int reminderMinutes, int escalationMinutes) {}
    private long staff() {
        authorization.requirePermission(PermissionName.ORDER_VIEW);
        return authorization.getCurrentStaff().getId();
    }
    private void enabled() {if (!alerts.enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    @GetMapping("/settings") public ResponseEntity<Settings> settings(@RequestParam(required = false) UUID deviceId, HttpServletRequest request) {
        long staffId = staff(); boolean enabled = alerts.enabled(), configured = enabled && push.configured();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Settings(enabled, alerts.scope(), staffId, configured,
                configured ? pushProperties.getPublicKey() : null, enabled && subscriptions.live(staffId, deviceId, StaffSessionService.cookie(request)),
                enabled && email.recipient(staffId) != null, alerts.reminderMinutes(), alerts.escalationMinutes()));
    }
    @GetMapping public ResponseEntity<StaffOrderAlerts.Page> page(@RequestParam(required = false) Long before) {
        long staffId = staff(); enabled();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(alerts.page(staffId, before));
    }
    @PutMapping("/{id}/read") public ResponseEntity<Void> read(@PathVariable long id) {
        long staffId = staff(); enabled(); alerts.markRead(staffId, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/push-subscriptions") public ResponseEntity<StaffPushSubscriptions.Result> subscribe(@RequestBody StaffPushSubscriptions.Input input, HttpServletRequest request) {
        long staffId = staff(); enabled();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(subscriptions.subscribe(staffId, StaffSessionService.cookie(request), input));
    }
    // Revocation remains possible while the feature flag is OFF.
    @DeleteMapping("/push-subscriptions/{id}") public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        subscriptions.revoke(authorization.getCurrentStaff().getId(), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
