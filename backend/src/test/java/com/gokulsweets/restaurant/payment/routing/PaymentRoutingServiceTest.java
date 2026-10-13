package com.gokulsweets.restaurant.payment.routing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.provider.*;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class PaymentRoutingServiceTest {
    PaymentRoutingStore store = mock(PaymentRoutingStore.class);
    PaymentProviderRegistry registry = mock(PaymentProviderRegistry.class);
    PaymentProviderReadiness readiness = mock(PaymentProviderReadiness.class);
    StaffAuthorizationService staff = mock(StaffAuthorizationService.class);
    PaymentRoutingService routing = new PaymentRoutingService(store, registry, readiness, staff);
    StaffUser owner = new StaffUser();

    @BeforeEach
    void setup() {
        var role = new Role();
        role.setName("OWNER_ADMIN");
        owner.setRole(role);
        owner.setId(1L);
        owner.setUsername("owner");
        when(staff.getCurrentStaff()).thenReturn(owner);
        when(registry.defaultProvider()).thenReturn(PaymentProviderType.PHONEPE);
        when(store.read()).thenReturn(new PaymentRoutingStore.Selection(null, 0));
        when(store.lock()).thenReturn(new PaymentRoutingStore.Selection(null, 0));
        for (var provider : List.of(PaymentProviderType.PHONEPE, PaymentProviderType.RAZORPAY))
            when(readiness.check(provider))
                    .thenReturn(new PaymentProviderReadiness.Status(provider, true, List.of()));
    }

    @Test
    void usesBootstrapUntilOwnerSwitchThenReadsEveryNewAttempt() {
        assertThat(routing.activeProvider()).isEqualTo(PaymentProviderType.PHONEPE);
        when(store.read())
                .thenReturn(new PaymentRoutingStore.Selection(PaymentProviderType.RAZORPAY, 1));
        var adapter = mock(PaymentProvider.class);
        when(registry.registered(PaymentProviderType.RAZORPAY)).thenReturn(adapter);
        assertThat(routing.forNewPayment()).isSameAs(adapter);
        verify(store, times(2)).read();
    }

    @Test
    void switchIsAuditedAndRevisionPreventsStaleOwnerOverwrite() {
        assertThat(
                        routing.activate(PaymentProviderType.RAZORPAY, 0, "  live migration  ")
                                .revision())
                .isEqualTo(1);
        verify(store)
                .save(
                        PaymentProviderType.PHONEPE,
                        PaymentProviderType.RAZORPAY,
                        1,
                        owner,
                        "live migration");
        when(store.lock())
                .thenReturn(new PaymentRoutingStore.Selection(PaymentProviderType.RAZORPAY, 1));
        assertThatThrownBy(() -> routing.activate(PaymentProviderType.PHONEPE, 0, "stale"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refresh");
        verify(store, times(1)).save(any(), any(), anyLong(), any(), anyString());
    }

    @Test
    void duplicateActivationDoesNotCreateAnotherAuditEntry() {
        routing.activate(PaymentProviderType.PHONEPE, 0, "already active");
        verify(store, never()).save(any(), any(), anyLong(), any(), anyString());
    }

    @Test
    void unconfiguredProviderIsBlockedWithoutFallbackOrMutation() {
        when(readiness.check(PaymentProviderType.PHONEPE))
                .thenReturn(
                        new PaymentProviderReadiness.Status(
                                PaymentProviderType.PHONEPE, false, List.of("Missing key")));
        assertThatThrownBy(routing::forNewPayment).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> routing.activate(PaymentProviderType.PHONEPE, 0, "try"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(registry, never()).registered(any());
        verify(store, never()).save(any(), any(), anyLong(), any(), anyString());
    }

    @Test
    void rejectsManagerInactiveOwnerAndUnsupportedProvider() {
        owner.getRole().setName("MANAGER");
        assertThatThrownBy(routing::workspace)
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> routing.activate(PaymentProviderType.RAZORPAY, 0, "switch"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        owner.getRole().setName("OWNER_ADMIN");
        owner.setActive(false);
        assertThatThrownBy(routing::workspace)
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        owner.setActive(true);
        assertThatThrownBy(() -> routing.activate(PaymentProviderType.PAYTM, 0, "unsupported"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
