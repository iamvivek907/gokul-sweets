package com.gokulsweets.restaurant.delivery;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.ZoneId;

class DeliveryDispatchPilotServiceTest {
    private final EnhancementProperties flags = new EnhancementProperties();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StaffAuthorizationService staff = mock(StaffAuthorizationService.class);
    private final DeliveryDispatchPilotService service =
            new DeliveryDispatchPilotService(
                    flags, staff, jdbc, Clock.system(ZoneId.of("Asia/Kolkata")));

    @Test
    void offByDefaultCannotAccessStaffBoardOrAssign() {
        assertThrows(IllegalStateException.class, () -> service.board(1));
        assertThrows(IllegalStateException.class, () -> service.assign(1, 1, 1));
        verifyNoInteractions(jdbc, staff);
    }

    @Test
    void dispatchRequiresExistingAssignmentWhenPilotIsActive() {
        flags.setDeliveryDispatchPilot(true);
        flags.setDeliveryEconomics(true);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(42L))).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.requireAssignment(42));
    }

    @Test
    void malformedExceptionCannotBeRecorded() {
        flags.setDeliveryDispatchPilot(true);
        flags.setDeliveryEconomics(true);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.exception(1, 42, "WRONG", "detail", false));
        verify(staff).requireBranchAccess(1L);
        verifyNoInteractions(jdbc);
    }

    @Test
    void negativeJourneyCostCannotBeRecorded() {
        flags.setDeliveryDispatchPilot(true);
        flags.setDeliveryEconomics(true);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.complete(1, 42, new java.math.BigDecimal("-1"), "DELIVERED"));
        verifyNoInteractions(jdbc);
    }
}
