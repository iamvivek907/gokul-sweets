package com.gokulsweets.restaurant.maintenance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.Role;
import com.gokulsweets.restaurant.staff.StaffUser;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class DataCleanupControllerTest {
    @Test
    void everyEndpointRejectsNonOwnerBeforeAccessingData() {
        var staff = mock(StaffAuthorizationService.class);
        var cleanup = mock(DataCleanupService.class);
        var role = new Role();
        role.setName("BRANCH_MANAGER");
        var user = new StaffUser();
        user.setId(1L);
        user.setRole(role);
        when(staff.getCurrentStaff()).thenReturn(user);
        var controller = new DataCleanupController(staff, cleanup);
        assertThatThrownBy(controller::view).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(controller::preview).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(
                        () -> controller.save(new DataCleanupService.Config(true, "03:30", 90, 0)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.run(new DataCleanupController.RunInput(0)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(cleanup);
    }
}
