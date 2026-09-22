package com.gokulsweets.restaurant.review.admin;

import com.gokulsweets.restaurant.review.ReviewStatus;
import com.gokulsweets.restaurant.review.admin.AdminReviewDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    @GetMapping
    public ResponseEntity<ReviewPageResponse> getReviews(
            @RequestParam Long branchId,
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminReviewService.getReviews(branchId, status, page, size));
    }

    @PatchMapping("/{reviewId}/status")
    public ResponseEntity<ReviewSummaryResponse> updateStatus(
            @PathVariable Long reviewId,
            @Valid @RequestBody StatusUpdateRequest request
    ) {
        return ResponseEntity.ok(adminReviewService.updateStatus(reviewId, request));
    }
}
