package com.gokulsweets.restaurant.order.controller;

import com.gokulsweets.restaurant.order.dto.CreateOrderRequest;
import com.gokulsweets.restaurant.order.dto.CustomerOrderHistoryRequest;
import com.gokulsweets.restaurant.order.dto.CustomerOrderResponse;
import com.gokulsweets.restaurant.order.dto.CustomerOrderSummaryResponse;
import com.gokulsweets.restaurant.order.dto.OrderResponse;
import com.gokulsweets.restaurant.order.dto.UpdatePendingOrderRequest;
import com.gokulsweets.restaurant.order.service.OrderQueryService;
import com.gokulsweets.restaurant.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    private final OrderQueryService orderQueryService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(

            @RequestHeader("Idempotency-Key")
            String idempotencyKey,

            @Valid
            @RequestBody
            CreateOrderRequest request
    ) {

        log.debug(
                "Received create order request: branchId={}, pickupSlotId={}, pickupType={}, itemCount={}",
                request.branchId(),
                request.pickupSlotId(),
                request.pickupType(),
                request.items().size()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        orderService.createOrder(
                                request,
                                idempotencyKey
                        )
                );
    }

    @PutMapping("/{orderNumber}/checkout")
    public ResponseEntity<OrderResponse> updatePendingCheckout(

            @PathVariable
            String orderNumber,

            @Valid
            @RequestBody
            UpdatePendingOrderRequest request
    ) {

        log.debug(
                "Received pending checkout update: orderNumber={}, pickupSlotId={}, pickupType={}, itemCount={}",
                orderNumber,
                request.pickupSlotId(),
                request.pickupType(),
                request.items().size()
        );

        return ResponseEntity.ok(
                orderService.updatePendingCheckout(
                        orderNumber,
                        request
                )
        );
    }

    /*
     * One compact request replaces hundreds of individual
     * customer-order requests on the My Orders screen.
     */
    @PostMapping("/history")
    public ResponseEntity<List<CustomerOrderSummaryResponse>> getOrderHistory(

            @Valid
            @RequestBody
            CustomerOrderHistoryRequest request
    ) {

        log.debug(
                "Received customer order history request: orderCount={}",
                request.orderNumbers().size()
        );

        return ResponseEntity.ok(
                orderQueryService.getCustomerOrderHistory(
                        request.orderNumbers()
                )
        );
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<CustomerOrderResponse> getOrder(

            @PathVariable
            String orderNumber
    ) {

        return ResponseEntity.ok(
                orderQueryService.getCustomerOrder(
                        orderNumber
                )
        );
    }
}
