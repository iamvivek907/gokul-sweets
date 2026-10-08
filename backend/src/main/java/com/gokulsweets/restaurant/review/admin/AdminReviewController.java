package com.gokulsweets.restaurant.review.admin;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.review.ReviewStatus;
import com.gokulsweets.restaurant.review.admin.AdminReviewDtos.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for admin review operations. */
@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    /**
     * Returns reviews.
     *
     * @param branchId the branch id
     * @param status the status
     * @param page the page
     * @param size the size
     * @return the get reviews result
     */
    @GetMapping
    public ResponseEntity<ReviewPageResponse> getReviews(
            @RequestParam Long branchId,
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminReviewController.class, "getReviews(Long,ReviewStatus,int,int)");
        try {
            return ResponseEntity.ok(adminReviewService.getReviews(branchId, status, page, size));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminReviewController.class,
                    "getReviews(Long,ReviewStatus,int,int)");
        }
    }

    /**
     * Updates status.
     *
     * @param reviewId the review id
     * @param request the request
     * @return the update status result
     */
    @PatchMapping("/{reviewId}/status")
    public ResponseEntity<ReviewSummaryResponse> updateStatus(
            @PathVariable Long reviewId, @Valid @RequestBody StatusUpdateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminReviewController.class, "updateStatus(Long,StatusUpdateRequest)");
        try {
            return ResponseEntity.ok(adminReviewService.updateStatus(reviewId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminReviewController.class,
                    "updateStatus(Long,StatusUpdateRequest)");
        }
    }
}
