package com.gokulsweets.restaurant.payment.routing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.provider.*;
import com.gokulsweets.restaurant.payment.provider.phonepe.PhonePeProperties;
import com.gokulsweets.restaurant.payment.provider.razorpay.RazorpayProperties;
import com.gokulsweets.restaurant.payment.service.CheckoutUrlVault;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

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
    void persistedRouteSurvivesRetiredBootstrapAndUnsavedBootstrapFailsClosed() {
        var phonepe = mock(PaymentProvider.class);
        var razorpay = mock(PaymentProvider.class);
        when(phonepe.providerType()).thenReturn(PaymentProviderType.PHONEPE);
        when(razorpay.providerType()).thenReturn(PaymentProviderType.RAZORPAY);
        var realRegistry =
                new PaymentProviderRegistry(List.of(phonepe, razorpay), "RAZORPAY", "PHONEPE");
        var properties = new RazorpayProperties();
        properties.setKeyId("rzp_test_fixture");
        properties.setKeySecret("fixture-secret");
        properties.setWebhookSecret("fixture-webhook");
        var realReadiness =
                new PaymentProviderReadiness(
                        realRegistry,
                        new PhonePeProperties(),
                        properties,
                        new MockEnvironment(),
                        new CheckoutUrlVault(""));
        var service = new PaymentRoutingService(store, realRegistry, realReadiness, staff);
        assertThatThrownBy(service::forNewPayment).isInstanceOf(IllegalStateException.class);
        when(store.read())
                .thenReturn(new PaymentRoutingStore.Selection(PaymentProviderType.RAZORPAY, 1));
        assertThat(service.forNewPayment()).isSameAs(razorpay);
        assertThat(realRegistry.registered(PaymentProviderType.PHONEPE)).isSameAs(phonepe);
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
        when(store.lock())
                .thenReturn(new PaymentRoutingStore.Selection(PaymentProviderType.PHONEPE, 1));
        routing.activate(PaymentProviderType.PHONEPE, 1, "already active");
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

    @Test
    void explicitlyActivatingBootstrapDefaultPersistsItAcrossEnvironmentDefaultChanges() {
        routing.activate(PaymentProviderType.PHONEPE, 0, "Confirm bootstrap choice");
        verify(store)
                .save(
                        PaymentProviderType.PHONEPE,
                        PaymentProviderType.PHONEPE,
                        1,
                        owner,
                        "Confirm bootstrap choice");
    }
}
