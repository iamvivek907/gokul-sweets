package com.gokulsweets.restaurant.review;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.entity.OrderItem;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.review.ReviewDtos.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Coordinates customer review operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerReviewService {

    private final OrderRepository orderRepository;

    private final ReviewRepository reviewRepository;

    private final ReviewItemRepository reviewItemRepository;

    private final EntityManager entityManager;

    /**
     * Returns context.
     *
     * @param orderNumber the order number
     * @return the get context result
     */
    @Transactional(readOnly = true)
    public ReviewContextResponse getContext(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerReviewService.class, "getContext(String)");
        try {
            Order order = findOrder(orderNumber);
            CustomerReviewResponse existing =
                    reviewRepository
                            .findByOrderId(order.getId())
                            .map(this::toResponse)
                            .orElse(null);
            boolean eligible =
                    order.getOrderStatus() == OrderStatus.PICKED_UP
                            || order.getOrderStatus() == OrderStatus.DELIVERED;
            return new ReviewContextResponse(
                    eligible,
                    eligible ? null : eligibilityMessage(order.getOrderStatus()),
                    reviewableProducts(order),
                    existing);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerReviewService.class, "getContext(String)");
        }
    }

    /**
     * Inserts or updates customer review data and returns the {@code CustomerReviewResponse}
     * result.
     *
     * <p>Delegates to {@code reviewRepository.findByOrderId(...)}, {@code
     * reviewRepository.save(...)}.
     *
     * @param orderNumber the order number supplied to this method
     * @param request the request supplied to this method
     * @return the {@code CustomerReviewResponse} result
     * @throws IllegalArgumentException when the method rejects the request with {@code Each product
     *     can be rated only once.}; {@code Only products from this order can be rated.}
     */
    @Transactional
    public CustomerReviewResponse upsert(String orderNumber, UpsertReviewRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerReviewService.class, "upsert(String,UpsertReviewRequest)");
        try {
            Order order = findOrder(orderNumber);
            entityManager.lock(order, LockModeType.PESSIMISTIC_WRITE);
            if (order.getOrderStatus() != OrderStatus.PICKED_UP
                    && order.getOrderStatus() != OrderStatus.DELIVERED) {
                throw new IllegalStateException(eligibilityMessage(order.getOrderStatus()));
            }
            Map<Long, OrderItem> purchasedProducts =
                    order.getItems().stream()
                            .collect(
                                    Collectors.toMap(
                                            item -> item.getProduct().getId(),
                                            Function.identity(),
                                            (first, ignored) -> first,
                                            LinkedHashMap::new));
            List<ItemRatingRequest> requestedItems =
                    request.itemRatings() == null ? List.of() : request.itemRatings();
            Set<Long> seen = new HashSet<>();
            for (ItemRatingRequest itemRating : requestedItems) {
                if (!seen.add(itemRating.productId())) {
                    throw new IllegalArgumentException("Each product can be rated only once.");
                }
                if (!purchasedProducts.containsKey(itemRating.productId())) {
                    throw new IllegalArgumentException(
                            "Only products from this order can be rated.");
                }
            }
            Review review =
                    reviewRepository
                            .findByOrderId(order.getId())
                            .orElseGet(
                                    () -> {
                                        Review created = new Review();
                                        created.setOrder(order);
                                        created.setBranch(order.getBranch());
                                        return created;
                                    });
            review.setOverallRating(request.overallRating());
            review.setComment(normalizeComment(request.comment()));
            Map<Long, ReviewItem> existingItems =
                    review.getItems().stream()
                            .collect(
                                    Collectors.toMap(
                                            item -> item.getProduct().getId(),
                                            Function.identity()));
            review.getItems().stream()
                    .filter(item -> !seen.contains(item.getProduct().getId()))
                    .toList()
                    .forEach(review::removeItem);
            for (ItemRatingRequest itemRating : requestedItems) {
                OrderItem purchased = purchasedProducts.get(itemRating.productId());
                ReviewItem reviewItem = existingItems.get(itemRating.productId());
                if (reviewItem == null) {
                    reviewItem = new ReviewItem();
                    reviewItem.setProduct(purchased.getProduct());
                    reviewItem.setProductName(purchased.getProductName());
                    review.addItem(reviewItem);
                }
                reviewItem.setRating(itemRating.rating());
            }
            Review saved = reviewRepository.save(review);
            log.info(
                    "Customer review saved: orderNumber={}, reviewId={}, overallRating={},"
                            + " itemRatingCount={}",
                    order.getOrderNumber(),
                    saved.getId(),
                    saved.getOverallRating(),
                    saved.getItems().size());
            return toResponse(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerReviewService.class,
                    "upsert(String,UpsertReviewRequest)");
        }
    }

    /**
     * Summarizes products.
     *
     * @param request the request
     * @return the summarize products result
     */
    @Transactional(readOnly = true)
    public List<ProductRatingSummaryResponse> summarizeProducts(ProductSummaryRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerReviewService.class, "summarizeProducts(ProductSummaryRequest)");
        try {
            LinkedHashSet<Long> productIds = new LinkedHashSet<>(request.productIds());
            return reviewItemRepository
                    .summarizeProducts(productIds, ReviewStatus.PUBLISHED)
                    .stream()
                    .map(
                            summary ->
                                    new ProductRatingSummaryResponse(
                                            summary.getProductId(),
                                            BigDecimal.valueOf(summary.getAverageRating())
                                                    .setScale(1, RoundingMode.HALF_UP)
                                                    .doubleValue(),
                                            summary.getRatingCount()))
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerReviewService.class,
                    "summarizeProducts(ProductSummaryRequest)");
        }
    }

    /**
     * Finds order.
     *
     * @param suppliedOrderNumber the supplied order number
     * @return the find order result
     */
    private Order findOrder(String suppliedOrderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerReviewService.class, "findOrder(String)");
        try {
            String orderNumber = suppliedOrderNumber.trim().toUpperCase();
            return orderRepository
                    .findDetailedByOrderNumber(orderNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Order does not exist."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerReviewService.class, "findOrder(String)");
        }
    }

    /**
     * Reviewables products.
     *
     * @param order the order
     * @return the reviewable products result
     */
    private List<ReviewableProductResponse> reviewableProducts(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerReviewService.class, "reviewableProducts(Order)");
        try {
            return order.getItems().stream()
                    .collect(
                            Collectors.toMap(
                                    item -> item.getProduct().getId(),
                                    item ->
                                            new ReviewableProductResponse(
                                                    item.getProduct().getId(),
                                                    item.getProductName()),
                                    (first, ignored) -> first,
                                    LinkedHashMap::new))
                    .values()
                    .stream()
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerReviewService.class,
                    "reviewableProducts(Order)");
        }
    }

    /**
     * Eligibility message.
     *
     * @param status the status
     * @return the eligibility message result
     */
    private String eligibilityMessage(OrderStatus status) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerReviewService.class, "eligibilityMessage(OrderStatus)");
        try {
            return switch (status) {
                case PENDING_PAYMENT,
                                CONFIRMED,
                                PREPARING,
                                READY_FOR_PICKUP,
                                READY_FOR_DELIVERY,
                                OUT_FOR_DELIVERY ->
                        "You can review this order once it has been picked up or delivered.";
                case PAYMENT_FAILED, CANCELLED, NO_SHOW, PICKUP_WINDOW_EXPIRED ->
                        "This order is not eligible for a review.";
                case PICKED_UP, DELIVERED -> null;
            };
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerReviewService.class,
                    "eligibilityMessage(OrderStatus)");
        }
    }

    /**
     * Normalizes comment.
     *
     * @param comment the comment
     * @return the normalize comment result
     */
    private String normalizeComment(String comment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerReviewService.class, "normalizeComment(String)");
        try {
            if (comment == null || comment.isBlank()) {
                return null;
            }
            return comment.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerReviewService.class,
                    "normalizeComment(String)");
        }
    }

    /**
     * Tos response.
     *
     * @param review the review
     * @return the to response result
     */
    private CustomerReviewResponse toResponse(Review review) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerReviewService.class, "toResponse(Review)");
        try {
            return new CustomerReviewResponse(
                    review.getId(),
                    review.getOrder().getOrderNumber(),
                    review.getOverallRating(),
                    review.getComment(),
                    review.getReviewStatus(),
                    review.getItems().stream()
                            .map(
                                    item ->
                                            new ReviewItemResponse(
                                                    item.getProduct().getId(),
                                                    item.getProductName(),
                                                    item.getRating()))
                            .toList(),
                    review.getCreatedAt(),
                    review.getUpdatedAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerReviewService.class, "toResponse(Review)");
        }
    }
}
