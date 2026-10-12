package com.gokulsweets.restaurant.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Persistence operations for review item records. */
public interface ReviewItemRepository extends JpaRepository<ReviewItem, Long> {

    /** Backend product rating summary view contract and implementation. */
    interface ProductRatingSummaryView {

        /**
         * Returns product id.
         *
         * @return the get product id result
         */
        Long getProductId();

        /**
         * Returns average rating.
         *
         * @return the get average rating result
         */
        Double getAverageRating();

        /**
         * Returns rating count.
         *
         * @return the get rating count result
         */
        Long getRatingCount();
    }

    /**
     * Summarizes products.
     *
     * @param productIds the product ids
     * @param status the status
     * @return the summarize products result
     */
    @Query(
            """
            SELECT ri.product.id AS productId,
                   AVG(ri.rating) AS averageRating,
                   COUNT(ri.id) AS ratingCount
            FROM ReviewItem ri
            WHERE ri.review.reviewStatus = :status
              AND ri.product.id IN :productIds
            GROUP BY ri.product.id
            """)
    List<ProductRatingSummaryView> summarizeProducts(
            @Param("productIds") Collection<Long> productIds, @Param("status") ReviewStatus status);
}
