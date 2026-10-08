package com.gokulsweets.restaurant.inventory.repository;

import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence operations for branch inventory policy records. */
public interface BranchInventoryPolicyRepository
        extends JpaRepository<BranchInventoryPolicy, Long> {

    /**
     * Finds by branch product id.
     *
     * @param branchProductId the branch product id
     * @return the find by branch product id result
     */
    @EntityGraph(
            attributePaths = {
                "branchProduct",
                "branchProduct.branch",
                "branchProduct.product",
                "branchProduct.product.category"
            })
    Optional<BranchInventoryPolicy> findByBranchProductId(Long branchProductId);

    /**
     * Finds by branch product id in.
     *
     * @param branchProductIds the branch product ids
     * @return the find by branch product id in result
     */
    @EntityGraph(
            attributePaths = {
                "branchProduct",
                "branchProduct.branch",
                "branchProduct.product",
                "branchProduct.product.category"
            })
    List<BranchInventoryPolicy> findByBranchProductIdIn(Collection<Long> branchProductIds);

    /**
     * Performs the find by branch product branch id and online enabled true order by branch product
     * product name asc operation for branch inventory policy repository.
     *
     * @param branchId the branch id
     * @return the find by branch product branch id and online enabled true order by branch product
     *     product name asc result
     */
    @EntityGraph(
            attributePaths = {
                "branchProduct",
                "branchProduct.branch",
                "branchProduct.product",
                "branchProduct.product.category"
            })
    List<BranchInventoryPolicy>
            findByBranchProduct_Branch_IdAndOnlineEnabledTrueOrderByBranchProduct_Product_NameAsc(
                    Long branchId);
}
