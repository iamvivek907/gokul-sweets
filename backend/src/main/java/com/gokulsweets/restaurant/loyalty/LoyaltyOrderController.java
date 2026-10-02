package com.gokulsweets.restaurant.loyalty;
import com.gokulsweets.restaurant.customer.identity.VerifiedOrderAccess;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/orders/{orderNumber}/rewards")
public class LoyaltyOrderController {
 private final VerifiedOrderAccess access;
 private final LoyaltyCheckoutService checkout;
 @GetMapping public ResponseEntity<LoyaltyCheckoutService.Checkout> read(@PathVariable String orderNumber,HttpServletRequest request){access.requireOrder(orderNumber,request);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(checkout.read(orderNumber));}
 @PutMapping public ResponseEntity<LoyaltyCheckoutService.Checkout> select(@PathVariable String orderNumber,@RequestBody LoyaltyService.Selection selection,HttpServletRequest request){access.requireOrder(orderNumber,request);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(checkout.select(orderNumber,selection.rewardCode(),selection.policyVersion()));}
}
