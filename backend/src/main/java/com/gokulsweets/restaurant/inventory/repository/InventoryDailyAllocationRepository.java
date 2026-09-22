package com.gokulsweets.restaurant.inventory.repository;

import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryDailyAllocationRepository
        extends JpaRepository<InventoryDailyAllocation, Long> {

    List<InventoryDailyAllocation> findByBranchProductIdInAndServiceDateBetween(
            Collection<Long> branchProductIds, LocalDate fromDate, LocalDate throughDate
    );

    @EntityGraph(attributePaths = {"branchProduct", "branchProduct.branch", "branchProduct.product", "branchProduct.product.category"})
    Optional<InventoryDailyAllocation> findByBranchProductIdAndServiceDate(Long branchProductId, LocalDate serviceDate);

    @EntityGraph(attributePaths = {"branchProduct", "branchProduct.branch", "branchProduct.product", "branchProduct.product.category"})
    List<InventoryDailyAllocation> findByBranchProduct_Branch_IdAndServiceDateOrderByBranchProduct_Product_NameAsc(
            Long branchId, LocalDate serviceDate
    );

    @EntityGraph(attributePaths = {"branchProduct", "branchProduct.branch", "branchProduct.product", "branchProduct.product.category"})
    List<InventoryDailyAllocation> findByBranchProduct_Branch_IdAndServiceDateBetweenOrderByServiceDateAsc(
            Long branchId, LocalDate fromDate, LocalDate throughDate
    );

    @EntityGraph(attributePaths = {"branchProduct", "branchProduct.branch", "branchProduct.product", "branchProduct.product.category"})
    List<InventoryDailyAllocation> findByBranchProductIdInAndServiceDate(
            Collection<Long> branchProductIds, LocalDate serviceDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT allocation
            FROM InventoryDailyAllocation allocation
            JOIN FETCH allocation.branchProduct branchProduct
            JOIN FETCH branchProduct.branch
            JOIN FETCH branchProduct.product product
            JOIN FETCH product.category
            WHERE branchProduct.id = :branchProductId
              AND allocation.serviceDate = :serviceDate
            """)
    Optional<InventoryDailyAllocation> findForUpdate(
            @Param("branchProductId") Long branchProductId,
            @Param("serviceDate") LocalDate serviceDate
    );
}
