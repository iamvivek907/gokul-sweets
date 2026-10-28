package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public bounded reads and branch-authorized owner edits. */
@RestController
@RequiredArgsConstructor
public class MenuDetailsController {
    private final MenuDetailsService details;

    /** Reads optional public menu facts. */
    @GetMapping("/api/menu/details")
    public List<MenuDetailsService.Details> read(
            @RequestParam long branchId, @RequestParam List<Long> productIds) {
        long started = MethodTiming.start(MenuDetailsController.class, "read(long,List<Long>)");
        try {
            return details.read(branchId, productIds);
        } finally {
            MethodTiming.finish(started, MenuDetailsController.class, "read(long,List<Long>)");
        }
    }

    /** Reads authorized editable dish facts. */
    @GetMapping("/api/admin/branches/{branchId}/menu/products/{productId}/details")
    public MenuDetailsService.Details edit(
            @PathVariable long branchId, @PathVariable long productId) {
        long started = MethodTiming.start(MenuDetailsController.class, "edit(long,long)");
        try {
            return details.adminRead(branchId, productId);
        } finally {
            MethodTiming.finish(started, MenuDetailsController.class, "edit(long,long)");
        }
    }

    /** Validates and saves a versioned owner edit. */
    @PutMapping("/api/admin/branches/{branchId}/menu/products/{productId}/details")
    public MenuDetailsService.Details save(
            @PathVariable long branchId,
            @PathVariable long productId,
            @Valid @RequestBody MenuDetailsService.Update value) {
        long started =
                MethodTiming.start(
                        MenuDetailsController.class, "save(long,long,MenuDetailsService.Update)");
        try {
            return details.save(branchId, productId, value);
        } finally {
            MethodTiming.finish(
                    started,
                    MenuDetailsController.class,
                    "save(long,long,MenuDetailsService.Update)");
        }
    }
}
