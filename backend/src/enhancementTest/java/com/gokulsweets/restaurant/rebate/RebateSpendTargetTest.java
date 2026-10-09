package com.gokulsweets.restaurant.rebate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.*;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

class RebateSpendTargetTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final RebateRepository rebates = mock(RebateRepository.class);
    private final RebateSlabRepository slabs = mock(RebateSlabRepository.class);
    private final RebateEligibilityService service =
            new RebateEligibilityService(
                    orders,
                    mock(PaymentRepository.class),
                    rebates,
                    slabs,
                    mock(RebateCustomerRepository.class),
                    mock(RebateRedemptionRepository.class));

    private Rebate setup(String spend, String threshold, String saving) {
        var branch = new Branch();
        branch.setId(1L);
        var order = new Order();
        order.setId(1L);
        order.setBranch(branch);
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        order.setPickupType(PickupType.NORMAL);
        order.setFulfillmentType(FulfillmentType.PICKUP);
        order.setSubtotal(new BigDecimal(spend));
        order.setTaxAmount(BigDecimal.ZERO);
        order.setPriorityCharge(BigDecimal.ZERO);
        order.setConvenienceFee(new BigDecimal("100"));
        when(orders.findDetailedByOrderNumber("GKS-TARGET")).thenReturn(Optional.of(order));
        var rebate = new Rebate();
        rebate.setId(1L);
        rebate.setScope(RebateScope.GENERAL);
        rebate.setRebateType(RebateType.SLAB);
        when(rebates.findActivePublicCandidates(eq(1L), any())).thenReturn(List.of(rebate));
        var slab = new RebateSlab();
        slab.setMinimumOrderAmount(new BigDecimal(threshold));
        slab.setRebateAmount(new BigDecimal(saving));
        when(slabs.findByRebateIdOrderByMinimumOrderAmountAsc(1L)).thenReturn(List.of(slab));
        return rebate;
    }

    @Test
    void firstTierIsInformationalAndFeeCannotUnlockIt() {
        setup("50", "100", "20");
        assertThat(service.getAvailableRebates("GKS-TARGET")).isEmpty();
        var target = service.getSpendTargets("GKS-TARGET").getFirst();
        assertThat(target.amountNeededForNextSlab()).isEqualByComparingTo("50");
        assertThat(target.nextSlabRebateAmount()).isEqualByComparingTo("20");
    }

    @Test
    void extraDiscountConsumingSpendIsNeverPromoted() {
        setup("90", "100", "20");
        assertThat(service.getSpendTargets("GKS-TARGET")).isEmpty();
    }

    @Test
    void authoritativeMaximumCapsBothPromotionAndAvailableDiscount() {
        var rebate = setup("50", "100", "40");
        rebate.setMaximumDiscountAmount(new BigDecimal("15"));
        assertThat(service.getSpendTargets("GKS-TARGET").getFirst().nextSlabRebateAmount())
                .isEqualByComparingTo("15");
        setup("150", "100", "40").setMaximumDiscountAmount(new BigDecimal("15"));
        assertThat(service.getAvailableRebates("GKS-TARGET").getFirst().rebateAmount())
                .isEqualByComparingTo("15");
    }
}
