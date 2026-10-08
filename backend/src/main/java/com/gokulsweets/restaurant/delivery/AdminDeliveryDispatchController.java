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
     * Boards the operation.
     *
     * @param branchId the branch id
     * @return the board result
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
     * Riderses the operation.
     *
     * @param branchId the branch id
     * @return the riders result
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
     * Riders the operation.
     *
     * @param branchId the branch id
     * @param input the input
     * @return the rider result
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
     * Availability the operation.
     *
     * @param branchId the branch id
     * @param riderId the rider id
     * @param windowId the window id
     * @param input the input
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
     * Assigns the operation.
     *
     * @param branchId the branch id
     * @param orderId the order id
     * @param input the input
     * @return the assign result
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
     * Exceptions the operation.
     *
     * @param branchId the branch id
     * @param orderId the order id
     * @param input the input
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
     * Completes the operation.
     *
     * @param branchId the branch id
     * @param orderId the order id
     * @param input the input
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

    /** Immutable rider request data contract. */
    public record RiderRequest(String name) {}

    /** Immutable availability request data contract. */
    public record AvailabilityRequest(boolean available) {}

    /** Immutable assignment request data contract. */
    public record AssignmentRequest(long riderId) {}

    /** Immutable exception request data contract. */
    public record ExceptionRequest(String reason, String detail, boolean customerContacted) {}

    /** Immutable outcome request data contract. */
    public record OutcomeRequest(BigDecimal actualJourneyCost, String outcome) {}
}
