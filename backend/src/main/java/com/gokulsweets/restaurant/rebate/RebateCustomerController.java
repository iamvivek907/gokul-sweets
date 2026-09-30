package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.customer.identity.VerifiedOrderAccess;
import com.gokulsweets.restaurant.rebate.dto.AppliedRebateResponse;
import com.gokulsweets.restaurant.rebate.dto.ApplyRebateRequest;
import com.gokulsweets.restaurant.rebate.dto.AvailableRebateResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class RebateCustomerController {

    private final RebateEligibilityService
            rebateEligibilityService;

    private final RebateApplicationService
            rebateApplicationService;
    private final VerifiedOrderAccess orderAccess;

    @GetMapping(
            "/{orderNumber}/available-rebates"
    )
    public ResponseEntity<List<AvailableRebateResponse>>
    getAvailableRebates(

            @PathVariable
            String orderNumber,
            HttpServletRequest servletRequest
    ) {

        orderAccess.requireOrder(orderNumber, servletRequest);

        return ResponseEntity.ok(
                rebateEligibilityService
                        .getAvailableRebates(
                                orderNumber
                        )
        );
    }

    @GetMapping("/{orderNumber}/rebate-spend-targets")
    public List<AvailableRebateResponse> spendTargets(@PathVariable String orderNumber,HttpServletRequest request) {
        orderAccess.requireOrder(orderNumber,request);
        return rebateEligibilityService.getSpendTargets(orderNumber);
    }

    @PostMapping(
            "/{orderNumber}/rebate"
    )
    public ResponseEntity<AppliedRebateResponse>
    applyRebate(

            @PathVariable
            String orderNumber,

            @Valid
            @RequestBody
            ApplyRebateRequest request,
            HttpServletRequest servletRequest
    ) {

        orderAccess.requireOrder(orderNumber, servletRequest);

        return ResponseEntity.ok(
                rebateApplicationService
                        .apply(
                                orderNumber,
                                request
                        )
        );
    }

    @DeleteMapping(
            "/{orderNumber}/rebate"
    )
    public ResponseEntity<AppliedRebateResponse>
    removeRebate(

            @PathVariable
            String orderNumber,
            HttpServletRequest servletRequest
    ) {

        orderAccess.requireOrder(orderNumber, servletRequest);

        return ResponseEntity.ok(
                rebateApplicationService
                        .remove(
                                orderNumber
                        )
        );
    }
}
