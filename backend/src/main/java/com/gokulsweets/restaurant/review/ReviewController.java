package com.gokulsweets.restaurant.review;

import com.gokulsweets.restaurant.review.ReviewDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final CustomerReviewService customerReviewService;

    @GetMapping("/api/orders/{orderNumber}/review")
    public ResponseEntity<ReviewContextResponse> getReviewContext(
            @PathVariable String orderNumber
    ) {
        return ResponseEntity.ok(customerReviewService.getContext(orderNumber));
    }

    @PutMapping("/api/orders/{orderNumber}/review")
    public ResponseEntity<CustomerReviewResponse> upsertReview(
            @PathVariable String orderNumber,
            @Valid @RequestBody UpsertReviewRequest request
    ) {
        return ResponseEntity.ok(customerReviewService.upsert(orderNumber, request));
    }

    @PostMapping("/api/reviews/product-summaries")
    public ResponseEntity<List<ProductRatingSummaryResponse>> getProductSummaries(
            @Valid @RequestBody ProductSummaryRequest request
    ) {
        return ResponseEntity.ok(customerReviewService.summarizeProducts(request));
    }
}
