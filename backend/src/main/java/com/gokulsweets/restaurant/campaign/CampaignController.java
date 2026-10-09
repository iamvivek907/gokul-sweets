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
     * Handles {@code GET /api/storefront/campaigns} for campaign.
     *
     * <p>Delegates to {@code service.active(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code features.isHomepageCampaigns() ? service.active(branchId) :
     *     List.of()}
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
     * Handles {@code GET /api/admin/homepage-campaigns/{id}/publications} for campaign.
     *
     * <p>Authorization checks include {@code PermissionName.MENU_MANAGE}.
     *
     * <p>Delegates to {@code service.history(...)}.
     *
     * @param id the id supplied to this method
     * @return the value of {@code service.history(id)}
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
     * Handles {@code POST /api/admin/homepage-campaigns/{id}/publications/{revision}/restore} for
     * campaign.
     *
     * <p>Authorization checks include {@code PermissionName.MENU_MANAGE}.
     *
     * <p>Delegates to {@code service.rollback(...)}.
     *
     * @param id the id supplied to this method
     * @param revision the revision supplied to this method
     * @param expectedVersion the expected version supplied to this method
     * @return the value of {@code service.rollback(id, revision, expectedVersion)}
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
     * Handles {@code GET /api/admin/homepage-campaigns} for campaign.
     *
     * <p>Authorization checks include {@code PermissionName.MENU_MANAGE}.
     *
     * <p>Delegates to {@code repository.findAllByOrderByDisplayOrderAscIdAsc(...)}.
     *
     * @return the value of {@code repository.findAllByOrderByDisplayOrderAscIdAsc()}
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
     * Handles {@code POST /api/admin/homepage-campaigns} for campaign.
     *
     * <p>Authorization checks include {@code PermissionName.MENU_MANAGE}.
     *
     * <p>Delegates to {@code service.create(...)}.
     *
     * @param request the request supplied to this method
     * @param requestId the request id supplied to this method
     * @return the value of {@code service.create(request, requestId)}
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
     * Handles {@code PUT /api/admin/homepage-campaigns/{id}} for campaign.
     *
     * <p>Authorization checks include {@code PermissionName.MENU_MANAGE}.
     *
     * <p>Delegates to {@code service.save(...)}.
     *
     * @param id the id supplied to this method
     * @param request the request supplied to this method
     * @param expectedVersion the expected version supplied to this method
     * @return the value of {@code service.save(id, request, expectedVersion)}
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
     * Handles {@code POST /api/admin/homepage-campaigns/{id}/media} for campaign.
     *
     * <p>Authorization checks include {@code PermissionName.MENU_MANAGE}.
     *
     * <p>Delegates to {@code service.upload(...)}.
     *
     * @param id the id supplied to this method
     * @param file the file supplied to this method
     * @param fallback the fallback supplied to this method
     * @param requestId the request id supplied to this method
     * @param expectedVersion the expected version supplied to this method
     * @return the value of {@code service.upload(id, file, fallback, requestId, expectedVersion)}
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
     * Handles {@code DELETE /api/admin/homepage-campaigns/{id}/media} for campaign.
     *
     * <p>Authorization checks include {@code PermissionName.MENU_MANAGE}.
     *
     * <p>Delegates to {@code service.removeMedia(...)}.
     *
     * @param id the id supplied to this method
     * @param fallback the fallback supplied to this method
     * @param expectedVersion the expected version supplied to this method
     * @return the value of {@code service.removeMedia(id, fallback, expectedVersion)}
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
