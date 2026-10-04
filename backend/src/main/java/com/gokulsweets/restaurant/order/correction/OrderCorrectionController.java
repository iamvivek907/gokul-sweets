package com.gokulsweets.restaurant.order.correction;

import com.gokulsweets.restaurant.customer.identity.VerifiedOrderAccess;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OrderCorrectionController {
  private final OrderCorrectionService corrections;
  private final VerifiedOrderAccess access;

  @GetMapping("/api/customer/identity/orders/{number}/correction")
  public ResponseEntity<OrderCorrectionService.Summary> preview(
      @PathVariable String number, HttpServletRequest request) {
    access.requireOwner(number, request);
    return result(corrections.preview(number, false));
  }

  @PostMapping("/api/customer/identity/orders/{number}/cancel")
  public ResponseEntity<OrderCorrectionService.Summary> cancel(
      @PathVariable String number,
      @RequestBody OrderCorrectionService.Cancellation input,
      HttpServletRequest request) {
    access.requireOwner(number, request);
    return result(corrections.cancel(number, input, false));
  }

  @GetMapping("/api/admin/orders/{number}/correction")
  public ResponseEntity<OrderCorrectionService.Summary> staffPreview(@PathVariable String number) {
    return result(corrections.preview(number, true));
  }

  @PostMapping("/api/admin/orders/{number}/cancel-refund")
  public ResponseEntity<OrderCorrectionService.Summary> staffCancel(
      @PathVariable String number, @RequestBody OrderCorrectionService.Cancellation input) {
    return result(corrections.cancel(number, input, true));
  }

  @PostMapping("/api/admin/orders/{number}/transfer")
  public ResponseEntity<OrderCorrectionService.Summary> transfer(
      @PathVariable String number, @RequestBody OrderCorrectionService.Transfer input) {
    return result(corrections.transfer(number, input));
  }

  private ResponseEntity<OrderCorrectionService.Summary> result(
      OrderCorrectionService.Summary value) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
  }
}
