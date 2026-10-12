package com.gokulsweets.restaurant.order.controller;

import com.gokulsweets.restaurant.customer.identity.TrustedCheckoutIdentity;
import com.gokulsweets.restaurant.customer.identity.VerifiedOrderAccess;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.CreateOrderRequest;
import com.gokulsweets.restaurant.order.dto.CustomerOrderHistoryRequest;
import com.gokulsweets.restaurant.order.dto.CustomerOrderResponse;
import com.gokulsweets.restaurant.order.dto.CustomerOrderSummaryResponse;
import com.gokulsweets.restaurant.order.dto.OrderResponse;
import com.gokulsweets.restaurant.order.dto.UpdatePendingOrderRequest;
import com.gokulsweets.restaurant.order.service.CheckoutQuoteService;
import com.gokulsweets.restaurant.order.service.OrderQueryService;
import com.gokulsweets.restaurant.order.service.OrderService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.CacheControl;
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

/** HTTP endpoints for order operations. */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    private final OrderQueryService orderQueryService;

    private final CheckoutQuoteService checkoutQuoteService;

    private final TrustedCheckoutIdentity checkoutIdentity;

    private final VerifiedOrderAccess orderAccess;

    private final com.gokulsweets.restaurant.order.service.PickupCodeService pickupCodes;

    /**
     * Pickups code.
     *
     * @param orderNumber the order number
     * @param request the request
     * @return the pickup code result
     */
    @GetMapping("/{orderNumber}/pickup-code")
    public ResponseEntity<com.gokulsweets.restaurant.order.service.PickupCodeService.CustomerCode>
            pickupCode(@PathVariable String orderNumber, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderController.class, "pickupCode(String,HttpServletRequest)");
        try {
            orderAccess.requirePickupCode(orderNumber, request);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(pickupCodes.customerCode(orderNumber));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderController.class,
                    "pickupCode(String,HttpServletRequest)");
        }
    }

    private final com.gokulsweets.restaurant.order.service.MobileCheckoutPreview mobilePreview;

    /**
     * Mobiles preview.
     *
     * @param request the request
     * @param servletRequest the servlet request
     * @return the mobile preview result
     */
    @PostMapping("/mobile-preview")
    public com.gokulsweets.restaurant.order.service.MobileCheckoutPreview.Preview mobilePreview(
            @Valid @RequestBody CreateOrderRequest request, HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderController.class,
                        "mobilePreview(CreateOrderRequest,HttpServletRequest)");
        try {
            return mobilePreview.preview(request, checkoutIdentity.token(servletRequest));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderController.class,
                    "mobilePreview(CreateOrderRequest,HttpServletRequest)");
        }
    }

    /**
     * Previews quote.
     *
     * @param request the request
     * @return the preview quote result
     */
    @PostMapping("/quote")
    public CheckoutQuoteService.Quote previewQuote(@Valid @RequestBody CreateOrderRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderController.class, "previewQuote(CreateOrderRequest)");
        try {
            return checkoutQuoteService.preview(request, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderController.class,
                    "previewQuote(CreateOrderRequest)");
        }
    }

    /**
     * Previews pending quote.
     *
     * @param orderNumber the order number
     * @param request the request
     * @param servletRequest the servlet request
     * @return the preview pending quote result
     */
    @PostMapping("/{orderNumber}/quote")
    public CheckoutQuoteService.Quote previewPendingQuote(
            @PathVariable String orderNumber,
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderController.class,
                        "previewPendingQuote(String,CreateOrderRequest,HttpServletRequest)");
        try {
            orderAccess.requireOrder(orderNumber, servletRequest);
            return checkoutQuoteService.preview(request, orderNumber);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderController.class,
                    "previewPendingQuote(String,CreateOrderRequest,HttpServletRequest)");
        }
    }

    /**
     * Creates order.
     *
     * @param idempotencyKey the idempotency key
     * @param request the request
     * @param servletRequest the servlet request
     * @return the create order result
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderController.class,
                        "createOrder(String,CreateOrderRequest,HttpServletRequest)");
        try {
            log.debug(
                    "Received create order request: branchId={}, pickupSlotId={}, pickupType={},"
                            + " itemCount={}",
                    request.branchId(),
                    request.pickupSlotId(),
                    request.pickupType(),
                    request.items().size());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(
                            orderService.createOrder(
                                    request,
                                    idempotencyKey,
                                    checkoutIdentity.token(servletRequest)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderController.class,
                    "createOrder(String,CreateOrderRequest,HttpServletRequest)");
        }
    }

    /**
     * Updates pending checkout.
     *
     * @param orderNumber the order number
     * @param request the request
     * @param servletRequest the servlet request
     * @return the update pending checkout result
     */
    @PutMapping("/{orderNumber}/checkout")
    public ResponseEntity<OrderResponse> updatePendingCheckout(
            @PathVariable String orderNumber,
            @Valid @RequestBody UpdatePendingOrderRequest request,
            HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderController.class,
                        "updatePendingCheckout(String,UpdatePendingOrderRequest,HttpServletRequest)");
        try {
            orderAccess.requireOrder(orderNumber, servletRequest);
            log.debug(
                    "Received pending checkout update: orderNumber={}, pickupSlotId={},"
                            + " pickupType={}, itemCount={}",
                    orderNumber,
                    request.pickupSlotId(),
                    request.pickupType(),
                    request.items().size());
            return ResponseEntity.ok(orderService.updatePendingCheckout(orderNumber, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderController.class,
                    "updatePendingCheckout(String,UpdatePendingOrderRequest,HttpServletRequest)");
        }
    }

    /**
     * Returns order history.
     *
     * @param request the request
     * @param servletRequest the servlet request
     * @return the get order history result
     */
    @PostMapping("/history")
    public ResponseEntity<List<CustomerOrderSummaryResponse>> getOrderHistory(
            @Valid @RequestBody CustomerOrderHistoryRequest request,
            HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderController.class,
                        "getOrderHistory(CustomerOrderHistoryRequest,HttpServletRequest)");
        try {
            log.debug(
                    "Received customer order history request: orderCount={}",
                    request.orderNumbers().size());
            return ResponseEntity.ok(
                    orderQueryService.getCustomerOrderHistory(
                            request.orderNumbers().stream()
                                    .filter(number -> orderAccess.mayRead(number, servletRequest))
                                    .toList()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderController.class,
                    "getOrderHistory(CustomerOrderHistoryRequest,HttpServletRequest)");
        }
    }

    /**
     * Returns order.
     *
     * @param orderNumber the order number
     * @param servletRequest the servlet request
     * @return the get order result
     */
    @GetMapping("/{orderNumber}")
    public ResponseEntity<CustomerOrderResponse> getOrder(
            @PathVariable String orderNumber, HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderController.class, "getOrder(String,HttpServletRequest)");
        try {
            orderAccess.requireOrder(orderNumber, servletRequest);
            return ResponseEntity.ok(orderQueryService.getCustomerOrder(orderNumber));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderController.class,
                    "getOrder(String,HttpServletRequest)");
        }
    }
}
