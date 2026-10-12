package com.gokulsweets.restaurant.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.branch.*;
import com.gokulsweets.restaurant.branchproduct.*;
import com.gokulsweets.restaurant.menu.MenuServiceWindows;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.pickup.PickupSlotValidationService;
import com.gokulsweets.restaurant.pickup.repository.PickupSlotRepository;
import com.gokulsweets.restaurant.product.Product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

class PickupValidationSnapshotTest {
    @Test
    void previewReusesOneEvaluatorForManualChecksAndAllSlotsWithoutCrossRequestCaching() {
        var branches = mock(BranchRepository.class);
        var products = mock(BranchProductRepository.class);
        var windows = mock(MenuServiceWindows.class);
        var branch = new Branch();
        branch.setId(1L);
        branch.setActive(true);
        branch.setOperational(true);
        when(branches.findById(1L)).thenReturn(Optional.of(branch));
        var product = new Product();
        product.setId(2L);
        product.setName("Sweet");
        product.setActive(true);
        product.setBasePrice(BigDecimal.TEN);
        var bp = new BranchProduct();
        bp.setId(3L);
        bp.setBranch(branch);
        bp.setProduct(product);
        when(products.findForOrder(eq(1L), anySet())).thenReturn(List.of(bp));
        Function<LocalDateTime, MenuServiceWindows.Snapshot> evaluator = mock(Function.class);
        when(evaluator.apply(any())).thenReturn(new MenuServiceWindows.Snapshot(false, Map.of()));
        when(windows.pickupEvaluator(1L, Set.of(2L))).thenReturn(evaluator);
        var validation =
                new OrderValidationService(
                        branches,
                        products,
                        mock(PickupSlotRepository.class),
                        mock(PickupSlotValidationService.class),
                        mock(SmartOrderingRules.class),
                        windows);
        var request = List.of(new CreateOrderItemRequest(2L, 1, null));
        var first = validation.validatePickupCartWithWindows(1L, request);
        assertThat(first.items()).hasSize(1);
        assertThat(first.serviceAt()).isSameAs(evaluator);
        for (int slot = 0; slot < 40; slot++)
            first.serviceAt().apply(LocalDateTime.of(2026, 10, 9, 10, slot));
        verify(windows, times(1)).pickupEvaluator(1L, Set.of(2L));
        validation.validatePickupCartWithWindows(1L, request);
        verify(windows, times(2)).pickupEvaluator(1L, Set.of(2L));

        when(evaluator.apply(isNull()))
                .thenReturn(
                        new MenuServiceWindows.Snapshot(
                                true,
                                Map.of(
                                        2L,
                                        new MenuServiceWindows.Status(
                                                false, "SOLD_OUT", "Sold out", null))));
        assertThatThrownBy(() -> validation.validatePickupCartWithWindows(1L, request))
                .hasMessageContaining("Sold out");
        assertThatThrownBy(() -> validation.validatePickupCart(1L, request))
                .hasMessageContaining("Sold out");
    }
}
