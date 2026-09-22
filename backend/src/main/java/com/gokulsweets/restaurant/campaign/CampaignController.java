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
    public List<HomepageCampaign> active() {
        return features.isHomepageCampaigns() ? service.active() : List.of();
    }

    @GetMapping("/api/admin/homepage-campaigns")
    public List<HomepageCampaign> list() {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return repository.findAllByOrderByDisplayOrderAscIdAsc();
    }

    @PostMapping("/api/admin/homepage-campaigns")
    public HomepageCampaign create(@Valid @RequestBody CampaignRequest request) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return service.save(null, request);
    }

    @PutMapping("/api/admin/homepage-campaigns/{id}")
    public HomepageCampaign update(@PathVariable Long id, @Valid @RequestBody CampaignRequest request) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return service.save(id, request);
    }

    @PostMapping(value = "/api/admin/homepage-campaigns/{id}/media", consumes = "multipart/form-data")
    public HomepageCampaign upload(@PathVariable Long id, @RequestParam MultipartFile file,
                                   @RequestParam(defaultValue = "false") boolean fallback) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return service.upload(id, file, fallback);
    }

    @DeleteMapping("/api/admin/homepage-campaigns/{id}/media")
    public HomepageCampaign remove(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean fallback) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return service.removeMedia(id, fallback);
    }
}
