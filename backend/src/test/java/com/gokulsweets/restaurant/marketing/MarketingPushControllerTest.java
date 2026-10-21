package com.gokulsweets.restaurant.marketing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class MarketingPushControllerTest {
    final StaffAuthorizationService authorization = mock(StaffAuthorizationService.class);
    final MarketingPushService marketing = mock(MarketingPushService.class);
    final MarketingPushController controller =
            new MarketingPushController(authorization, marketing);

    @Test
    void everyEndpointRequiresPermissionBeforeReadingOrSending() {
        doThrow(new AccessDeniedException("Denied"))
                .when(authorization)
                .requirePermission(PermissionName.REBATE_MANAGE);
        assertThatThrownBy(() -> controller.get(1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.save(1, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.evaluate(1)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(marketing);
    }

    @Test
    void managersCannotReadOrSendForAnotherBranch() {
        doThrow(new AccessDeniedException("Wrong branch"))
                .when(authorization)
                .requireBranchAccess(2L);
        assertThatThrownBy(() -> controller.get(2)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.save(2, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.evaluate(2)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(marketing);
    }

    @Test
    void evaluationUsesExactAuthorizedBranch() {
        when(marketing.evaluate(3)).thenReturn(2);
        assertThat(controller.evaluate(3)).containsEntry("queued", 2);
        var ordered = inOrder(authorization, marketing);
        ordered.verify(authorization).requirePermission(PermissionName.REBATE_MANAGE);
        ordered.verify(authorization).requireBranchAccess(3L);
        ordered.verify(marketing).evaluate(3);
    }
}
