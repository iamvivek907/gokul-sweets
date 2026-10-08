package com.gokulsweets.restaurant.campaign;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** HTTP endpoints for campaign operations. */
@RestController
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService service;

    private final HomepageCampaignRepository repository;

    private final EnhancementProperties features;

    private final StaffAuthorizationService authorization;

    /**
     * Actives the operation.
     *
     * @param branchId the branch id
     * @return the active result
     */
    @GetMapping("/api/storefront/campaigns")
    public List<HomepageCampaign> active(@RequestParam(required = false) Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignController.class, "active(Long)");
        try {
            return features.isHomepageCampaigns() ? service.active(branchId) : List.of();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CampaignController.class, "active(Long)");
        }
    }

    /**
     * History the operation.
     *
     * @param id the id
     * @return the history result
     */
    @GetMapping("/api/admin/homepage-campaigns/{id}/publications")
    public List<CampaignPublication> history(@PathVariable Long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignController.class, "history(Long)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            return service.history(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CampaignController.class, "history(Long)");
        }
    }

    /**
     * Restores the operation.
     *
     * @param id the id
     * @param revision the revision
     * @param expectedVersion the expected version
     * @return the restore result
     */
    @PostMapping("/api/admin/homepage-campaigns/{id}/publications/{revision}/restore")
    public HomepageCampaign restore(
            @PathVariable Long id,
            @PathVariable Long revision,
            @RequestHeader(value = "If-Match", required = false) Long expectedVersion) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignController.class, "restore(Long,Long,Long)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            return service.rollback(id, revision, expectedVersion);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CampaignController.class, "restore(Long,Long,Long)");
        }
    }

    /**
     * Lists the operation.
     *
     * @return the list result
     */
    @GetMapping("/api/admin/homepage-campaigns")
    public List<HomepageCampaign> list() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignController.class, "list()");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            return repository.findAllByOrderByDisplayOrderAscIdAsc();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CampaignController.class, "list()");
        }
    }

    /**
     * Creates the operation.
     *
     * @param request the request
     * @param requestId the request id
     * @return the create result
     */
    @PostMapping("/api/admin/homepage-campaigns")
    public HomepageCampaign create(
            @Valid @RequestBody CampaignRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) java.util.UUID requestId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CampaignController.class, "create(CampaignRequest,java.util.UUID)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            return service.create(request, requestId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CampaignController.class,
                    "create(CampaignRequest,java.util.UUID)");
        }
    }

    /**
     * Updates the operation.
     *
     * @param id the id
     * @param request the request
     * @param expectedVersion the expected version
     * @return the update result
     */
    @PutMapping("/api/admin/homepage-campaigns/{id}")
    public HomepageCampaign update(
            @PathVariable Long id,
            @Valid @RequestBody CampaignRequest request,
            @RequestHeader(value = "If-Match", required = false) Long expectedVersion) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignController.class, "update(Long,CampaignRequest,Long)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            return service.save(id, request, expectedVersion);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CampaignController.class,
                    "update(Long,CampaignRequest,Long)");
        }
    }

    /**
     * Uploads the operation.
     *
     * @param id the id
     * @param file the file
     * @param fallback the fallback
     * @param requestId the request id
     * @param expectedVersion the expected version
     * @return the upload result
     */
    @PostMapping(
            value = "/api/admin/homepage-campaigns/{id}/media",
            consumes = "multipart/form-data")
    public HomepageCampaign upload(
            @PathVariable Long id,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "false") boolean fallback,
            @RequestHeader(value = "Idempotency-Key", required = false) java.util.UUID requestId,
            @RequestHeader(value = "If-Match", required = false) Long expectedVersion) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CampaignController.class,
                        "upload(Long,MultipartFile,boolean,java.util.UUID,Long)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            return service.upload(id, file, fallback, requestId, expectedVersion);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CampaignController.class,
                    "upload(Long,MultipartFile,boolean,java.util.UUID,Long)");
        }
    }

    /**
     * Uploads mobile.
     *
     * @param id the id
     * @param file the file
     * @param requestId the request id
     * @param expectedVersion the expected version
     * @return the upload mobile result
     */
    @PostMapping(
            value = "/api/admin/homepage-campaigns/{id}/mobile-media",
            consumes = "multipart/form-data")
    public HomepageCampaign uploadMobile(
            @PathVariable Long id,
            @RequestParam MultipartFile file,
            @RequestHeader(value = "Idempotency-Key", required = false) java.util.UUID requestId,
            @RequestHeader(value = "If-Match", required = false) Long expectedVersion) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CampaignController.class,
                        "uploadMobile(Long,MultipartFile,java.util.UUID,Long)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            return service.uploadMobile(id, file, requestId, expectedVersion);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CampaignController.class,
                    "uploadMobile(Long,MultipartFile,java.util.UUID,Long)");
        }
    }

    /**
     * Removes mobile.
     *
     * @param id the id
     * @param expectedVersion the expected version
     * @return the remove mobile result
     */
    @DeleteMapping("/api/admin/homepage-campaigns/{id}/mobile-media")
    public HomepageCampaign removeMobile(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) Long expectedVersion) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignController.class, "removeMobile(Long,Long)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            return service.removeMobile(id, expectedVersion);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CampaignController.class, "removeMobile(Long,Long)");
        }
    }

    /**
     * Removes the operation.
     *
     * @param id the id
     * @param fallback the fallback
     * @param expectedVersion the expected version
     * @return the remove result
     */
    @DeleteMapping("/api/admin/homepage-campaigns/{id}/media")
    public HomepageCampaign remove(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean fallback,
            @RequestHeader(value = "If-Match", required = false) Long expectedVersion) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignController.class, "remove(Long,boolean,Long)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            return service.removeMedia(id, fallback, expectedVersion);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CampaignController.class,
                    "remove(Long,boolean,Long)");
        }
    }
}
