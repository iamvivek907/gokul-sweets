package com.gokulsweets.restaurant.campaign;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class CampaignController {
    private final CampaignService service;
    private final HomepageCampaignRepository repository;
    private final EnhancementProperties features;
    private final StaffAuthorizationService authorization;

    @GetMapping("/api/storefront/campaigns")
    public List<HomepageCampaign> active(@RequestParam(required = false) Long branchId) {
        return features.isHomepageCampaigns() ? service.active(branchId) : List.of();
    }

    @GetMapping("/api/admin/homepage-campaigns/{id}/publications")
    public List<CampaignPublication> history(@PathVariable Long id) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return service.history(id);
    }

    @PostMapping("/api/admin/homepage-campaigns/{id}/publications/{revision}/restore")
    public HomepageCampaign restore(@PathVariable Long id, @PathVariable Long revision) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return service.rollback(id, revision);
    }

    @GetMapping("/api/admin/homepage-campaigns")
    public List<HomepageCampaign> list() {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return repository.findAllByOrderByDisplayOrderAscIdAsc();
    }

    @PostMapping("/api/admin/homepage-campaigns")
    public HomepageCampaign create(@Valid @RequestBody CampaignRequest request,
                                   @RequestHeader(value = "Idempotency-Key", required = false) java.util.UUID requestId) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return service.create(request, requestId);
    }

    @PutMapping("/api/admin/homepage-campaigns/{id}")
    public HomepageCampaign update(@PathVariable Long id, @Valid @RequestBody CampaignRequest request) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return service.save(id, request);
    }

    @PostMapping(value = "/api/admin/homepage-campaigns/{id}/media", consumes = "multipart/form-data")
    public HomepageCampaign upload(@PathVariable Long id, @RequestParam MultipartFile file,
                                   @RequestParam(defaultValue = "false") boolean fallback,
                                   @RequestHeader(value = "Idempotency-Key", required = false) java.util.UUID requestId) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return service.upload(id, file, fallback, requestId);
    }

    @PostMapping(value = "/api/admin/homepage-campaigns/{id}/mobile-media", consumes = "multipart/form-data")
    public HomepageCampaign uploadMobile(@PathVariable Long id, @RequestParam MultipartFile file,
                @RequestHeader(value = "Idempotency-Key", required = false) java.util.UUID requestId) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return service.uploadMobile(id, file, requestId);
    }

    @DeleteMapping("/api/admin/homepage-campaigns/{id}/mobile-media")
    public HomepageCampaign removeMobile(@PathVariable Long id) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return service.removeMobile(id);
    }

    @DeleteMapping("/api/admin/homepage-campaigns/{id}/media")
    public HomepageCampaign remove(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean fallback) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return service.removeMedia(id, fallback);
    }
}
