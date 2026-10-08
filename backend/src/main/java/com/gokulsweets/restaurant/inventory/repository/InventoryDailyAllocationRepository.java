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

/** Persistence operations for inventory daily allocation records. */
public interface InventoryDailyAllocationRepository
        extends JpaRepository<InventoryDailyAllocation, Long> {

    /**
     * Performs the find by branch product id in and service date between operation for inventory
     * daily allocation repository.
     *
     * @param branchProductIds the branch product ids
     * @param fromDate the from date
     * @param throughDate the through date
     * @return the find by branch product id in and service date between result
     */
    List<InventoryDailyAllocation> findByBranchProductIdInAndServiceDateBetween(
            Collection<Long> branchProductIds, LocalDate fromDate, LocalDate throughDate);

    /**
     * Performs the find by branch product id and service date operation for inventory daily
     * allocation repository.
     *
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @return the find by branch product id and service date result
     */
    @EntityGraph(
            attributePaths = {
                "branchProduct",
                "branchProduct.branch",
                "branchProduct.product",
                "branchProduct.product.category"
            })
    Optional<InventoryDailyAllocation> findByBranchProductIdAndServiceDate(
            Long branchProductId, LocalDate serviceDate);

    /**
     * Performs the find by branch product branch id and service date order by branch product
     * product name asc operation for inventory daily allocation repository.
     *
     * @param branchId the branch id
     * @param serviceDate the service date
     * @return the find by branch product branch id and service date order by branch product product
     *     name asc result
     */
    @EntityGraph(
            attributePaths = {
                "branchProduct",
                "branchProduct.branch",
                "branchProduct.product",
                "branchProduct.product.category"
            })
    List<InventoryDailyAllocation>
            findByBranchProduct_Branch_IdAndServiceDateOrderByBranchProduct_Product_NameAsc(
                    Long branchId, LocalDate serviceDate);

    /**
     * Performs the find by branch product branch id and service date between order by service date
     * asc operation for inventory daily allocation repository.
     *
     * @param branchId the branch id
     * @param fromDate the from date
     * @param throughDate the through date
     * @return the find by branch product branch id and service date between order by service date
     *     asc result
     */
    @EntityGraph(
            attributePaths = {
                "branchProduct",
                "branchProduct.branch",
                "branchProduct.product",
                "branchProduct.product.category"
            })
    List<InventoryDailyAllocation>
            findByBranchProduct_Branch_IdAndServiceDateBetweenOrderByServiceDateAsc(
                    Long branchId, LocalDate fromDate, LocalDate throughDate);

    /**
     * Performs the find by branch product id in and service date operation for inventory daily
     * allocation repository.
     *
     * @param branchProductIds the branch product ids
     * @param serviceDate the service date
     * @return the find by branch product id in and service date result
     */
    @EntityGraph(
            attributePaths = {
                "branchProduct",
                "branchProduct.branch",
                "branchProduct.product",
                "branchProduct.product.category"
            })
    List<InventoryDailyAllocation> findByBranchProductIdInAndServiceDate(
            Collection<Long> branchProductIds, LocalDate serviceDate);

    /**
     * Finds for update.
     *
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @return the find for update result
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
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
            @Param("serviceDate") LocalDate serviceDate);
}
