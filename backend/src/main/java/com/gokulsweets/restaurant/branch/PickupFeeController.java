package com.gokulsweets.restaurant.branch;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin/branches/{branchId}/pickup-fee")
public class PickupFeeController {
 private final BranchRepository branches;
 private final StaffAuthorizationService staff;
 public record Fee(BigDecimal amount,BigDecimal taxRate) {}
 public record Input(@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal amount,@NotNull @DecimalMin("0") @DecimalMax("28") @Digits(integer=2,fraction=2) BigDecimal taxRate,boolean taxReviewed) {}
 private Branch branch(long id){staff.requirePermission(PermissionName.BRANCH_MANAGE);staff.requireBranchAccess(id);return branches.findById(id).orElseThrow(()->new IllegalArgumentException("Branch not found."));}
 @GetMapping public Fee get(@PathVariable long branchId){var b=branch(branchId);return new Fee(b.getPickupConvenienceFee(),b.getPickupConvenienceFeeTaxRate());}
 @PutMapping @org.springframework.transaction.annotation.Transactional public Fee save(@PathVariable long branchId,@Valid @RequestBody Input input){staff.requirePermission(PermissionName.BRANCH_MANAGE);staff.requireBranchAccess(branchId);var b=branches.lockFeeBranch(branchId).orElseThrow(()->new IllegalArgumentException("Branch not found."));if(input.amount().signum()>0 && !input.taxReviewed())throw new IllegalArgumentException("Confirm the fee tax treatment before enabling it.");b.setPickupFeeVersion(b.getPickupFeeVersion()+1);b.setPickupConvenienceFee(input.amount());b.setPickupConvenienceFeeTaxRate(input.taxRate());branches.saveAndFlush(b);return new Fee(input.amount(),input.taxRate());}
}
