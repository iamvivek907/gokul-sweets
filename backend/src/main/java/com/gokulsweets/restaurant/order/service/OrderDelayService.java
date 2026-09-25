package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.dto.admin.AdminOrderDetailResponse;
import com.gokulsweets.restaurant.order.dto.admin.UpdateOrderDelayRequest;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class OrderDelayService {
    private static final EnumSet<OrderStatus> REPORTABLE =
            EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING);

    private final EnhancementProperties features;
    private final ApplicationClock clock;
    private final OrderRepository orders;
    private final StaffAuthorizationService authorization;
    private final AdminOrderQueryService queries;

    @Transactional
    public AdminOrderDetailResponse report(String orderNumber, UpdateOrderDelayRequest request) {
        if (!features.isTruthfulOrderTracking()) {
            throw new IllegalStateException("Delay reporting is not enabled.");
        }
        Order order = orders.findForUpdate(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order does not exist."));
        authorization.requireBranchAccess(order.getBranch().getId());
        authorization.requirePermission(PermissionName.ORDER_MARK_READY);
        if (!REPORTABLE.contains(order.getOrderStatus())) {
            throw new IllegalStateException("Only confirmed or preparing orders can report a revised ready time.");
        }
        if (request.estimatedReadyAt() == null || request.reason() == null ||
                request.reason().trim().length() < 10 || request.reason().trim().length() > 300) {
            throw new IllegalArgumentException("Provide a revised ready time and a reason of 10 to 300 characters.");
        }
        LocalDateTime now = clock.now();
        LocalDateTime originalStart = order.getPickupSlot().getSlotDate()
                .atTime(order.getPickupSlot().getStartTime());
        if (!request.estimatedReadyAt().isAfter(now) ||
                !request.estimatedReadyAt().isAfter(originalStart) ||
                request.estimatedReadyAt().isAfter(now.plusHours(24))) {
            throw new IllegalArgumentException(
                    "The revised ready time must be after the booked pickup start and within 24 hours from now (IST).");
        }
        String reason = request.reason().trim();
        if (!request.estimatedReadyAt().equals(order.getEstimatedReadyAt()) ||
                !reason.equals(order.getDelayReason())) {
            order.setEstimatedReadyAt(request.estimatedReadyAt());
            order.setDelayReason(reason);
            order.setDelayReportedAt(now);
            orders.saveAndFlush(order);
        }
        return queries.getOrder(orderNumber);
    }
}
