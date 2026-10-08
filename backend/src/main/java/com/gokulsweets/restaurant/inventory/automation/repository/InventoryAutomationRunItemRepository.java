package com.gokulsweets.restaurant.inventory.automation.repository;

import com.gokulsweets.restaurant.inventory.automation.dto.AutomationRunExplanation;
import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAutomationRunItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Persistence operations for inventory automation run item records. */
public interface InventoryAutomationRunItemRepository
        extends JpaRepository<InventoryAutomationRunItem, Long> {

    /**
     * Explains the operation.
     *
     * @param branchId the branch id
     * @param runId the run id
     * @return the explain result
     */
    @Query(
            """
select new com.gokulsweets.restaurant.inventory.automation.dto.AutomationRunExplanation(
    i.branchProduct.product.name, i.outcome, i.message, min(i.serviceDate), max(i.serviceDate), count(i))
from InventoryAutomationRunItem i where i.run.id = :runId and i.run.branch.id = :branchId
group by i.branchProduct.id, i.branchProduct.product.name, i.outcome, i.message
order by i.branchProduct.product.name
""")
    List<AutomationRunExplanation> explain(Long branchId, Long runId);
}
