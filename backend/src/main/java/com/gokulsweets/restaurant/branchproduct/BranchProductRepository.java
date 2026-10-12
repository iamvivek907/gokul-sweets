package com.gokulsweets.restaurant.branchproduct;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence operations for branch product records. */
public interface BranchProductRepository extends JpaRepository<BranchProduct, Long> {

    /**
     * Performs the find by branch id and available true order by display order asc operation for
     * branch product repository.
     *
     * @param branchId the branch id
     * @return the find by branch id and available true order by display order asc result
     */
    List<BranchProduct> findByBranchIdAndAvailableTrueOrderByDisplayOrderAsc(Long branchId);

    /**
     * Finds by branch id and product id.
     *
     * @param branchId the branch id
     * @param productId the product id
     * @return the find by branch id and product id result
     */
    Optional<BranchProduct> findByBranchIdAndProductId(Long branchId, Long productId);

    /**
     * Existses by product id and branch id not.
     *
     * @param productId the product id
     * @param branchId the branch id
     * @return the exists by product id and branch id not result
     */
    boolean existsByProductIdAndBranchIdNot(Long productId, Long branchId);

    /**
     * Counts by id in and branch id.
     *
     * @param ids the ids
     * @param branchId the branch id
     * @return the count by id in and branch id result
     */
    long countByIdInAndBranchId(Collection<Long> ids, Long branchId);

    /**
     * Existses category in other branch.
     *
     * @param categoryId the category id
     * @param branchId the branch id
     * @return the exists category in other branch result
     */
    @Query(
            """
            SELECT CASE WHEN COUNT(bp) > 0 THEN true ELSE false END
            FROM BranchProduct bp
            WHERE bp.product.category.id = :categoryId
              AND bp.branch.id <> :branchId
            """)
    boolean existsCategoryInOtherBranch(
            @Param("categoryId") Long categoryId, @Param("branchId") Long branchId);

    /**
     * Finds available menu.
     *
     * @param branchId the branch id
     * @return the find available menu result
     */
    @EntityGraph(attributePaths = {"product", "product.category"})
    @Query(
            """
            SELECT bp FROM BranchProduct bp
            WHERE bp.branch.id = :branchId
              AND bp.available = true
              AND bp.product.active = true
              AND bp.product.category.active = true
              AND bp.occasionOnly = false
            ORDER BY bp.product.category.displayOrder ASC,
                     bp.displayOrder ASC,
                     bp.product.name ASC
            """)
    List<BranchProduct> findAvailableMenu(@Param("branchId") Long branchId);

    /**
     * Finds admin menu.
     *
     * @param branchId the branch id
     * @return the find admin menu result
     */
    @EntityGraph(attributePaths = {"branch", "product", "product.category", "product.taxCategory"})
    @Query(
            """
            SELECT bp FROM BranchProduct bp
            WHERE bp.branch.id = :branchId
            ORDER BY bp.product.category.displayOrder ASC,
                     bp.displayOrder ASC,
                     bp.product.name ASC
            """)
    List<BranchProduct> findAdminMenu(@Param("branchId") Long branchId);

    /**
     * Finds catalog.
     *
     * @param branchId the branch id
     * @return the find catalog result
     */
    @EntityGraph(attributePaths = {"product", "product.category"})
    @Query(
            "SELECT bp FROM BranchProduct bp WHERE bp.branch.id=:branchId ORDER BY"
                + " bp.product.category.displayOrder ASC,bp.displayOrder ASC,bp.product.name ASC")
    List<BranchProduct> findCatalog(@Param("branchId") Long branchId);

    /**
     * Finds for order.
     *
     * @param branchId the branch id
     * @param productIds the product ids
     * @return the find for order result
     */
    @EntityGraph(attributePaths = {"product", "product.taxCategory"})
    @Query(
            """
            SELECT bp FROM BranchProduct bp
            WHERE bp.branch.id = :branchId
              AND bp.product.id IN :productIds
              AND bp.occasionOnly = false
            """)
    List<BranchProduct> findForOrder(
            @Param("branchId") Long branchId, @Param("productIds") Collection<Long> productIds);
}
