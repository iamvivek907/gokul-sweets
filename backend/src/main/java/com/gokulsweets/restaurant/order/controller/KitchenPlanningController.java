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
     * Alertses the operation.
     *
     * @param branchId the branch id
     * @return the alerts result
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
     * Returns the operation.
     *
     * @param branchId the branch id
     * @param filter the filter
     * @param date the date
     * @param start the start
     * @param page the page
     * @return the get result
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
