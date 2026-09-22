package com.gokulsweets.restaurant.inventory.automation.repository;

import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAutomationRule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryAutomationRuleRepository
        extends JpaRepository<InventoryAutomationRule, Long> {

    @EntityGraph(attributePaths = {
            "branchProduct",
            "branchProduct.branch",
            "branchProduct.product",
            "branchProduct.product.category"
    })
    List<InventoryAutomationRule> findByBranchProduct_Branch_IdOrderByBranchProduct_Product_NameAsc(
            Long branchId
    );

    @EntityGraph(attributePaths = {"branchProduct", "branchProduct.branch", "branchProduct.product"})
    List<InventoryAutomationRule> findByActiveTrue();

    Optional<InventoryAutomationRule> findByBranchProductId(Long branchProductId);
}
