package com.gokulsweets.restaurant.pickup;

import com.gokulsweets.restaurant.pickup.repository.PickupSlotRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickupAdminAuthorizationTest {
    @Test void generationAndListingRequireBranchAccessBeforeReadingOrWritingSlots() {
        var service = mock(PickupSlotService.class);
        var repository = mock(PickupSlotRepository.class);
        var authorization = mock(StaffAuthorizationService.class);
        var controller = new PickupSlotController(service, authorization, repository);
        doThrow(new AccessDeniedException("Wrong branch")).when(authorization).requireBranchAccess(2L);
        assertThatThrownBy(() -> controller.createSlots(2L, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.listSlots(2L, LocalDate.now(), LocalDate.now())).isInstanceOf(AccessDeniedException.class);
        verify(authorization, times(2)).requirePermission(PermissionName.BRANCH_MANAGE);
        verifyNoInteractions(service, repository);
    }
}
