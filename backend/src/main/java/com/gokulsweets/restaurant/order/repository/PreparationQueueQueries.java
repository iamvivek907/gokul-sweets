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

            Pageable pageable
    );

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
            LocalDate earlyDate
    );
}
