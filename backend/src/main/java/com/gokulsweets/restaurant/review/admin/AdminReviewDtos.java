package com.gokulsweets.restaurant.review.admin;

import com.gokulsweets.restaurant.review.ReviewStatus;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/** Backend admin review dtos contract and implementation. */
public final class AdminReviewDtos {

    /** Creates a admin review dtos instance. */
    private AdminReviewDtos() {}

    /**
     * Immutable status update request data contract.
     *
     * @param status the status
     */
    public record StatusUpdateRequest(@NotNull ReviewStatus status) {}

    /**
     * Immutable review summary response data contract.
     *
     * @param id the id
     * @param orderNumber the order number
     * @param branchId the branch id
     * @param branchName the branch name
     * @param customerName the customer name
     * @param overallRating the overall rating
     * @param comment the comment
     * @param status the status
     * @param createdAt the created at
     * @param updatedAt the updated at
     */
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

    /**
     * Immutable review page response data contract.
     *
     * @param content the content
     * @param page the page
     * @param size the size
     * @param totalElements the total elements
     * @param totalPages the total pages
     */
    public record ReviewPageResponse(
            List<ReviewSummaryResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {}
}
