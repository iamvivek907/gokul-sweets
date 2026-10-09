package com.gokulsweets.restaurant.order.repository;

import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;

import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** JDBC queue fragment: avoid parsing deeply correlated HQL during repository bootstrap. */
public interface PreparationQueueQueries {

    /**
     * Finds preparation queue candidates.
     *
     * @param branchId the branch id
     * @param confirmedStatus the confirmed status
     * @param normalType the normal type
     * @param normalCutoffDate the normal cutoff date
     * @param normalCutoffTime the normal cutoff time
     * @param priorityType the priority type
     * @param priorityCutoffDate the priority cutoff date
     * @param priorityCutoffTime the priority cutoff time
     * @param adminOverrideType the admin override type
     * @param adminOverrideCutoffDate the admin override cutoff date
     * @param adminOverrideCutoffTime the admin override cutoff time
     * @param earlyDate the early date
     * @param pageable the pageable
     * @return the find preparation queue candidates result
     */
    List<Order> findPreparationQueueCandidates(
            Long branchId,
            OrderStatus confirmedStatus,
            PickupType normalType,
            LocalDate normalCutoffDate,
            LocalTime normalCutoffTime,
            PickupType priorityType,
            LocalDate priorityCutoffDate,
            LocalTime priorityCutoffTime,
            PickupType adminOverrideType,
            LocalDate adminOverrideCutoffDate,
            LocalTime adminOverrideCutoffTime,
            LocalDate earlyDate,
            Pageable pageable);

    /**
     * Counts preparation queue candidates.
     *
     * @param branchId the branch id
     * @param confirmedStatus the confirmed status
     * @param normalType the normal type
     * @param normalCutoffDate the normal cutoff date
     * @param normalCutoffTime the normal cutoff time
     * @param priorityType the priority type
     * @param priorityCutoffDate the priority cutoff date
     * @param priorityCutoffTime the priority cutoff time
     * @param adminOverrideType the admin override type
     * @param adminOverrideCutoffDate the admin override cutoff date
     * @param adminOverrideCutoffTime the admin override cutoff time
     * @param earlyDate the early date
     * @return the count preparation queue candidates result
     */
    long countPreparationQueueCandidates(
            Long branchId,
            OrderStatus confirmedStatus,
            PickupType normalType,
            LocalDate normalCutoffDate,
            LocalTime normalCutoffTime,
            PickupType priorityType,
            LocalDate priorityCutoffDate,
            LocalTime priorityCutoffTime,
            PickupType adminOverrideType,
            LocalDate adminOverrideCutoffDate,
            LocalTime adminOverrideCutoffTime,
            LocalDate earlyDate);
}
