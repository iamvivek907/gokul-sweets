package com.gokulsweets.restaurant.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Backend review dtos contract and implementation. */
public final class ReviewDtos {

    /** Creates a review dtos instance. */
    private ReviewDtos() {}

    /**
     * Immutable item rating request data contract.
     *
     * @param productId the product id
     * @param rating the rating
     */
    public record ItemRatingRequest(
            @NotNull Long productId, @NotNull @Min(1) @Max(5) Integer rating) {}

    /**
     * Immutable upsert review request data contract.
     *
     * @param overallRating the overall rating
     * @param comment the comment
     * @param itemRatings the item ratings
     */
    public record UpsertReviewRequest(
            @NotNull @Min(1) @Max(5) Integer overallRating,
            @Size(max = 1000) String comment,
            @Size(max = 100) List<@Valid ItemRatingRequest> itemRatings) {}

    /**
     * Immutable review item response data contract.
     *
     * @param productId the product id
     * @param productName the product name
     * @param rating the rating
     */
    public record ReviewItemResponse(Long productId, String productName, Integer rating) {}

    /**
     * Immutable customer review response data contract.
     *
     * @param id the id
     * @param orderNumber the order number
     * @param overallRating the overall rating
     * @param comment the comment
     * @param status the status
     * @param itemRatings the item ratings
     * @param createdAt the created at
     * @param updatedAt the updated at
     */
    public record CustomerReviewResponse(
            Long id,
            String orderNumber,
            Integer overallRating,
            String comment,
            ReviewStatus status,
            List<ReviewItemResponse> itemRatings,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    /**
     * Immutable reviewable product response data contract.
     *
     * @param productId the product id
     * @param productName the product name
     */
    public record ReviewableProductResponse(Long productId, String productName) {}

    /**
     * Immutable review context response data contract.
     *
     * @param eligible the eligible
     * @param eligibilityMessage the eligibility message
     * @param products the products
     * @param review the review
     */
    public record ReviewContextResponse(
            boolean eligible,
            String eligibilityMessage,
            List<ReviewableProductResponse> products,
            CustomerReviewResponse review) {}

    /**
     * Immutable product summary request data contract.
     *
     * @param productIds the product ids
     */
    public record ProductSummaryRequest(
            @NotNull @Size(min = 1, max = 100) List<@NotNull Long> productIds) {}

    /**
     * Immutable product rating summary response data contract.
     *
     * @param productId the product id
     * @param averageRating the average rating
     * @param ratingCount the rating count
     */
    public record ProductRatingSummaryResponse(
            Long productId, double averageRating, long ratingCount) {}
}
