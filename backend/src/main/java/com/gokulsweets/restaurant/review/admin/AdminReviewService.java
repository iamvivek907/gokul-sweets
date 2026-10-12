package com.gokulsweets.restaurant.review.admin;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.review.Review;
import com.gokulsweets.restaurant.review.ReviewRepository;
import com.gokulsweets.restaurant.review.ReviewStatus;
import com.gokulsweets.restaurant.review.admin.AdminReviewDtos.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates admin review operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminReviewService {

    private static final int MAX_PAGE_SIZE = AppConstant.ADMIN_REVIEW_SERVICE_MAX_PAGE_SIZE;

    private final ReviewRepository reviewRepository;

    /**
     * Returns reviews.
     *
     * @param branchId the branch id
     * @param status the status
     * @param page the page
     * @param size the size
     * @return the get reviews result
     */
    @Transactional(readOnly = true)
    public ReviewPageResponse getReviews(Long branchId, ReviewStatus status, int page, int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminReviewService.class, "getReviews(Long,ReviewStatus,int,int)");
        try {
            if (page < 0) {
                throw new IllegalArgumentException("Page must not be negative.");
            }
            int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
            PageRequest pageable =
                    PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Review> reviews =
                    status == null
                            ? reviewRepository.findByBranchId(branchId, pageable)
                            : reviewRepository.findByBranchIdAndReviewStatus(
                                    branchId, status, pageable);
            return new ReviewPageResponse(
                    reviews.getContent().stream().map(this::toSummary).toList(),
                    reviews.getNumber(),
                    reviews.getSize(),
                    reviews.getTotalElements(),
                    reviews.getTotalPages());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminReviewService.class,
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
    @Transactional
    public ReviewSummaryResponse updateStatus(Long reviewId, StatusUpdateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminReviewService.class, "updateStatus(Long,StatusUpdateRequest)");
        try {
            Review review =
                    reviewRepository
                            .findById(reviewId)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Review does not exist."));
            review.setReviewStatus(request.status());
            log.info(
                    "Admin review status updated: reviewId={}, status={}",
                    reviewId,
                    request.status());
            return toSummary(reviewRepository.save(review));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminReviewService.class,
                    "updateStatus(Long,StatusUpdateRequest)");
        }
    }

    /**
     * Tos summary.
     *
     * @param review the review
     * @return the to summary result
     */
    private ReviewSummaryResponse toSummary(Review review) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminReviewService.class, "toSummary(Review)");
        try {
            return new ReviewSummaryResponse(
                    review.getId(),
                    review.getOrder().getOrderNumber(),
                    review.getBranch().getId(),
                    review.getBranch().getName(),
                    review.getOrder().getCustomerName(),
                    review.getOverallRating(),
                    review.getComment(),
                    review.getReviewStatus(),
                    review.getCreatedAt(),
                    review.getUpdatedAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminReviewService.class, "toSummary(Review)");
        }
    }
}
