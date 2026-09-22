package com.gokulsweets.restaurant.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public record ItemRatingRequest(
            @NotNull Long productId,
            @NotNull @Min(1) @Max(5) Integer rating
    ) {
    }

    public record UpsertReviewRequest(
            @NotNull @Min(1) @Max(5) Integer overallRating,
            @Size(max = 1000) String comment,
            @Size(max = 100) List<@Valid ItemRatingRequest> itemRatings
    ) {
    }

    public record ReviewItemResponse(
            Long productId,
            String productName,
            Integer rating
    ) {
    }

    public record CustomerReviewResponse(
            Long id,
            String orderNumber,
            Integer overallRating,
            String comment,
            ReviewStatus status,
            List<ReviewItemResponse> itemRatings,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ReviewableProductResponse(
            Long productId,
            String productName
    ) {
    }

    public record ReviewContextResponse(
            boolean eligible,
            String eligibilityMessage,
            List<ReviewableProductResponse> products,
            CustomerReviewResponse review
    ) {
    }

    public record ProductSummaryRequest(
            @NotNull @Size(min = 1, max = 100) List<@NotNull Long> productIds
    ) {
    }

    public record ProductRatingSummaryResponse(
            Long productId,
            double averageRating,
            long ratingCount
    ) {
    }
}
