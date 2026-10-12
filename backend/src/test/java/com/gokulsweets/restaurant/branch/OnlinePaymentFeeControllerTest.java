package com.gokulsweets.restaurant.branch;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

class OnlinePaymentFeeControllerTest {
    @Test
    void savingRequiresBranchPermissionAndReviewAndVersionsPricing() {
        var branches = mock(BranchRepository.class);
        var staff = mock(StaffAuthorizationService.class);
        var controller = new OnlinePaymentFeeController(branches, staff);
        var b = new Branch();
        b.setId(3L);
        when(branches.lockFeeBranch(3L)).thenReturn(Optional.of(b));
        var input =
                new OnlinePaymentFeeController.Input(
                        true, new BigDecimal("2.00"), new BigDecimal("18.00"), false);
        assertThatThrownBy(() -> controller.save(3L, input))
                .isInstanceOf(IllegalArgumentException.class);
        verify(branches, never()).lockFeeBranch(anyLong());
        controller.save(
                3L,
                new OnlinePaymentFeeController.Input(
                        true, input.percentage(), input.taxRate(), true));
        assertThat(b.isOnlinePaymentFeeEnabled()).isTrue();
        assertThat(b.getOnlinePaymentFeeRate()).isEqualByComparingTo("2");
        assertThat(b.getPickupFeeVersion()).isEqualTo(1);
        verify(staff, atLeastOnce()).requirePermission(PermissionName.BRANCH_MANAGE);
        verify(staff, atLeastOnce()).requireBranchAccess(3L);
        doThrow(new org.springframework.security.access.AccessDeniedException("Other branch"))
                .when(staff)
                .requireBranchAccess(4L);
        assertThatThrownBy(() -> controller.save(4L, input))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(branches, never()).lockFeeBranch(4L);
    }
}
