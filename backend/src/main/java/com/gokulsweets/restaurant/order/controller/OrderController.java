package com.gokulsweets.restaurant.order.controller;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import com.gokulsweets.restaurant.customer.identity.IdentityClientConnection;
import com.gokulsweets.restaurant.order.dto.CreateOrderRequest;
import jakarta.servlet.http.HttpServletRequest;
import com.gokulsweets.restaurant.order.dto.CustomerOrderHistoryRequest;
import com.gokulsweets.restaurant.order.dto.CustomerOrderResponse;
import com.gokulsweets.restaurant.order.dto.CustomerOrderSummaryResponse;
import com.gokulsweets.restaurant.order.dto.OrderResponse;
import com.gokulsweets.restaurant.order.dto.UpdatePendingOrderRequest;
import com.gokulsweets.restaurant.order.service.OrderQueryService;
import com.gokulsweets.restaurant.order.service.OrderService;
import com.gokulsweets.restaurant.order.service.CheckoutQuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
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
    private final CheckoutQuoteService checkoutQuoteService;
    private final IdentityClientConnection clientConnection;
    private final WebCorsProperties cors;
    private final Environment settings;

    @PostMapping("/quote")
    public CheckoutQuoteService.Quote previewQuote(@Valid @RequestBody CreateOrderRequest request) {
        return checkoutQuoteService.preview(request, null);
    }

    @PostMapping("/{orderNumber}/quote")
    public CheckoutQuoteService.Quote previewPendingQuote(@PathVariable String orderNumber,
                                                            @Valid @RequestBody CreateOrderRequest request) {
        return checkoutQuoteService.preview(request, orderNumber);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(

            @RequestHeader("Idempotency-Key")
            String idempotencyKey,

            @Valid
            @RequestBody
            CreateOrderRequest request,
            HttpServletRequest servletRequest
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
                                idempotencyKey,
                                trustedIdentityCookie(servletRequest)
                        )
                );
    }

    private String trustedIdentityCookie(HttpServletRequest request) {
        if (request.getCookies() == null || !cors.effectiveAllowedOrigins(settings)
                .contains(request.getHeader(HttpHeaders.ORIGIN))) return null;
        try {
            if (!clientConnection.resolve(request).secure()) return null;
        } catch (IllegalStateException invalidProxy) {
            return null;
        }
        return java.util.Arrays.stream(request.getCookies())
                .filter(cookie -> "__Host-gokul-customer".equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue).findFirst().orElse(null);
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
