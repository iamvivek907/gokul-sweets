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
     * Owneds the operation.
     *
     * @param number the number
     * @param request the request
     * @return the owned result
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
     * Recommends the operation.
     *
     * @param browse the browse
     * @param branchId the branch id
     * @param pickupSlotId the pickup slot id
     * @param pickupType the pickup type
     * @param orderNumber the order number
     * @param body the body
     * @param request the request
     * @return the recommend result
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
     * Checks the operation.
     *
     * @param branchId the branch id
     * @param orderNumber the order number
     * @param body the body
     * @param request the request
     * @return the check result
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
