package com.gokulsweets.restaurant.inventory.repository;

import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BranchInventoryPolicyRepository
        extends JpaRepository<BranchInventoryPolicy, Long> {

    @EntityGraph(attributePaths = {
            "branchProduct", "branchProduct.branch", "branchProduct.product",
            "branchProduct.product.category"
    })
    Optional<BranchInventoryPolicy> findByBranchProductId(Long branchProductId);

    @EntityGraph(attributePaths = {
            "branchProduct", "branchProduct.branch", "branchProduct.product",
            "branchProduct.product.category"
    })
    List<BranchInventoryPolicy> findByBranchProductIdIn(Collection<Long> branchProductIds);

    @EntityGraph(attributePaths = {
            "branchProduct", "branchProduct.branch", "branchProduct.product",
            "branchProduct.product.category"
    })
    List<BranchInventoryPolicy>
    findByBranchProduct_Branch_IdAndOnlineEnabledTrueOrderByBranchProduct_Product_NameAsc(
            Long branchId
    );
}
