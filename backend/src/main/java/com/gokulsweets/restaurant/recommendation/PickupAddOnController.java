package com.gokulsweets.restaurant.recommendation;

import com.gokulsweets.restaurant.customer.identity.VerifiedOrderAccess;
import com.gokulsweets.restaurant.inventory.dto.CustomerInventoryCheckRequest;
import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for pickup add on operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/menu/pickup-addons")
public class PickupAddOnController {

    private final PickupAddOnService service;

    private final VerifiedOrderAccess access;

    /**
     * Returns owned information for pickup add on.
     *
     * @param number the number supplied to this method
     * @param request the request supplied to this method
     * @return the {@code String} result
     */
    private String owned(String number, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupAddOnController.class, "owned(String,HttpServletRequest)");
        try {
            if (number == null) return null;
            number = number.trim().toUpperCase(java.util.Locale.ROOT);
            access.requireOrder(number, request);
            return number;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupAddOnController.class,
                    "owned(String,HttpServletRequest)");
        }
    }

    /**
     * Handles {@code POST /api/menu/pickup-addons} for pickup add on.
     *
     * <p>Delegates to {@code service.recommend(...)}.
     *
     * @param browse the browse supplied to this method
     * @param branchId the branch id supplied to this method
     * @param pickupSlotId the pickup slot id supplied to this method
     * @param pickupType the pickup type supplied to this method
     * @param orderNumber the order number supplied to this method
     * @param body the body supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code service.recommend(branchId, body, owned(orderNumber, request),
     *     pickupSlotId, pickupType, browse)}
     */
    @PostMapping
    public List<PickupAddOnService.Suggestion> recommend(
            @RequestParam(defaultValue = "false") boolean browse,
            @RequestParam long branchId,
            @RequestParam(required = false) Long pickupSlotId,
            @RequestParam(required = false)
                    com.gokulsweets.restaurant.order.enums.PickupType pickupType,
            @RequestParam(required = false) String orderNumber,
            @Valid @RequestBody CustomerInventoryCheckRequest body,
            HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PickupAddOnController.class,
                        "recommend(boolean,long,Long,com.gokulsweets.restaurant.order.enums.PickupType,String,CustomerInventoryCheckRequest,HttpServletRequest)");
        try {
            return service.recommend(
                    branchId, body, owned(orderNumber, request), pickupSlotId, pickupType, browse);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupAddOnController.class,
                    "recommend(boolean,long,Long,com.gokulsweets.restaurant.order.enums.PickupType,String,CustomerInventoryCheckRequest,HttpServletRequest)");
        }
    }

    /**
     * Handles {@code POST /api/menu/pickup-addons/check} for pickup add on.
     *
     * <p>Delegates to {@code service.check(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @param orderNumber the order number supplied to this method
     * @param body the body supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code service.check(branchId, body, owned(orderNumber, request))}
     */
    @PostMapping("/check")
    public PickupAddOnService.Availability check(
            @RequestParam long branchId,
            @RequestParam(required = false) String orderNumber,
            @Valid @RequestBody CustomerInventoryCheckRequest body,
            HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PickupAddOnController.class,
                        "check(long,String,CustomerInventoryCheckRequest,HttpServletRequest)");
        try {
            return service.check(branchId, body, owned(orderNumber, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupAddOnController.class,
                    "check(long,String,CustomerInventoryCheckRequest,HttpServletRequest)");
        }
    }
}
