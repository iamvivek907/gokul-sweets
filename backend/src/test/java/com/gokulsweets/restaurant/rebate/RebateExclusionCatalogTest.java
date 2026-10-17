package com.gokulsweets.restaurant.rebate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.staff.StaffUser;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

class RebateExclusionCatalogTest {
    final StaffAuthorizationService auth = mock(StaffAuthorizationService.class);
    final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    final RebateExclusionCatalogController controller =
            new RebateExclusionCatalogController(auth, jdbc);

    @Test
    void requiresOfferManagementPermission() {
        doThrow(new AccessDeniedException("denied"))
                .when(auth)
                .requirePermission(PermissionName.REBATE_MANAGE);
        assertThatThrownBy(() -> controller.options(1L, "", 0, null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void inaccessibleBranchCannotListProducts() {
        doThrow(new AccessDeniedException("denied")).when(auth).requireBranchAccess(9L);
        assertThatThrownBy(() -> controller.options(9L, "", 0, null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void branchManagersCannotOmitScopeToReadGlobalCatalogue() {
        var staff = mock(StaffUser.class, RETURNS_DEEP_STUBS);
        when(staff.getRole().getName()).thenReturn("BRANCH_ADMIN");
        when(auth.getCurrentStaff()).thenReturn(staff);
        assertThatThrownBy(() -> controller.options(null, "", 0, null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsUnboundedAndInvalidInputs() {
        assertThatThrownBy(() -> controller.options(1L, "x".repeat(101), 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.options(1L, "", -1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.options(1L, "", 0, Set.of(-1L)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }
}
