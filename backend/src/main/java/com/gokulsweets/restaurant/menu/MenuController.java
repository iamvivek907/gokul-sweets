package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.menu.dto.MenuCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;
    private final MenuCatalogService catalog;
    private final MenuAvailabilityService availability;

    @GetMapping("/catalog/{branchId}/{revision}")
    public org.springframework.http.ResponseEntity<MenuCatalogService.Catalog> versionedCatalog(@org.springframework.web.bind.annotation.PathVariable long branchId,@org.springframework.web.bind.annotation.PathVariable String revision) {
        var snapshot=catalog.get(branchId);
        if(!snapshot.revision().equals(revision))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Menu changed. Refresh availability before loading its catalog.");
        // Historical display data is safe to reuse; availability and order acceptance always use live APIs.
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable()).body(snapshot);
    }

    @GetMapping("/catalog")
    public org.springframework.http.ResponseEntity<MenuCatalogService.Catalog> catalog(@RequestParam long branchId, org.springframework.web.context.request.WebRequest request) {
        var snapshot=catalog.get(branchId);
        String etag="\"menu-"+branchId+"-"+snapshot.revision()+"\"";
        if(request.checkNotModified(etag))return org.springframework.http.ResponseEntity.status(304).eTag(etag).cacheControl(org.springframework.http.CacheControl.noCache()).build();
        return org.springframework.http.ResponseEntity.ok().eTag(etag).cacheControl(org.springframework.http.CacheControl.noCache()).body(snapshot);
    }

    @GetMapping("/availability")
    public org.springframework.http.ResponseEntity<MenuAvailabilityService.Availability> availability(@RequestParam long branchId) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(availability.get(branchId));
    }

    @GetMapping
    public org.springframework.http.ResponseEntity<?> getMenu(@RequestParam Long branchId, @RequestParam(defaultValue="combined") String view) {
        Object body=switch(view) {
            case "catalog" -> catalog.get(branchId);
            case "availability" -> availability.get(branchId);
            case "combined" -> menuService.getMenu(branchId);
            default -> throw new IllegalArgumentException("Choose a valid menu view.");
        };
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(body);
    }
}