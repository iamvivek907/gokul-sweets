package com.gokulsweets.restaurant.order.controller;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.service.KitchenPlanningService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.time.*;

/** HTTP endpoints for kitchen planning operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/orders/planning")
public class KitchenPlanningController {

    private final KitchenPlanningService planning;

    /**
     * Handles {@code GET /api/admin/orders/planning/alerts} for kitchen planning.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code planning.alerts(branchId)}
     */
    @GetMapping("/alerts")
    public KitchenPlanningService.AlertCounts alerts(@RequestParam long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KitchenPlanningController.class, "alerts(long)");
        try {
            return planning.alerts(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, KitchenPlanningController.class, "alerts(long)");
        }
    }

    /**
     * Handles {@code GET /api/admin/orders/planning} for kitchen planning.
     *
     * @param branchId the branch id supplied to this method
     * @param filter the filter supplied to this method
     * @param date the date supplied to this method
     * @param start the start supplied to this method
     * @param page the page supplied to this method
     * @return the value of {@code planning.get(branchId, filter, date, start, page)}
     */
    @GetMapping
    public KitchenPlanningService.Plan get(
            @RequestParam long branchId,
            @RequestParam(defaultValue = "ALL") KitchenPlanningService.Filter filter,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) LocalTime start,
            @RequestParam(defaultValue = "0") int page) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        KitchenPlanningController.class,
                        "get(long,KitchenPlanningService.Filter,LocalDate,LocalTime,int)");
        try {
            return planning.get(branchId, filter, date, start, page);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    KitchenPlanningController.class,
                    "get(long,KitchenPlanningService.Filter,LocalDate,LocalTime,int)");
        }
    }
}
