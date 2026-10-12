package com.gokulsweets.restaurant.inventory.automation.repository;

import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAutomationRule;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Persistence operations for inventory automation rule records. */
public interface InventoryAutomationRuleRepository
        extends JpaRepository<InventoryAutomationRule, Long> {

    /**
     * Performs the find by branch product branch id order by branch product product name asc
     * operation for inventory automation rule repository.
     *
     * @param branchId the branch id
     * @return the find by branch product branch id order by branch product product name asc result
     */
    @EntityGraph(
            attributePaths = {
                "branchProduct",
                "branchProduct.branch",
                "branchProduct.product",
                "branchProduct.product.category"
            })
    List<InventoryAutomationRule> findByBranchProduct_Branch_IdOrderByBranchProduct_Product_NameAsc(
            Long branchId);

    /**
     * Finds by active true.
     *
     * @return the find by active true result
     */
    @EntityGraph(
            attributePaths = {"branchProduct", "branchProduct.branch", "branchProduct.product"})
    List<InventoryAutomationRule> findByActiveTrue();

    /**
     * Finds by branch product id.
     *
     * @param branchProductId the branch product id
     * @return the find by branch product id result
     */
    Optional<InventoryAutomationRule> findByBranchProductId(Long branchProductId);
}
