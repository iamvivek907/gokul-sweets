package com.gokulsweets.restaurant.occasion;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for occasion catalogue operations. */
@RestController
@RequiredArgsConstructor
public class OccasionCatalogueController {

    private final OccasionCatalogue catalogue;

    private final StaffAuthorizationService staff;

    private final com.gokulsweets.restaurant.storage.R2StorageService storage;

    /**
     * Handles {@code GET /api/branches/{branchId}/occasion-catalogue} for occasion catalogue.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code catalogue.catalogue(branchId, false)}
     */
    @GetMapping("/api/branches/{branchId}/occasion-catalogue")
    public OccasionCatalogue.Catalogue customer(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionCatalogueController.class, "customer(long)");
        try {
            return catalogue.catalogue(branchId, false);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OccasionCatalogueController.class, "customer(long)");
        }
    }

    /**
     * Handles {@code GET /api/admin/branches/{branchId}/occasion-catalogue} for occasion catalogue.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code catalogue.catalogue(branchId, true)}
     */
    @GetMapping("/api/admin/branches/{branchId}/occasion-catalogue")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public OccasionCatalogue.Catalogue admin(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionCatalogueController.class, "admin(long)");
        try {
            staff.requireBranchAccess(branchId);
            return catalogue.catalogue(branchId, true);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OccasionCatalogueController.class, "admin(long)");
        }
    }

    /**
     * Handles {@code PUT /api/admin/branches/{branchId}/occasion-catalogue/sweets/{productId}} for
     * occasion catalogue.
     *
     * @param branchId the branch id supplied to this method
     * @param productId the product id supplied to this method
     * @param input the input supplied to this method
     */
    @PutMapping("/api/admin/branches/{branchId}/occasion-catalogue/sweets/{productId}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public void sweet(
            @PathVariable long branchId,
            @PathVariable long productId,
            @Valid @RequestBody OccasionCatalogue.SweetSettings input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionCatalogueController.class,
                        "sweet(long,long,OccasionCatalogue.SweetSettings)");
        try {
            staff.requireBranchAccess(branchId);
            catalogue.configureSweet(branchId, productId, input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionCatalogueController.class,
                    "sweet(long,long,OccasionCatalogue.SweetSettings)");
        }
    }

    /**
     * Handles {@code POST /api/admin/branches/{branchId}/occasion-catalogue/boxes} for occasion
     * catalogue.
     *
     * @param branchId the branch id supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code catalogue.saveBox(branchId, input)}
     */
    @PostMapping("/api/admin/branches/{branchId}/occasion-catalogue/boxes")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public OccasionCatalogue.Box box(
            @PathVariable long branchId, @Valid @RequestBody OccasionCatalogue.Box input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionCatalogueController.class, "box(long,OccasionCatalogue.Box)");
        try {
            staff.requireBranchAccess(branchId);
            return catalogue.saveBox(branchId, input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionCatalogueController.class,
                    "box(long,OccasionCatalogue.Box)");
        }
    }

    /**
     * Handles {@code PUT /api/admin/branches/{branchId}/occasion-catalogue/branding} for occasion
     * catalogue.
     *
     * @param branchId the branch id supplied to this method
     * @param input the input supplied to this method
     */
    @PutMapping("/api/admin/branches/{branchId}/occasion-catalogue/branding")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public void branding(
            @PathVariable long branchId, @Valid @RequestBody OccasionCatalogue.Branding input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionCatalogueController.class,
                        "branding(long,OccasionCatalogue.Branding)");
        try {
            staff.requireBranchAccess(branchId);
            catalogue.saveBranding(branchId, input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionCatalogueController.class,
                    "branding(long,OccasionCatalogue.Branding)");
        }
    }

    /**
     * Handles {@code POST /api/admin/branches/{branchId}/occasion-catalogue/photos} for occasion
     * catalogue.
     *
     * <p>Delegates to {@code storage.uploadCampaignMedia(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @param image the image supplied to this method
     * @return the {@code java.util.Map<String, String>} result
     */
    @PostMapping(
            value = "/api/admin/branches/{branchId}/occasion-catalogue/photos",
            consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public java.util.Map<String, String> photo(
            @PathVariable long branchId,
            @RequestParam("image") org.springframework.web.multipart.MultipartFile image) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionCatalogueController.class,
                        "photo(long,org.springframework.web.multipart.MultipartFile)");
        try {
            staff.requireBranchAccess(branchId);
            catalogue.enabled();
            return java.util.Map.of(
                    "url", storage.uploadCampaignMedia(branchId, image, true).url());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionCatalogueController.class,
                    "photo(long,org.springframework.web.multipart.MultipartFile)");
        }
    }
}
