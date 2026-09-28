package com.gokulsweets.restaurant.delivery;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/branches/{branchId}/delivery-dispatch")
public class AdminDeliveryDispatchController {
    private final DeliveryDispatchPilotService pilot;

    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ResponseEntity<List<DeliveryDispatchPilotService.BoardRow>> board(@PathVariable long branchId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(pilot.board(branchId));
    }
    @GetMapping("/riders")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public List<DeliveryDispatchPilotService.Rider> riders(@PathVariable long branchId) {
        return pilot.riders(branchId);
    }
    @PostMapping("/riders")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public DeliveryDispatchPilotService.Rider rider(@PathVariable long branchId, @RequestBody RiderRequest input) {
        return pilot.addRider(branchId, input.name());
    }
    @PutMapping("/riders/{riderId}/windows/{windowId}")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public void availability(@PathVariable long branchId, @PathVariable long riderId,
                             @PathVariable long windowId, @RequestBody AvailabilityRequest input) {
        pilot.availability(branchId, riderId, windowId, input.available());
    }
    @PostMapping("/orders/{orderId}/assign")
    @PreAuthorize("hasAuthority('ORDER_DISPATCH_DELIVERY')")
    public DeliveryDispatchPilotService.Assignment assign(@PathVariable long branchId,
            @PathVariable long orderId, @RequestBody AssignmentRequest input) {
        return pilot.assign(branchId, orderId, input.riderId());
    }
    @PostMapping("/orders/{orderId}/exception")
    @PreAuthorize("hasAuthority('ORDER_DISPATCH_DELIVERY')")
    public void exception(@PathVariable long branchId, @PathVariable long orderId,
                          @RequestBody ExceptionRequest input) {
        pilot.exception(branchId, orderId, input.reason(), input.detail(), input.customerContacted());
    }
    @PostMapping("/orders/{orderId}/outcome")
    @PreAuthorize("hasAuthority('ORDER_CONFIRM_DELIVERY')")
    public void complete(@PathVariable long branchId, @PathVariable long orderId, @RequestBody OutcomeRequest input) {
        pilot.complete(branchId, orderId, input.actualJourneyCost(), input.outcome());
    }
    public record RiderRequest(String name) {}
    public record AvailabilityRequest(boolean available) {}
    public record AssignmentRequest(long riderId) {}
    public record ExceptionRequest(String reason, String detail, boolean customerContacted) {}
    public record OutcomeRequest(BigDecimal actualJourneyCost, String outcome) {}
}
