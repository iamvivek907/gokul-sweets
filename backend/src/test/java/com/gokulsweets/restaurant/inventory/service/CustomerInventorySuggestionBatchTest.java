package com.gokulsweets.restaurant.inventory.service;

import com.gokulsweets.restaurant.branch.*;
import com.gokulsweets.restaurant.branchproduct.*;
import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.dto.CustomerInventoryCheckRequest;
import com.gokulsweets.restaurant.inventory.entity.*;
import com.gokulsweets.restaurant.inventory.enums.*;
import com.gokulsweets.restaurant.inventory.repository.*;
import com.gokulsweets.restaurant.product.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

class CustomerInventorySuggestionBatchTest {
    @Test
    void findsEarliestDateAcrossBatchesWithoutQueryingEveryDate() {
        var branches = mock(BranchRepository.class);
        var products = mock(BranchProductRepository.class);
        var policies = mock(BranchInventoryPolicyRepository.class);
        var allocations = mock(InventoryDailyAllocationRepository.class);
        var branch = new Branch(); branch.setId(1L); branch.setActive(true); branch.setOperational(true);
        var product = new Product(); product.setId(1L); product.setName("Test"); product.setActive(true); product.setSaleMode(ProductSaleMode.UNIT);
        var bp = new BranchProduct(); bp.setId(1L); bp.setProduct(product); bp.setAvailable(true);
        var policy = new BranchInventoryPolicy(); policy.setBranchProduct(bp); policy.setOnlineEnabled(true);
        policy.setInventoryUnit(InventoryUnit.PIECE); policy.setControlMode(InventoryControlMode.DAILY_PRODUCTION); policy.setBookingHorizonDays(14);
        var today = LocalDate.of(2026,10,6);
        var allocation = new InventoryDailyAllocation(); allocation.setBranchProduct(bp); allocation.setServiceDate(today.plusDays(8));
        var calculator = mock(InventoryAvailabilityService.class);
        when(calculator.calculate(allocation, policy)).thenReturn(new com.gokulsweets.restaurant.inventory.model.InventoryAvailability(
                1L,today.plusDays(8),InventoryUnit.PIECE,InventoryAllocationStatus.APPROVED,BigDecimal.TEN,true,null,null));
        when(branches.findById(1L)).thenReturn(java.util.Optional.of(branch));
        when(products.findForOrder(eq(1L),anyCollection())).thenReturn(List.of(bp));
        when(policies.findByBranchProductIdIn(anyCollection())).thenReturn(List.of(policy));
        when(allocations.findByBranchProductIdInAndServiceDate(anyCollection(),eq(today))).thenReturn(List.of());
        when(allocations.findByBranchProductIdInAndServiceDateBetween(anyCollection(),eq(today.plusDays(1)),eq(today.plusDays(7)))).thenReturn(List.of());
        when(allocations.findByBranchProductIdInAndServiceDateBetween(anyCollection(),eq(today.plusDays(8)),eq(today.plusDays(14)))).thenReturn(List.of(allocation));
        var properties = new InventoryProperties(); properties.setEnforcementEnabled(true);
        var service = new CustomerInventoryAvailabilityService(branches,products,policies,allocations,calculator,
                new InventoryQuantityService(),properties,Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(),ZoneOffset.UTC));
        var response = service.check(1L,new CustomerInventoryCheckRequest(today,List.of(new CustomerInventoryCheckRequest.Item(1L,1,null))));
        assertThat(response.orderable()).isFalse(); assertThat(response.suggestedDate()).isEqualTo(today.plusDays(8));
        verify(allocations,times(1)).findByBranchProductIdInAndServiceDate(anyCollection(),any());
        verify(allocations,times(2)).findByBranchProductIdInAndServiceDateBetween(anyCollection(),any(),any());
    }
}
