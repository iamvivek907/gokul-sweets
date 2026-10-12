package com.gokulsweets.restaurant.order.correction;

import com.gokulsweets.restaurant.customer.identity.VerifiedOrderAccess;
import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for order correction operations. */
@RestController
@RequiredArgsConstructor
public class OrderCorrectionController {

    private final OrderCorrectionService corrections;

    private final VerifiedOrderAccess access;

    /**
     * Handles {@code GET /api/customer/identity/orders/{number}/correction} for order correction.
     *
     * @param number the number supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code result(corrections.preview(number, false))}
     */
    @GetMapping("/api/customer/identity/orders/{number}/correction")
    public ResponseEntity<OrderCorrectionService.Summary> preview(
            @PathVariable String number, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderCorrectionController.class, "preview(String,HttpServletRequest)");
        try {
            access.requireOwner(number, request);
            return result(corrections.preview(number, false));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCorrectionController.class,
                    "preview(String,HttpServletRequest)");
        }
    }

    /**
     * Handles {@code POST /api/customer/identity/orders/{number}/cancel} for order correction.
     *
     * @param number the number supplied to this method
     * @param input the input supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code result(corrections.cancel(number, input, false))}
     */
    @PostMapping("/api/customer/identity/orders/{number}/cancel")
    public ResponseEntity<OrderCorrectionService.Summary> cancel(
            @PathVariable String number,
            @RequestBody OrderCorrectionService.Cancellation input,
            HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderCorrectionController.class,
                        "cancel(String,OrderCorrectionService.Cancellation,HttpServletRequest)");
        try {
            access.requireOwner(number, request);
            return result(corrections.cancel(number, input, false));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCorrectionController.class,
                    "cancel(String,OrderCorrectionService.Cancellation,HttpServletRequest)");
        }
    }

    /**
     * Staffs preview.
     *
     * @param number the number
     * @return the staff preview result
     */
    @GetMapping("/api/admin/orders/{number}/correction")
    public ResponseEntity<OrderCorrectionService.Summary> staffPreview(
            @PathVariable String number) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderCorrectionController.class, "staffPreview(String)");
        try {
            return result(corrections.preview(number, true));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCorrectionController.class,
                    "staffPreview(String)");
        }
    }

    /**
     * Staffs cancel.
     *
     * @param number the number
     * @param input the input
     * @return the staff cancel result
     */
    @PostMapping("/api/admin/orders/{number}/cancel-refund")
    public ResponseEntity<OrderCorrectionService.Summary> staffCancel(
            @PathVariable String number, @RequestBody OrderCorrectionService.Cancellation input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderCorrectionController.class,
                        "staffCancel(String,OrderCorrectionService.Cancellation)");
        try {
            return result(corrections.cancel(number, input, true));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCorrectionController.class,
                    "staffCancel(String,OrderCorrectionService.Cancellation)");
        }
    }

    /**
     * Handles {@code POST /api/admin/orders/{number}/transfer} for order correction.
     *
     * @param number the number supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code result(corrections.transfer(number, input))}
     */
    @PostMapping("/api/admin/orders/{number}/transfer")
    public ResponseEntity<OrderCorrectionService.Summary> transfer(
            @PathVariable String number, @RequestBody OrderCorrectionService.Transfer input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderCorrectionController.class,
                        "transfer(String,OrderCorrectionService.Transfer)");
        try {
            return result(corrections.transfer(number, input));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCorrectionController.class,
                    "transfer(String,OrderCorrectionService.Transfer)");
        }
    }

    /**
     * Handles {@code POST /api/admin/orders/{number}/reschedule} for order correction.
     *
     * @param number the number supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code result(corrections.reschedule(number, input))}
     */
    @PostMapping("/api/admin/orders/{number}/reschedule")
    public ResponseEntity<OrderCorrectionService.Summary> reschedule(
            @PathVariable String number, @RequestBody OrderCorrectionService.Reschedule input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderCorrectionController.class,
                        "reschedule(String,OrderCorrectionService.Reschedule)");
        try {
            return result(corrections.reschedule(number, input));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCorrectionController.class,
                    "reschedule(String,OrderCorrectionService.Reschedule)");
        }
    }

    /**
     * Returns result information for order correction.
     *
     * @param value the value supplied to this method
     * @return the value of {@code
     *     ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value)}
     */
    private ResponseEntity<OrderCorrectionService.Summary> result(
            OrderCorrectionService.Summary value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderCorrectionController.class, "result(OrderCorrectionService.Summary)");
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCorrectionController.class,
                    "result(OrderCorrectionService.Summary)");
        }
    }
}
