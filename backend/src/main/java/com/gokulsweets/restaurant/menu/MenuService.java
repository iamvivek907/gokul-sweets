package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.menu.dto.*;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Legacy combined endpoint remains compatible; new clients refresh only the availability overlay.
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuCatalogService catalog;

    private final MenuAvailabilityService availability;

    /**
     * Returns menu.
     *
     * @param branchId the branch id
     * @return the get menu result
     */
    @Transactional(readOnly = true)
    public List<MenuCategoryResponse> getMenu(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuService.class, "getMenu(Long)");
        try {
            if (branchId == null) throw new IllegalArgumentException("Branch ID is required.");
            for (int attempt = 0; attempt < 3; attempt++) {
                var snapshot = catalog.get(branchId);
                var live = availability.get(branchId);
                if (!snapshot.revision().equals(live.revision())) continue;
                var states = new HashMap<Long, MenuAvailabilityService.Item>();
                live.items().forEach(i -> states.put(i.productId(), i));
                return snapshot.categories().stream()
                        .map(
                                c ->
                                        new MenuCategoryResponse(
                                                c.id(),
                                                c.name(),
                                                c.description(),
                                                c.displayOrder(),
                                                c.products().stream()
                                                        .filter(
                                                                p ->
                                                                        live.serviceWindowsEnabled()
                                                                                || states.get(
                                                                                                p
                                                                                                        .id())
                                                                                        .available())
                                                        .map(
                                                                p -> {
                                                                    var state = states.get(p.id());
                                                                    return new MenuProductResponse(
                                                                            p.id(),
                                                                            p.categoryId(),
                                                                            p.categoryName(),
                                                                            p.name(),
                                                                            p.description(),
                                                                            p.price(),
                                                                            p.imageUrl(),
                                                                            state.available(),
                                                                            p.saleMode(),
                                                                            p.minimumWeightGrams(),
                                                                            p.weightStepGrams(),
                                                                            retime(
                                                                                    state
                                                                                            .serviceAvailability(),
                                                                                    live
                                                                                            .observedAt()),
                                                                            p.vegetarian());
                                                                })
                                                        .toList()))
                        .filter(c -> !c.products().isEmpty())
                        .toList();
            }
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Menu is updating. Please try again.");
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, MenuService.class, "getMenu(Long)");
        }
    }

    /**
     * Retimes the operation.
     *
     * @param status the status
     * @param observedAt the observed at
     * @return the retime result
     */
    private MenuServiceWindows.Status retime(
            MenuServiceWindows.Status status, java.time.Instant observedAt) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuService.class, "retime(MenuServiceWindows.Status,java.time.Instant)");
        try {
            if (status == null || observedAt == null) return status;
            return new MenuServiceWindows.Status(
                    status.available(),
                    status.code(),
                    status.message(),
                    status.nextChangeAt(),
                    observedAt);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuService.class,
                    "retime(MenuServiceWindows.Status,java.time.Instant)");
        }
    }
}
