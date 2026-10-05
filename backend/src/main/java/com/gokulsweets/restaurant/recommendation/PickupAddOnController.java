package com.gokulsweets.restaurant.recommendation;
import com.gokulsweets.restaurant.customer.identity.VerifiedOrderAccess;
import com.gokulsweets.restaurant.inventory.dto.CustomerInventoryCheckRequest;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/menu/pickup-addons")
public class PickupAddOnController {
 private final PickupAddOnService service;
 private final VerifiedOrderAccess access;
 private String owned(String number,HttpServletRequest request){if(number==null)return null;number=number.trim().toUpperCase(java.util.Locale.ROOT);access.requireOrder(number,request);return number;}
 @PostMapping public List<PickupAddOnService.Suggestion> recommend(@RequestParam long branchId,@RequestParam(required=false) Long pickupSlotId,@RequestParam(required=false) com.gokulsweets.restaurant.order.enums.PickupType pickupType,@RequestParam(required=false) String orderNumber,@Valid @RequestBody CustomerInventoryCheckRequest body,HttpServletRequest request){return service.recommend(branchId,body,owned(orderNumber,request),pickupSlotId,pickupType);}
 @PostMapping("/check") public PickupAddOnService.Availability check(@RequestParam long branchId,@RequestParam(required=false) String orderNumber,@Valid @RequestBody CustomerInventoryCheckRequest body,HttpServletRequest request){return service.check(branchId,body,owned(orderNumber,request));}
}
