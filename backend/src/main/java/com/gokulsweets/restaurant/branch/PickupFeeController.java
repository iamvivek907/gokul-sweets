package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/** HTTP endpoints for pickup fee operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/branches/{branchId}/pickup-fee")
public class PickupFeeController {

    private final BranchRepository branches;

    private final StaffAuthorizationService staff;

    /**
     * Immutable fee data contract.
     *
     * @param amount the amount
     * @param taxRate the tax rate
     */
    public record Fee(BigDecimal amount, BigDecimal taxRate) {}

    /**
     * Immutable input data contract.
     *
     * @param amount the amount
     * @param taxRate the tax rate
     * @param taxReviewed the tax reviewed
     */
    public record Input(
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
                    BigDecimal amount,
            @NotNull @DecimalMin("0") @DecimalMax("28") @Digits(integer = 2, fraction = 2)
                    BigDecimal taxRate,
            boolean taxReviewed) {}

    /**
     * Returns branch information for pickup fee.
     *
     * <p>Authorization checks include {@code PermissionName.BRANCH_MANAGE}.
     *
     * @param id the id supplied to this method
     * @return the {@code Branch} result
     */
    private Branch branch(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupFeeController.class, "branch(long)");
        try {
            staff.requirePermission(PermissionName.BRANCH_MANAGE);
            staff.requireBranchAccess(id);
            return branches.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Branch not found."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PickupFeeController.class, "branch(long)");
        }
    }

    /**
     * Handles {@code GET /api/admin/branches/{branchId}/pickup-fee} for pickup fee.
     *
     * @param branchId the branch id supplied to this method
     * @return the {@code Fee} result
     */
    @GetMapping
    public Fee get(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupFeeController.class, "get(long)");
        try {
            var b = branch(branchId);
            return new Fee(b.getPickupConvenienceFee(), b.getPickupConvenienceFeeTaxRate());
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, PickupFeeController.class, "get(long)");
        }
    }

    /**
     * Handles {@code PUT /api/admin/branches/{branchId}/pickup-fee} for pickup fee.
     *
     * <p>Authorization checks include {@code PermissionName.BRANCH_MANAGE}.
     *
     * @param branchId the branch id supplied to this method
     * @param input the input supplied to this method
     * @return the {@code Fee} result
     * @throws IllegalArgumentException when the method rejects the request with {@code Confirm the
     *     fee tax treatment before enabling it.}
     */
    @PutMapping
    @org.springframework.transaction.annotation.Transactional
    public Fee save(@PathVariable long branchId, @Valid @RequestBody Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupFeeController.class, "save(long,Input)");
        try {
            staff.requirePermission(PermissionName.BRANCH_MANAGE);
            staff.requireBranchAccess(branchId);
            var b =
                    branches.lockFeeBranch(branchId)
                            .orElseThrow(() -> new IllegalArgumentException("Branch not found."));
            if (input.amount().signum() > 0 && !input.taxReviewed())
                throw new IllegalArgumentException(
                        "Confirm the fee tax treatment before enabling it.");
            b.setPickupFeeVersion(b.getPickupFeeVersion() + 1);
            b.setPickupConvenienceFee(input.amount());
            b.setPickupConvenienceFeeTaxRate(input.taxRate());
            branches.saveAndFlush(b);
            return new Fee(input.amount(), input.taxRate());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PickupFeeController.class, "save(long,Input)");
        }
    }
}
