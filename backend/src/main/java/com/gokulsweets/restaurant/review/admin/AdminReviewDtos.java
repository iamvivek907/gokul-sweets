package com.gokulsweets.restaurant.review.admin;

import com.gokulsweets.restaurant.review.ReviewStatus;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/** Backend admin review dtos contract and implementation. */
public final class AdminReviewDtos {

    private AdminReviewDtos() {}

    /** Immutable status update request data contract. */
    public record StatusUpdateRequest(@NotNull ReviewStatus status) {}

    /** Immutable review summary response data contract. */
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
            LocalDateTime updatedAt) {}

    /** Immutable review page response data contract. */
    public record ReviewPageResponse(
            List<ReviewSummaryResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {}
}
