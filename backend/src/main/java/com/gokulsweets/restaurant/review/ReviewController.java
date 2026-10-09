package com.gokulsweets.restaurant.review;

import com.gokulsweets.restaurant.customer.identity.VerifiedOrderAccess;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.review.ReviewDtos.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for review operations. */
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final CustomerReviewService customerReviewService;

    private final VerifiedOrderAccess orderAccess;

    /**
     * Returns review context.
     *
     * @param orderNumber the order number
     * @param servletRequest the servlet request
     * @return the get review context result
     */
    @GetMapping("/api/orders/{orderNumber}/review")
    public ResponseEntity<ReviewContextResponse> getReviewContext(
            @PathVariable String orderNumber, HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ReviewController.class, "getReviewContext(String,HttpServletRequest)");
        try {
            orderAccess.requireOrder(orderNumber, servletRequest);
            return ResponseEntity.ok(customerReviewService.getContext(orderNumber));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ReviewController.class,
                    "getReviewContext(String,HttpServletRequest)");
        }
    }

    /**
     * Upserts review.
     *
     * @param orderNumber the order number
     * @param request the request
     * @param servletRequest the servlet request
     * @return the upsert review result
     */
    @PutMapping("/api/orders/{orderNumber}/review")
    public ResponseEntity<CustomerReviewResponse> upsertReview(
            @PathVariable String orderNumber,
            @Valid @RequestBody UpsertReviewRequest request,
            HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ReviewController.class,
                        "upsertReview(String,UpsertReviewRequest,HttpServletRequest)");
        try {
            orderAccess.requireOrder(orderNumber, servletRequest);
            return ResponseEntity.ok(customerReviewService.upsert(orderNumber, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ReviewController.class,
                    "upsertReview(String,UpsertReviewRequest,HttpServletRequest)");
        }
    }

    /**
     * Returns product summaries.
     *
     * @param request the request
     * @return the get product summaries result
     */
    @PostMapping("/api/reviews/product-summaries")
    public ResponseEntity<List<ProductRatingSummaryResponse>> getProductSummaries(
            @Valid @RequestBody ProductSummaryRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ReviewController.class, "getProductSummaries(ProductSummaryRequest)");
        try {
            return ResponseEntity.ok(customerReviewService.summarizeProducts(request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ReviewController.class,
                    "getProductSummaries(ProductSummaryRequest)");
        }
    }
}
