package com.gokulsweets.restaurant.customer.consent;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HTTP endpoints for admin privacy request operations. */
@RestController
@RequestMapping("/api/admin/privacy-requests")
@RequiredArgsConstructor
public class AdminPrivacyRequestController {

    private final AdminPrivacyRequestQueue queue;

    /**
     * Lists the operation.
     *
     * @param page the page
     * @return the list result
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PRIVACY_REQUEST_VIEW')")
    public ResponseEntity<List<AdminPrivacyRequestQueue.Entry>> list(
            @RequestParam(defaultValue = "0") int page) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPrivacyRequestController.class, "list(int)");
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queue.view(page));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminPrivacyRequestController.class, "list(int)");
        }
    }

    /**
     * Triages the operation.
     *
     * @param requestId the request id
     * @param choice the choice
     * @return the triage result
     */
    @PatchMapping("/{requestId}/triage")
    @PreAuthorize("hasAuthority('PRIVACY_REQUEST_VIEW')")
    public ResponseEntity<AdminPrivacyRequestQueue.Entry> triage(
            @PathVariable long requestId, @RequestBody TriageChoice choice) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPrivacyRequestController.class, "triage(long,TriageChoice)");
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(queue.triage(requestId, choice == null ? null : choice.state()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPrivacyRequestController.class,
                    "triage(long,TriageChoice)");
        }
    }

    /** Immutable triage choice data contract. */
    public record TriageChoice(PrivacyReviewState state) {}
}
