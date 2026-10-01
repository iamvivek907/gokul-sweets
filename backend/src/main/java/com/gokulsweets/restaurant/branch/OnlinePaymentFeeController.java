package com.gokulsweets.restaurant.branch;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/branches/{branchId}/payment-fee")
public class OnlinePaymentFeeController {
 private final BranchRepository branches;private final StaffAuthorizationService staff;
 public record Fee(boolean enabled,BigDecimal percentage,BigDecimal taxRate) {}
 public record Input(boolean enabled,@NotNull @DecimalMin("0") @DecimalMax("10") @Digits(integer=2,fraction=2) BigDecimal percentage,@NotNull @DecimalMin("0") @DecimalMax("28") @Digits(integer=2,fraction=2) BigDecimal taxRate,boolean reviewed) {}
 private void authorize(long id){staff.requirePermission(PermissionName.BRANCH_MANAGE);staff.requireBranchAccess(id);}
 private Fee fee(Branch b){return new Fee(b.isOnlinePaymentFeeEnabled(),b.getOnlinePaymentFeeRate(),b.getOnlinePaymentFeeTaxRate());}
 @GetMapping public Fee get(@PathVariable long branchId){authorize(branchId);return fee(branches.findById(branchId).orElseThrow(()->new IllegalArgumentException("Branch not found.")));}
 @PutMapping @Transactional public Fee save(@PathVariable long branchId,@Valid @RequestBody Input input){authorize(branchId);if(input.enabled()&&input.percentage().signum()>0&&!input.reviewed())throw new IllegalArgumentException("Review provider terms and fee tax treatment before enabling the charge.");var b=branches.lockFeeBranch(branchId).orElseThrow(()->new IllegalArgumentException("Branch not found."));b.setOnlinePaymentFeeEnabled(input.enabled());b.setOnlinePaymentFeeRate(input.percentage());b.setOnlinePaymentFeeTaxRate(input.taxRate());b.setPickupFeeVersion(b.getPickupFeeVersion()+1);branches.saveAndFlush(b);return fee(b);}
}
