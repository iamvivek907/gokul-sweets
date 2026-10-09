package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** HTTP endpoints for admin delivery dispatch operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/branches/{branchId}/delivery-dispatch")
public class AdminDeliveryDispatchController {

    private final DeliveryDispatchPilotService pilot;

    /**
     * Handles {@code GET /api/admin/branches/{branchId}/delivery-dispatch} for admin delivery
     * dispatch.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code
     *     ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(pilot.board(branchId))}
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ResponseEntity<List<DeliveryDispatchPilotService.BoardRow>> board(
            @PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminDeliveryDispatchController.class, "board(long)");
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(pilot.board(branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminDeliveryDispatchController.class,
                    "board(long)");
        }
    }

    /**
     * Handles {@code GET /api/admin/branches/{branchId}/delivery-dispatch/riders} for admin
     * delivery dispatch.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code pilot.riders(branchId)}
     */
    @GetMapping("/riders")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public List<DeliveryDispatchPilotService.Rider> riders(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminDeliveryDispatchController.class, "riders(long)");
        try {
            return pilot.riders(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminDeliveryDispatchController.class,
                    "riders(long)");
        }
    }

    /**
     * Handles {@code POST /api/admin/branches/{branchId}/delivery-dispatch/riders} for admin
     * delivery dispatch.
     *
     * @param branchId the branch id supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code pilot.addRider(branchId, input.name())}
     */
    @PostMapping("/riders")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public DeliveryDispatchPilotService.Rider rider(
            @PathVariable long branchId, @RequestBody RiderRequest input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminDeliveryDispatchController.class, "rider(long,RiderRequest)");
        try {
            return pilot.addRider(branchId, input.name());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminDeliveryDispatchController.class,
                    "rider(long,RiderRequest)");
        }
    }

    /**
     * Handles {@code PUT
     * /api/admin/branches/{branchId}/delivery-dispatch/riders/{riderId}/windows/{windowId}} for
     * admin delivery dispatch.
     *
     * @param branchId the branch id supplied to this method
     * @param riderId the rider id supplied to this method
     * @param windowId the window id supplied to this method
     * @param input the input supplied to this method
     */
    @PutMapping("/riders/{riderId}/windows/{windowId}")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public void availability(
            @PathVariable long branchId,
            @PathVariable long riderId,
            @PathVariable long windowId,
            @RequestBody AvailabilityRequest input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminDeliveryDispatchController.class,
                        "availability(long,long,long,AvailabilityRequest)");
        try {
            pilot.availability(branchId, riderId, windowId, input.available());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminDeliveryDispatchController.class,
                    "availability(long,long,long,AvailabilityRequest)");
        }
    }

    /**
     * Handles {@code POST /api/admin/branches/{branchId}/delivery-dispatch/orders/{orderId}/assign}
     * for admin delivery dispatch.
     *
     * @param branchId the branch id supplied to this method
     * @param orderId the order id supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code pilot.assign(branchId, orderId, input.riderId())}
     */
    @PostMapping("/orders/{orderId}/assign")
    @PreAuthorize("hasAuthority('ORDER_DISPATCH_DELIVERY')")
    public DeliveryDispatchPilotService.Assignment assign(
            @PathVariable long branchId,
            @PathVariable long orderId,
            @RequestBody AssignmentRequest input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminDeliveryDispatchController.class,
                        "assign(long,long,AssignmentRequest)");
        try {
            return pilot.assign(branchId, orderId, input.riderId());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminDeliveryDispatchController.class,
                    "assign(long,long,AssignmentRequest)");
        }
    }

    /**
     * Handles {@code POST
     * /api/admin/branches/{branchId}/delivery-dispatch/orders/{orderId}/exception} for admin
     * delivery dispatch.
     *
     * @param branchId the branch id supplied to this method
     * @param orderId the order id supplied to this method
     * @param input the input supplied to this method
     */
    @PostMapping("/orders/{orderId}/exception")
    @PreAuthorize("hasAuthority('ORDER_DISPATCH_DELIVERY')")
    public void exception(
            @PathVariable long branchId,
            @PathVariable long orderId,
            @RequestBody ExceptionRequest input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminDeliveryDispatchController.class,
                        "exception(long,long,ExceptionRequest)");
        try {
            pilot.exception(
                    branchId, orderId, input.reason(), input.detail(), input.customerContacted());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminDeliveryDispatchController.class,
                    "exception(long,long,ExceptionRequest)");
        }
    }

    /**
     * Handles {@code POST
     * /api/admin/branches/{branchId}/delivery-dispatch/orders/{orderId}/outcome} for admin delivery
     * dispatch.
     *
     * @param branchId the branch id supplied to this method
     * @param orderId the order id supplied to this method
     * @param input the input supplied to this method
     */
    @PostMapping("/orders/{orderId}/outcome")
    @PreAuthorize("hasAuthority('ORDER_CONFIRM_DELIVERY')")
    public void complete(
            @PathVariable long branchId,
            @PathVariable long orderId,
            @RequestBody OutcomeRequest input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminDeliveryDispatchController.class,
                        "complete(long,long,OutcomeRequest)");
        try {
            pilot.complete(branchId, orderId, input.actualJourneyCost(), input.outcome());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminDeliveryDispatchController.class,
                    "complete(long,long,OutcomeRequest)");
        }
    }

    /**
     * Immutable rider request data contract.
     *
     * @param name the name
     */
    public record RiderRequest(String name) {}

    /**
     * Immutable availability request data contract.
     *
     * @param available the available
     */
    public record AvailabilityRequest(boolean available) {}

    /**
     * Immutable assignment request data contract.
     *
     * @param riderId the rider id
     */
    public record AssignmentRequest(long riderId) {}

    /**
     * Immutable exception request data contract.
     *
     * @param reason the reason
     * @param detail the detail
     * @param customerContacted the customer contacted
     */
    public record ExceptionRequest(String reason, String detail, boolean customerContacted) {}

    /**
     * Immutable outcome request data contract.
     *
     * @param actualJourneyCost the actual journey cost
     * @param outcome the outcome
     */
    public record OutcomeRequest(BigDecimal actualJourneyCost, String outcome) {}
}
