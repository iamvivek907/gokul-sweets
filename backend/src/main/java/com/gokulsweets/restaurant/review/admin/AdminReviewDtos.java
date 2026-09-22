package com.gokulsweets.restaurant.review.admin;

import com.gokulsweets.restaurant.review.ReviewStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminReviewDtos {

    private AdminReviewDtos() {
    }

    public record StatusUpdateRequest(@NotNull ReviewStatus status) {
    }

    public record ReviewSummaryResponse(
            Long id,
            String orderNumber,
            Long branchId,
            String branchName,
            String customerName,
            Integer overallRating,
            String comment,
            ReviewStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ReviewPageResponse(
            List<ReviewSummaryResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
