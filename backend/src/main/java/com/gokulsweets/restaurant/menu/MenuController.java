package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP endpoints for menu operations. */
@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    private final MenuCatalogService catalog;

    private final MenuAvailabilityService availability;

    /**
     * Versioneds catalog.
     *
     * @param branchId the branch id
     * @param revision the revision
     * @return the versioned catalog result
     */
    @GetMapping("/catalog/{branchId}/{revision}")
    public org.springframework.http.ResponseEntity<MenuCatalogService.Catalog> versionedCatalog(
            @org.springframework.web.bind.annotation.PathVariable long branchId,
            @org.springframework.web.bind.annotation.PathVariable String revision) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuController.class, "versionedCatalog(long,String)");
        try {
            var snapshot = catalog.get(branchId);
            if (!snapshot.revision().equals(revision))
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "Menu changed. Refresh availability before loading its catalog.");
            // Historical display data is safe to reuse; availability and order acceptance always
            // use live APIs.
            return org.springframework.http.ResponseEntity.ok()
                    .cacheControl(
                            org.springframework.http.CacheControl.maxAge(
                                            java.time.Duration.ofDays(365))
                                    .cachePublic()
                                    .immutable())
                    .body(snapshot);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuController.class,
                    "versionedCatalog(long,String)");
        }
    }

    /**
     * Handles {@code GET /api/menu/catalog} for menu.
     *
     * @param branchId the branch id supplied to this method
     * @param request the request supplied to this method
     * @return the {@code org.springframework.http.ResponseEntity<MenuCatalogService.Catalog>}
     *     result
     */
    @GetMapping("/catalog")
    public org.springframework.http.ResponseEntity<MenuCatalogService.Catalog> catalog(
            @RequestParam long branchId,
            org.springframework.web.context.request.WebRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuController.class,
                        "catalog(long,org.springframework.web.context.request.WebRequest)");
        try {
            var snapshot = catalog.get(branchId);
            String etag = "\"menu-" + branchId + "-" + snapshot.revision() + "\"";
            if (request.checkNotModified(etag))
                return org.springframework.http.ResponseEntity.status(304)
                        .eTag(etag)
                        .cacheControl(org.springframework.http.CacheControl.noCache())
                        .build();
            return org.springframework.http.ResponseEntity.ok()
                    .eTag(etag)
                    .cacheControl(org.springframework.http.CacheControl.noCache())
                    .body(snapshot);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuController.class,
                    "catalog(long,org.springframework.web.context.request.WebRequest)");
        }
    }

    /**
     * Handles {@code GET /api/menu/availability} for menu.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code
     *     org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(availability.get(branchId))}
     */
    @GetMapping("/availability")
    public org.springframework.http.ResponseEntity<MenuAvailabilityService.Availability>
            availability(@RequestParam long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuController.class, "availability(long)");
        try {
            return org.springframework.http.ResponseEntity.ok()
                    .cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(availability.get(branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuController.class, "availability(long)");
        }
    }

    /**
     * Returns menu.
     *
     * @param branchId the branch id
     * @param view the view
     * @return the get menu result
     */
    @GetMapping
    public org.springframework.http.ResponseEntity<?> getMenu(
            @RequestParam Long branchId, @RequestParam(defaultValue = "combined") String view) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuController.class, "getMenu(Long,String)");
        try {
            Object body =
                    switch (view) {
                        case "catalog" -> catalog.get(branchId);
                        case "availability" -> availability.get(branchId);
                        case "combined" -> menuService.getMenu(branchId);
                        default -> throw new IllegalArgumentException("Choose a valid menu view.");
                    };
            return org.springframework.http.ResponseEntity.ok()
                    .cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(body);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuController.class, "getMenu(Long,String)");
        }
    }
}
