package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for mobile menu options operations. */
@RestController
@RequiredArgsConstructor
public class MobileMenuOptionsController {

    private final MobileMenuOptionsService options;

    /**
     * Reads the operation.
     *
     * @param branchId the branch id
     * @return the read result
     */
    @GetMapping("/api/menu/portion-groups")
    public ResponseEntity<MobileMenuOptionsService.Snapshot> read(@RequestParam long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MobileMenuOptionsController.class, "read(long)");
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(options.publicRead(branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MobileMenuOptionsController.class, "read(long)");
        }
    }

    /**
     * Admins read.
     *
     * @param branchId the branch id
     * @return the admin read result
     */
    @GetMapping("/api/admin/branches/{branchId}/menu/portion-groups")
    public MobileMenuOptionsService.Snapshot adminRead(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MobileMenuOptionsController.class, "adminRead(long)");
        try {
            return options.adminRead(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MobileMenuOptionsController.class,
                    "adminRead(long)");
        }
    }

    /**
     * Saves the operation.
     *
     * @param branchId the branch id
     * @param input the input
     * @return the save result
     */
    @PutMapping("/api/admin/branches/{branchId}/menu/portion-groups")
    public MobileMenuOptionsService.Snapshot save(
            @PathVariable long branchId, @Valid @RequestBody MobileMenuOptionsService.Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MobileMenuOptionsController.class,
                        "save(long,MobileMenuOptionsService.Input)");
        try {
            return options.save(branchId, input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MobileMenuOptionsController.class,
                    "save(long,MobileMenuOptionsService.Input)");
        }
    }

    /**
     * Pages the operation.
     *
     * @param branchId the branch id
     * @param search the search
     * @param page the page
     * @return the page result
     */
    @GetMapping("/api/admin/branches/{branchId}/menu/workspace/groups")
    public MobileMenuOptionsService.GroupPage page(
            @PathVariable long branchId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MobileMenuOptionsController.class, "page(long,String,int)");
        try {
            return options.page(branchId, search, page);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MobileMenuOptionsController.class,
                    "page(long,String,int)");
        }
    }

    /** Immutable one data contract. */
    public record One(
            @jakarta.validation.constraints.Min(0) long version,
            @jakarta.validation.constraints.NotNull @Valid MobileMenuOptionsService.Group group) {}

    /**
     * Ones the operation.
     *
     * @param branchId the branch id
     * @param input the input
     * @return the one result
     */
    @PutMapping("/api/admin/branches/{branchId}/menu/workspace/groups")
    public java.util.Map<String, Long> one(
            @PathVariable long branchId, @Valid @RequestBody One input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MobileMenuOptionsController.class, "one(long,One)");
        try {
            return java.util.Map.of(
                    "version", options.saveOne(branchId, input.version(), input.group(), false));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MobileMenuOptionsController.class, "one(long,One)");
        }
    }

    /**
     * Removes the operation.
     *
     * @param branchId the branch id
     * @param key the key
     * @param version the version
     */
    @DeleteMapping("/api/admin/branches/{branchId}/menu/workspace/groups/{key}")
    public void remove(
            @PathVariable long branchId, @PathVariable String key, @RequestParam long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MobileMenuOptionsController.class, "remove(long,String,long)");
        try {
            if (!key.matches("[A-Za-z0-9-]{1,40}"))
                throw new IllegalArgumentException("Invalid group key.");
            options.saveOne(
                    branchId,
                    version,
                    new MobileMenuOptionsService.Group(key, "", java.util.List.of()),
                    true);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MobileMenuOptionsController.class,
                    "remove(long,String,long)");
        }
    }

    /**
     * Fors product.
     *
     * @param branchId the branch id
     * @param id the id
     * @return the for product result
     */
    @GetMapping("/api/admin/branches/{branchId}/menu/workspace/groups/product/{id}")
    public MobileMenuOptionsService.GroupMatch forProduct(
            @PathVariable long branchId, @PathVariable long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MobileMenuOptionsController.class, "forProduct(long,long)");
        try {
            return options.forProduct(branchId, id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MobileMenuOptionsController.class,
                    "forProduct(long,long)");
        }
    }
}
