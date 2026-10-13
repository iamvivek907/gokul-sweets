package com.gokulsweets.restaurant.payment.routing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.*;

@SpringBootTest(
        properties = {"gokul.jobs.background-enabled=false", "payment.default-provider=PHONEPE"})
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentRoutingIntegrationTest {
    @Autowired PaymentRoutingService routing;
    @Autowired PaymentRoutingStore store;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StaffAuthorizationService staff;
    @MockitoBean PaymentProviderReadiness readiness;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM payment_provider_switch_audit");
        jdbc.update(
                "UPDATE payment_provider_routing SET active_provider=NULL, revision=0,"
                    + " changed_at=NULL");
        var actor = new StaffUser();
        var role = new Role();
        role.setName("OWNER_ADMIN");
        actor.setRole(role);
        actor.setUsername("routing-test-owner");
        when(staff.getCurrentStaff()).thenReturn(actor);
        for (var provider : List.of(PaymentProviderType.PHONEPE, PaymentProviderType.RAZORPAY))
            when(readiness.check(provider))
                    .thenReturn(new PaymentProviderReadiness.Status(provider, true, List.of()));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM payment_provider_switch_audit");
        jdbc.update(
                "UPDATE payment_provider_routing SET active_provider=NULL, revision=0,"
                    + " changed_at=NULL");
    }

    @Test
    void persistsRouteAndAuditedActorAndNeverReadsAProcessCache() {
        routing.activate(PaymentProviderType.RAZORPAY, 0, "Approved DEV payment/refund test");
        assertThat(store.read().provider()).isEqualTo(PaymentProviderType.RAZORPAY);
        assertThat(routing.activeProvider()).isEqualTo(PaymentProviderType.RAZORPAY);
        assertThat(store.history()).hasSize(1);
        assertThat(store.history().getFirst().username()).isEqualTo("routing-test-owner");
        assertThat(store.history().getFirst().revision()).isEqualTo(1);
    }

    @Test
    void concurrentOwnersCannotOverwriteTheSameObservedRevision() throws Exception {
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task =
                    () -> {
                        gate.await();
                        try {
                            routing.activate(PaymentProviderType.RAZORPAY, 0, "Concurrent switch");
                            return true;
                        } catch (IllegalStateException stale) {
                            return false;
                        }
                    };
            var one = executor.submit(task);
            var two = executor.submit(task);
            gate.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(store.history()).hasSize(1);
            assertThat(store.read().revision()).isEqualTo(1);
        }
    }

    @Test
    void failedReadinessCheckLeavesBothRoutingAndAuditUnchanged() {
        when(readiness.check(PaymentProviderType.RAZORPAY))
                .thenReturn(
                        new PaymentProviderReadiness.Status(
                                PaymentProviderType.RAZORPAY, false, List.of("Missing secret")));
        assertThatThrownBy(() -> routing.activate(PaymentProviderType.RAZORPAY, 0, "Blocked"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.read().revision()).isZero();
        assertThat(store.history()).isEmpty();
    }
}
