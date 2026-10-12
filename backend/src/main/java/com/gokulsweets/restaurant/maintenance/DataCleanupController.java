package com.gokulsweets.restaurant.maintenance;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for data cleanup operations. */
@RestController
@RequestMapping("/api/admin/data-cleanup")
@RequiredArgsConstructor
public class DataCleanupController {

    private final StaffAuthorizationService staff;

    private final DataCleanupService cleanup;

    /**
     * Requires the current staff account to have the owner role and returns its identifier.
     *
     * @return the value of {@code actor.getId()}
     * @throws AccessDeniedException when the method rejects the request with {@code Only the owner
     *     may configure or run data cleanup.}
     */
    private long owner() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DataCleanupController.class, "owner()");
        try {
            var actor = staff.getCurrentStaff();
            if (!"OWNER_ADMIN".equals(actor.getRole().getName()))
                throw new AccessDeniedException(
                        "Only the owner may configure or run data cleanup.");
            return actor.getId();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, DataCleanupController.class, "owner()");
        }
    }

    /**
     * Handles {@code GET /api/admin/data-cleanup} for data cleanup.
     *
     * @return the value of {@code
     *     ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cleanup.view())}
     */
    @GetMapping
    public ResponseEntity<DataCleanupService.View> view() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DataCleanupController.class, "view()");
        try {
            owner();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cleanup.view());
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, DataCleanupController.class, "view()");
        }
    }

    /**
     * Handles {@code PUT /api/admin/data-cleanup} for data cleanup.
     *
     * @param config the config supplied to this method
     * @return the value of {@code cleanup.save(config, owner())}
     */
    @PutMapping
    public DataCleanupService.View save(@RequestBody DataCleanupService.Config config) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DataCleanupController.class, "save(DataCleanupService.Config)");
        try {
            return cleanup.save(config, owner());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DataCleanupController.class,
                    "save(DataCleanupService.Config)");
        }
    }

    /**
     * Handles {@code POST /api/admin/data-cleanup/preview} for data cleanup.
     *
     * @return the value of {@code cleanup.preview()}
     */
    @PostMapping("/preview")
    public DataCleanupService.Preview preview() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DataCleanupController.class, "preview()");
        try {
            owner();
            return cleanup.preview();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DataCleanupController.class, "preview()");
        }
    }

    /**
     * Immutable run input data contract.
     *
     * @param revision the revision
     */
    public record RunInput(long revision) {}

    /**
     * Handles {@code POST /api/admin/data-cleanup/run} for data cleanup.
     *
     * @param input the input supplied to this method
     * @return the value of {@code cleanup.run(owner(), input.revision())}
     */
    @PostMapping("/run")
    public DataCleanupService.View run(@RequestBody RunInput input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DataCleanupController.class, "run(RunInput)");
        try {
            return cleanup.run(owner(), input.revision());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DataCleanupController.class, "run(RunInput)");
        }
    }
}
