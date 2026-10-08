package com.gokulsweets.restaurant.tax;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.staff.Role;
import com.gokulsweets.restaurant.staff.StaffUser;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class TaxCollectionControllerTest {
    @Test
    void menuManagersCannotReadOrChangeTheGlobalOwnerSwitch() {
        var settings = mock(TaxCollectionSettings.class);
        var auth = mock(StaffAuthorizationService.class);
        var role = new Role();
        role.setName("MENU_MANAGER");
        var user = new StaffUser();
        user.setRole(role);
        when(auth.getCurrentStaff()).thenReturn(user);
        var controller = new TaxCollectionController(settings, auth);
        assertThatThrownBy(controller::get).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.save(new TaxCollectionController.Input(false)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(settings);
    }

    @Test
    void missingMenuPermissionStopsBeforeReadingTheCurrentStaffOrSetting() {
        var settings = mock(TaxCollectionSettings.class);
        var auth = mock(StaffAuthorizationService.class);
        doThrow(new AccessDeniedException("Denied"))
                .when(auth)
                .requirePermission(PermissionName.MENU_MANAGE);
        var controller = new TaxCollectionController(settings, auth);
        assertThatThrownBy(() -> controller.save(new TaxCollectionController.Input(false)))
                .isInstanceOf(AccessDeniedException.class);
        verify(auth, never()).getCurrentStaff();
        verifyNoInteractions(settings);
    }

    @Test
    void authorizedOwnerIdentityIsPassedToTheAudit() {
        var settings = mock(TaxCollectionSettings.class);
        var auth = mock(StaffAuthorizationService.class);
        var role = new Role();
        role.setName("OWNER_ADMIN");
        var user = new StaffUser();
        user.setId(17L);
        user.setRole(role);
        when(auth.getCurrentStaff()).thenReturn(user);
        when(settings.save(false, 17L)).thenReturn(false);
        assertThat(
                        new TaxCollectionController(settings, auth)
                                .save(new TaxCollectionController.Input(false))
                                .enabled())
                .isFalse();
        verify(auth).requirePermission(PermissionName.MENU_MANAGE);
        verify(settings).save(false, 17L);
    }
}
