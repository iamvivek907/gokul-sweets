package com.gokulsweets.restaurant.staff.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.customer.notification.WebPushProperties;
import com.gokulsweets.restaurant.customer.notification.WebPushTransport;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.staff.StaffUser;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class StaffAlertsControllerTest {
    @Test
    void settingsUseAuthenticatedStaffAndOffFeatureHidesPushSecretsWhileRevocationStillWorks() {
        var authorization = mock(StaffAuthorizationService.class);
        var alerts = mock(StaffOrderAlerts.class);
        var subscriptions = mock(StaffPushSubscriptions.class);
        var push = mock(WebPushTransport.class);
        var email = mock(StaffAlertEmail.class);
        var keys = new WebPushProperties();
        keys.setPublicKey("public-test-key");
        keys.setPrivateKey("private-never-browser");
        var controller =
                new StaffAlertsController(authorization, alerts, subscriptions, push, keys, email);
        var user = new StaffUser();
        user.setId(12L);
        when(authorization.getCurrentStaff()).thenReturn(user);
        when(alerts.scope()).thenReturn("DEV");
        var response = controller.settings(null, new MockHttpServletRequest());
        assertThat(response.getBody().staffId()).isEqualTo(12);
        assertThat(response.getBody().enabled()).isFalse();
        assertThat(response.getBody().applicationServerKey()).isNull();
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(authorization).requirePermission(PermissionName.ORDER_VIEW);
        assertThatThrownBy(() -> controller.page(null, false, ""))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var id = java.util.UUID.randomUUID();
        controller.revoke(id);
        verify(subscriptions).revoke(12, id);
        when(alerts.enabled()).thenReturn(true);
        when(push.configured()).thenReturn(true);
        assertThat(
                        controller
                                .settings(null, new MockHttpServletRequest())
                                .getBody()
                                .applicationServerKey())
                .isEqualTo("public-test-key");
    }

    @Test
    void alertReadCannotBypassOrderViewPermission() {
        var authorization = mock(StaffAuthorizationService.class);
        doThrow(new org.springframework.security.access.AccessDeniedException("No permission"))
                .when(authorization)
                .requirePermission(PermissionName.ORDER_VIEW);
        var alerts = mock(StaffOrderAlerts.class);
        var controller =
                new StaffAlertsController(
                        authorization,
                        alerts,
                        mock(StaffPushSubscriptions.class),
                        mock(WebPushTransport.class),
                        new WebPushProperties(),
                        mock(StaffAlertEmail.class));
        assertThatThrownBy(() -> controller.read(42))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(alerts);
    }
}
