package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/** HTTP endpoints for online payment fee operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/branches/{branchId}/payment-fee")
public class OnlinePaymentFeeController {

    private final BranchRepository branches;

    private final StaffAuthorizationService staff;

    /** Immutable fee data contract. */
    public record Fee(boolean enabled, BigDecimal percentage, BigDecimal taxRate) {}

    /** Immutable input data contract. */
    public record Input(
            boolean enabled,
            @NotNull @DecimalMin("0") @DecimalMax("10") @Digits(integer = 2, fraction = 2)
                    BigDecimal percentage,
            @NotNull @DecimalMin("0") @DecimalMax("28") @Digits(integer = 2, fraction = 2)
                    BigDecimal taxRate,
            boolean reviewed) {}

    /**
     * Authorizes the operation.
     *
     * @param id the id
     */
    private void authorize(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OnlinePaymentFeeController.class, "authorize(long)");
        try {
            staff.requirePermission(PermissionName.BRANCH_MANAGE);
            staff.requireBranchAccess(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OnlinePaymentFeeController.class, "authorize(long)");
        }
    }

    /**
     * Fees the operation.
     *
     * @param b the b
     * @return the fee result
     */
    private Fee fee(Branch b) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OnlinePaymentFeeController.class, "fee(Branch)");
        try {
            return new Fee(
                    b.isOnlinePaymentFeeEnabled(),
                    b.getOnlinePaymentFeeRate(),
                    b.getOnlinePaymentFeeTaxRate());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OnlinePaymentFeeController.class, "fee(Branch)");
        }
    }

    /**
     * Returns the operation.
     *
     * @param branchId the branch id
     * @return the get result
     */
    @GetMapping
    public Fee get(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OnlinePaymentFeeController.class, "get(long)");
        try {
            authorize(branchId);
            return fee(
                    branches.findById(branchId)
                            .orElseThrow(() -> new IllegalArgumentException("Branch not found.")));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OnlinePaymentFeeController.class, "get(long)");
        }
    }

    /**
     * Saves the operation.
     *
     * @param branchId the branch id
     * @param input the input
     * @return the save result
     */
    @PutMapping
    @Transactional
    public Fee save(@PathVariable long branchId, @Valid @RequestBody Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OnlinePaymentFeeController.class, "save(long,Input)");
        try {
            authorize(branchId);
            if (input.enabled() && input.percentage().signum() > 0 && !input.reviewed())
                throw new IllegalArgumentException(
                        "Review provider terms and fee tax treatment before enabling the charge.");
            var b =
                    branches.lockFeeBranch(branchId)
                            .orElseThrow(() -> new IllegalArgumentException("Branch not found."));
            b.setOnlinePaymentFeeEnabled(input.enabled());
            b.setOnlinePaymentFeeRate(input.percentage());
            b.setOnlinePaymentFeeTaxRate(input.taxRate());
            b.setPickupFeeVersion(b.getPickupFeeVersion() + 1);
            branches.saveAndFlush(b);
            return fee(b);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OnlinePaymentFeeController.class,
                    "save(long,Input)");
        }
    }
}
