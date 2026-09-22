package com.gokulsweets.restaurant.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @EntityGraph(attributePaths = {"items", "items.product", "order", "branch"})
    @Query("SELECT DISTINCT r FROM Review r WHERE r.order.id = :orderId")
    Optional<Review> findByOrderId(@Param("orderId") Long orderId);

    @EntityGraph(attributePaths = {"order", "branch"})
    Page<Review> findByBranchId(Long branchId, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "branch"})
    Page<Review> findByBranchIdAndReviewStatus(
            Long branchId,
            ReviewStatus reviewStatus,
            Pageable pageable
    );
}
