package com.gokulsweets.restaurant.order;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.AdminOrderQueryService;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerOrderNumberLookupTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final StaffAuthorizationService staff = mock(StaffAuthorizationService.class);
    private final AdminOrderQueryService query = new AdminOrderQueryService(orders,
            mock(com.gokulsweets.restaurant.payment.repository.PaymentRepository.class), staff,
            mock(com.gokulsweets.restaurant.order.service.PreparationEligibilityService.class),
            mock(com.gokulsweets.restaurant.order.config.PreparationWindowProperties.class),
            mock(com.gokulsweets.restaurant.config.ApplicationClock.class),
            mock(org.springframework.jdbc.core.JdbcTemplate.class),
            mock(com.gokulsweets.restaurant.delivery.DeliveryPreparationQueue.class));

    private Order fixture() {
        var branch = new Branch(); branch.setId(17L); branch.setName("Selected branch");
        var order = new Order(); order.setId(42L); order.setBranch(branch);
        order.setOrderNumber("GKS-OPAQUE-INTERNAL"); order.setCustomerOrderNumber(1L);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        when(orders.findByCustomerOrderNumber(1L)).thenReturn(Optional.of(order));
        when(orders.findDetailedByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));
        return order;
    }

    @Test
    void numberLookupPreservesOpaqueReferenceAndChecksBranchAccess() {
        var order = fixture();
        var detail = query.getOrderByCustomerNumber(1);
        assertThat(detail.orderNumber()).isEqualTo(order.getOrderNumber());
        assertThat(detail.customerOrderNumber()).isEqualTo(1L);
        verify(staff).requireBranchAccess(17L);
    }

    @Test
    void numberLookupCannotExposeAnOrderFromAnUnauthorizedBranch() {
        fixture();
        doThrow(new AccessDeniedException("Branch access denied")).when(staff).requireBranchAccess(17L);
        assertThatThrownBy(() -> query.getOrderByCustomerNumber(1)).isInstanceOf(AccessDeniedException.class);
    }
}
