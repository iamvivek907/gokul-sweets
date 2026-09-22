package com.gokulsweets.restaurant.branchproduct;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BranchProductRepository
        extends JpaRepository<BranchProduct, Long> {

    List<BranchProduct> findByBranchIdAndAvailableTrueOrderByDisplayOrderAsc(Long branchId);

    Optional<BranchProduct> findByBranchIdAndProductId(Long branchId, Long productId);

    boolean existsByProductIdAndBranchIdNot(Long productId, Long branchId);

    long countByIdInAndBranchId(Collection<Long> ids, Long branchId);

    @Query("""
            SELECT CASE WHEN COUNT(bp) > 0 THEN true ELSE false END
            FROM BranchProduct bp
            WHERE bp.product.category.id = :categoryId
              AND bp.branch.id <> :branchId
            """)
    boolean existsCategoryInOtherBranch(
            @Param("categoryId") Long categoryId,
            @Param("branchId") Long branchId
    );

    @EntityGraph(attributePaths = {"product", "product.category"})
    @Query("""
            SELECT bp FROM BranchProduct bp
            WHERE bp.branch.id = :branchId
              AND bp.available = true
              AND bp.product.active = true
              AND bp.product.category.active = true
            ORDER BY bp.product.category.displayOrder ASC,
                     bp.displayOrder ASC,
                     bp.product.name ASC
            """)
    List<BranchProduct> findAvailableMenu(@Param("branchId") Long branchId);

    @EntityGraph(attributePaths = {
            "branch",
            "product",
            "product.category",
            "product.taxCategory"
    })
    @Query("""
            SELECT bp FROM BranchProduct bp
            WHERE bp.branch.id = :branchId
            ORDER BY bp.product.category.displayOrder ASC,
                     bp.displayOrder ASC,
                     bp.product.name ASC
            """)
    List<BranchProduct> findAdminMenu(@Param("branchId") Long branchId);

    @EntityGraph(attributePaths = {"product", "product.taxCategory"})
    @Query("""
            SELECT bp FROM BranchProduct bp
            WHERE bp.branch.id = :branchId
              AND bp.product.id IN :productIds
            """)
    List<BranchProduct> findForOrder(
            @Param("branchId") Long branchId,
            @Param("productIds") Collection<Long> productIds
    );
}
