package com.gokulsweets.restaurant.review.admin;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminReviewService {

    private static final int MAX_PAGE_SIZE = 100;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public ReviewPageResponse getReviews(
            Long branchId,
            ReviewStatus status,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must not be negative.");
        }

        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(
                page,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Review> reviews = status == null
                ? reviewRepository.findByBranchId(branchId, pageable)
                : reviewRepository.findByBranchIdAndReviewStatus(branchId, status, pageable);

        return new ReviewPageResponse(
                reviews.getContent().stream().map(this::toSummary).toList(),
                reviews.getNumber(),
                reviews.getSize(),
                reviews.getTotalElements(),
                reviews.getTotalPages()
        );
    }

    @Transactional
    public ReviewSummaryResponse updateStatus(Long reviewId, StatusUpdateRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review does not exist."));
        review.setReviewStatus(request.status());

        log.info("Admin review status updated: reviewId={}, status={}", reviewId, request.status());
        return toSummary(reviewRepository.save(review));
    }

    private ReviewSummaryResponse toSummary(Review review) {
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
                review.getUpdatedAt()
        );
    }
}
