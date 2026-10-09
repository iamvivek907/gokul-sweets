package com.gokulsweets.restaurant.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Persistence operations for review records. */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Finds by order id.
     *
     * @param orderId the order id
     * @return the find by order id result
     */
    @EntityGraph(attributePaths = {"items", "items.product", "order", "branch"})
    @Query("SELECT DISTINCT r FROM Review r WHERE r.order.id = :orderId")
    Optional<Review> findByOrderId(@Param("orderId") Long orderId);

    /**
     * Finds by branch id.
     *
     * @param branchId the branch id
     * @param pageable the pageable
     * @return the find by branch id result
     */
    @EntityGraph(attributePaths = {"order", "branch"})
    Page<Review> findByBranchId(Long branchId, Pageable pageable);

    /**
     * Finds by branch id and review status.
     *
     * @param branchId the branch id
     * @param reviewStatus the review status
     * @param pageable the pageable
     * @return the find by branch id and review status result
     */
    @EntityGraph(attributePaths = {"order", "branch"})
    Page<Review> findByBranchIdAndReviewStatus(
            Long branchId, ReviewStatus reviewStatus, Pageable pageable);
}
