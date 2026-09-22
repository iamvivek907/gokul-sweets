package com.gokulsweets.restaurant.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, Long> {

    interface ProductRatingSummaryView {
        Long getProductId();
        Double getAverageRating();
        Long getRatingCount();
    }

    @Query("""
            SELECT ri.product.id AS productId,
                   AVG(ri.rating) AS averageRating,
                   COUNT(ri.id) AS ratingCount
            FROM ReviewItem ri
            WHERE ri.review.reviewStatus = :status
              AND ri.product.id IN :productIds
            GROUP BY ri.product.id
            """)
    List<ProductRatingSummaryView> summarizeProducts(
            @Param("productIds") Collection<Long> productIds,
            @Param("status") ReviewStatus status
    );
}
