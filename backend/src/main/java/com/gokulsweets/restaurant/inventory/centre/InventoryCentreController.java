package com.gokulsweets.restaurant.inventory.centre;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/** HTTP endpoints for inventory centre operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inventory/branches/{branch}/centre")
public class InventoryCentreController {

    private final InventoryCentreJobs jobs;

    /**
     * Handles {@code POST /api/admin/inventory/branches/{branch}/centre/jobs} for inventory centre.
     *
     * @param branch the branch supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code jobs.submit(branch, input)}
     */
    @PostMapping("/jobs")
    public Map<String, Object> submit(
            @PathVariable long branch, @Valid @RequestBody InventoryCentreJobs.Submit input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        InventoryCentreController.class, "submit(long,InventoryCentreJobs.Submit)");
        try {
            return jobs.submit(branch, input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryCentreController.class,
                    "submit(long,InventoryCentreJobs.Submit)");
        }
    }

    /**
     * Handles {@code GET /api/admin/inventory/branches/{branch}/centre/jobs} for inventory centre.
     *
     * @param branch the branch supplied to this method
     * @return the value of {@code jobs.recent(branch)}
     */
    @GetMapping("/jobs")
    public List<Map<String, Object>> recent(@PathVariable long branch) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryCentreController.class, "recent(long)");
        try {
            return jobs.recent(branch);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, InventoryCentreController.class, "recent(long)");
        }
    }

    /**
     * Handles {@code GET /api/admin/inventory/branches/{branch}/centre/jobs/{id}} for inventory
     * centre.
     *
     * @param branch the branch supplied to this method
     * @param id the id supplied to this method
     * @return the value of {@code jobs.summary(branch, id)}
     */
    @GetMapping("/jobs/{id}")
    public Map<String, Object> status(@PathVariable long branch, @PathVariable UUID id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryCentreController.class, "status(long,UUID)");
        try {
            return jobs.summary(branch, id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryCentreController.class,
                    "status(long,UUID)");
        }
    }

    /**
     * Handles {@code GET /api/admin/inventory/branches/{branch}/centre/jobs/{id}/results} for
     * inventory centre.
     *
     * @param branch the branch supplied to this method
     * @param id the id supplied to this method
     * @param page the page supplied to this method
     * @return the value of {@code jobs.results(branch, id, page)}
     */
    @GetMapping("/jobs/{id}/results")
    public List<Map<String, Object>> results(
            @PathVariable long branch,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryCentreController.class, "results(long,UUID,int)");
        try {
            return jobs.results(branch, id, page);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryCentreController.class,
                    "results(long,UUID,int)");
        }
    }
}
