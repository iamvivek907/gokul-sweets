package com.gokulsweets.restaurant.staff.notification;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.notification.WebPushTransport;
import com.gokulsweets.restaurant.security.StaffSessionService;
import com.gokulsweets.restaurant.staff.StaffUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "gokul.features.staff-order-alerts=true", "gokul.notifications.staff.scheduler-enabled=false",
    "gokul.environment-isolation.environment=DEV", "gokul.environment-isolation.enabled=true",
    "gokul.web.environment-cors-enabled=true", "gokul.environment-isolation.api-origin=https://api-staff-alerts-ci.example.invalid",
    "phonepe.redirect-url=https://dev.gokulsweets.in/checkout", "phonepe.webhook-url=https://api-staff-alerts-ci.example.invalid/api/payments/webhooks/phonepe",
    "phonepe.webhook-checksum-key-id=staff-alert-test", "cloudflare.r2.bucket-name=gokul-staff-alert-test",
    "cloudflare.r2.public-url=https://images-staff-alert-ci.example.invalid"
})
@Transactional
class StaffOrderAlertsIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox customerInbox;
    @Autowired jakarta.persistence.EntityManager entities;
    @Autowired EnhancementProperties flags;
    @MockitoSpyBean StaffOrderAlerts alerts;
    @Autowired StaffAlertDispatcher dispatcher;
    @Autowired StaffPushSubscriptions subscriptions;
    @Autowired StaffSessionService sessions;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean WebPushTransport push;
    @MockitoBean StaffAlertEmail email;
    long role, branch, otherBranch, staff, otherStaff, order, slot, payment;
    String number, cookie, publicKey;

    @BeforeEach void setup() throws Exception {
        flags.setStaffOrderAlerts(true);
        doReturn(LocalDateTime.of(2026, 10, 1, 16, 49)).when(alerts).now();
        role = jdbc.queryForObject("INSERT INTO roles(name) VALUES (?) RETURNING id", Long.class, "ALERT-" + UUID.randomUUID());
        jdbc.update("INSERT INTO role_permissions(role_id, permission_id) SELECT ?, id FROM permissions WHERE name IN ('ORDER_VIEW','ORDER_START_PREPARATION','ORDER_MARK_READY')", role);
        branch = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, 'Alerts branch') RETURNING id", Long.class, "ALERT-" + UUID.randomUUID().toString().substring(0,8));
        otherBranch = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, 'Other branch') RETURNING id", Long.class, "ALERT-" + UUID.randomUUID().toString().substring(0,8));
        staff = staff(branch); otherStaff = staff(otherBranch);
        slot = jdbc.queryForObject("INSERT INTO pickup_slots(branch_id, slot_date, start_time, end_time, capacity) VALUES (?, '2026-10-01', '18:00', '18:30', 20) RETURNING id", Long.class, branch);
        number = "ALERT-" + UUID.randomUUID();
        order = jdbc.queryForObject("""
            INSERT INTO orders(order_number, branch_id, pickup_slot_id, customer_name, customer_phone, pickup_type, order_status, reservation_expires_at)
            VALUES (?, ?, ?, 'Test', '9876543210', 'NORMAL', 'CONFIRMED', CURRENT_TIMESTAMP) RETURNING id
            """, Long.class, number, branch, slot);
        payment = jdbc.queryForObject("INSERT INTO payments(order_id, provider, amount, payment_status) VALUES (?, 'PHONEPE', 100, 'PAID') RETURNING id", Long.class, order);
        StaffUser user = new StaffUser(); user.setId(staff);
        user.setUpdatedAt(jdbc.queryForObject("SELECT updated_at FROM staff_users WHERE id = ?", LocalDateTime.class, staff));
        cookie = sessions.issue(user).token();
        when(push.configured()).thenReturn(true);
        when(email.configured()).thenReturn(true);
        when(email.recipient(staff)).thenReturn("staff-test@example.invalid");
        when(email.send(anyLong(), anyString(), anyString(), anyString(), anyLong())).thenReturn(200);
        if (java.security.Security.getProvider("BC") == null) java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        var generator = java.security.KeyPairGenerator.getInstance("EC", "BC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        publicKey = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(((org.bouncycastle.jce.interfaces.ECPublicKey) generator.generateKeyPair().getPublic()).getQ().getEncoded(false));
    }
    private long staff(long branch) {
        long id = jdbc.queryForObject("INSERT INTO staff_users(username, password_hash, full_name, role_id) VALUES (?, 'test-only', 'Alert staff', ?) RETURNING id", Long.class, "alert-" + UUID.randomUUID(), role);
        jdbc.update("INSERT INTO staff_branch_access(staff_user_id, branch_id) VALUES (?, ?)", id, branch); return id;
    }
    private StaffPushSubscriptions.Input input(String token) {
        return new StaffPushSubscriptions.Input("https://fcm.googleapis.com/fcm/send/" + token, publicKey, java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]));
    }
    private void now(int hour, int minute) {doReturn(LocalDateTime.of(2026, 10, 1, hour, minute)).when(alerts).now();}
    private void status(String status) {jdbc.update("UPDATE orders SET order_status = ? WHERE id = ?", status, order); entities.clear();}
    private long latest() {return alerts.page(staff, null).messages().getFirst().event().id();}

    @Test void markAllReadPreservesLaterAlertsOtherStaffAndUnresolvedActions() {
        alerts.paymentConfirmed(payment);
        long snapshot=alerts.page(staff,null).readThrough();
        now(18,5);alerts.generateReminders();
        alerts.markAllRead(staff,snapshot);
        assertThat(alerts.page(staff,null).unreadCount()).isEqualTo(1);
        assertThat(alerts.page(staff,null).messages()).anyMatch(m->m.actionRequired());
        alerts.markAllRead(staff,alerts.page(staff,null).readThrough());
        assertThat(alerts.page(staff,null).unreadCount()).isZero();
        assertThat(alerts.page(staff,null).messages()).anyMatch(m->m.actionRequired());
        assertThat(alerts.page(otherStaff,null).messages()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT order_status FROM orders WHERE id=?",String.class,order)).isEqualTo("CONFIRMED");
    }

    @Test void committedStagesAcknowledgeEligibleTaskAlertsButReadAloneDoesNotResolve() {
        long colleague=staff(branch);
        alerts.paymentConfirmed(payment);now(18,5);alerts.generateReminders();
        alerts.markRead(staff,latest());
        assertThat(alerts.page(staff,null).messages()).anyMatch(m->m.actionRequired());
        status("PREPARING");customerInbox.orderReady(order);
        assertThat(alerts.page(staff,null).unreadCount()).isZero();
        assertThat(alerts.page(colleague,null).unreadCount()).isZero();
        assertThat(alerts.page(otherStaff,null).messages()).isEmpty();
        alerts.generateReminders();
        assertThat(alerts.page(staff,null).messages().getFirst().event().kind()).isEqualTo("READY_OVERDUE");
        assertThat(alerts.page(staff,null).messages().getFirst().readAt()).isNull();
        status("READY_FOR_PICKUP");customerInbox.orderReady(order);customerInbox.orderReady(order);
        assertThat(alerts.page(staff,null).unreadCount()).isZero();
        assertThat(alerts.page(colleague,null).messages()).allMatch(m->m.readAt()!=null&&!m.actionRequired());
    }

    @Test void paidReplayIsOneExactBranchEventAndReadIsIdempotentWithoutChangingOrder() throws Exception {
        jdbc.update("UPDATE payments SET payment_status = 'PENDING' WHERE id = ?", payment);
        alerts.paymentConfirmed(payment); assertThat(alerts.page(staff, null).messages()).isEmpty();
        jdbc.update("UPDATE payments SET payment_status = 'PAID' WHERE id = ?", payment);
        alerts.paymentConfirmed(payment); alerts.paymentConfirmed(payment);
        assertThat(alerts.page(staff, null).messages()).hasSize(1);
        assertThat(alerts.page(staff, null).messages().getFirst().event().message()).contains(number, "06:00 PM", "IST");
        assertThat(alerts.page(otherStaff, null).messages()).isEmpty();
        assertThatThrownBy(() -> alerts.markRead(otherStaff, latest())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        alerts.markRead(staff, latest()); alerts.markRead(staff, latest());
        assertThat(alerts.page(staff, null).unreadCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT order_status FROM orders WHERE id = ?", String.class, order)).isEqualTo("CONFIRMED");
        flags.setStaffOrderAlerts(false); alerts.paymentConfirmed(payment); alerts.generateReminders(); dispatcher.dispatchBatch();
        verify(push, never()).sendStaff(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString());
    }
    @Test void reminderBoundariesUseExistingEligibilityAndActionSuppressesQueuedPush() throws Exception {
        subscriptions.subscribe(staff, cookie, input("reminder-" + UUID.randomUUID()));
        alerts.generateReminders(); assertThat(alerts.page(staff, null).messages()).isEmpty();
        now(16, 50); alerts.generateReminders(); alerts.generateReminders();
        assertThat(alerts.page(staff, null).messages()).hasSize(1);
        assertThat(alerts.page(staff, null).messages().getFirst().event().kind()).isEqualTo("PREPARATION_SOON");
        now(17, 0); alerts.generateReminders();
        assertThat(alerts.page(staff, null).messages().getFirst().event().kind()).isEqualTo("PREPARATION_DUE");
        status("PREPARING");
        dispatcher.dispatchBatch();
        verify(push, never()).sendStaff(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString());
        assertThat(alerts.page(staff, null).messages()).allMatch(message -> !message.actionRequired());
    }
    @Test void reminderCrossesIndiaMidnightWithoutUsingBrowserOrServerTimezone() {
        jdbc.update("UPDATE pickup_slots SET start_time = '00:30', end_time = '01:00' WHERE id = ?", slot);
        doReturn(LocalDateTime.of(2026, 9, 30, 23, 19)).when(alerts).now(); alerts.generateReminders();
        assertThat(alerts.page(staff, null).messages()).isEmpty();
        doReturn(LocalDateTime.of(2026, 9, 30, 23, 20)).when(alerts).now(); alerts.generateReminders();
        assertThat(alerts.page(staff, null).messages().getFirst().event().kind()).isEqualTo("PREPARATION_SOON");
        doReturn(LocalDateTime.of(2026, 9, 30, 23, 30)).when(alerts).now(); alerts.generateReminders();
        assertThat(alerts.page(staff, null).messages().getFirst().event().kind()).isEqualTo("PREPARATION_DUE");
    }
    @Test void readDoesNotStopOverdueEmailButPreparationAndReadyActionsDo() throws Exception {
        now(18, 0); alerts.generateReminders(); long waiting = latest();
        alerts.markRead(staff, waiting);
        now(18, 4); dispatcher.dispatchBatch(); verify(email, never()).send(anyLong(), anyString(), anyString(), anyString(), anyLong());
        now(18, 5); dispatcher.dispatchBatch(); dispatcher.dispatchBatch();
        verify(email, times(1)).send(eq(staff), anyString(), anyString(), eq(number), eq(waiting));
        verify(email, never()).send(eq(otherStaff), anyString(), anyString(), anyString(), anyLong());
        status("PREPARING");
        alerts.generateReminders(); long ready = latest();
        status("READY_FOR_PICKUP");
        dispatcher.dispatchBatch(); verify(email, never()).send(eq(staff), anyString(), anyString(), anyString(), eq(ready));
        assertThat(alerts.page(staff, null).messages()).allMatch(message -> !message.actionRequired());
    }
    @Test void liveBrowserRegistrationIsIdempotentAndPermissionRevocationOrGoneEndpointStopsSend() throws Exception {
        var input = input("live-" + UUID.randomUUID()); var registered = subscriptions.subscribe(staff, cookie, input);
        assertThat(subscriptions.subscribe(staff, cookie, input).id()).isEqualTo(registered.id());
        assertThatThrownBy(() -> subscriptions.subscribe(otherStaff, cookie, input)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        alerts.paymentConfirmed(payment); long event = latest();
        jdbc.update("DELETE FROM role_permissions WHERE role_id = ? AND permission_id = (SELECT id FROM permissions WHERE name = 'ORDER_VIEW')", role);
        dispatcher.dispatchBatch(); verify(push, never()).sendStaff(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString());
        jdbc.update("INSERT INTO role_permissions(role_id, permission_id) SELECT ?, id FROM permissions WHERE name = 'ORDER_VIEW'", role);
        when(push.sendStaff(anyString(), anyString(), anyString(), eq(event), anyString(), anyString(), anyString())).thenReturn(410);
        dispatcher.dispatchBatch();
        assertThat(subscriptions.live(staff, registered.id(), cookie)).isFalse();
        subscriptions.revoke(staff, registered.id()); subscriptions.revoke(staff, registered.id());
        sessions.revoke(cookie);
        assertThatThrownBy(() -> subscriptions.subscribe(staff, cookie, input("revoked"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void emailRetriesAreBounded() throws Exception {
        now(18, 5); alerts.generateReminders(); long event = latest();
        when(email.send(eq(staff), anyString(), anyString(), anyString(), eq(event))).thenReturn(503);
        dispatcher.dispatchBatch(); dispatcher.dispatchBatch();
        verify(email, times(1)).send(eq(staff), anyString(), anyString(), anyString(), eq(event));
        for (int i = 0; i < 2; i++) {jdbc.update("UPDATE staff_alert_deliveries SET next_attempt_at = CURRENT_TIMESTAMP WHERE event_id = ?", event); dispatcher.dispatchBatch();}
        assertThat(jdbc.queryForObject("SELECT state FROM staff_alert_deliveries WHERE event_id = ? AND channel = 'EMAIL'", String.class, event)).isEqualTo("FAILED");
        verify(email, times(3)).send(eq(staff), anyString(), anyString(), anyString(), eq(event));
    }
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoWorkersAcceptOneDeliveryAndSourceRollbackNeverQueuesAnAlert() throws Exception {
        var transaction = new TransactionTemplate(manager);
        subscriptions.subscribe(staff, cookie, input("concurrent-" + UUID.randomUUID()));
        transaction.executeWithoutResult(status -> {alerts.paymentConfirmed(payment); status.setRollbackOnly();});
        assertThat(alerts.page(staff, null).messages()).isEmpty();
        transaction.executeWithoutResult(status -> alerts.paymentConfirmed(payment)); long event = latest();
        when(push.sendStaff(anyString(), anyString(), anyString(), eq(event), anyString(), anyString(), anyString())).thenReturn(201);
        var workers = Executors.newFixedThreadPool(2); var gate = new CountDownLatch(1);
        try {
            var first = workers.submit(() -> {gate.await(); dispatcher.dispatchBatch(); return null;});
            var second = workers.submit(() -> {gate.await(); dispatcher.dispatchBatch(); return null;}); gate.countDown();
            first.get(15, TimeUnit.SECONDS); second.get(15, TimeUnit.SECONDS);
            verify(push, times(1)).sendStaff(anyString(), anyString(), anyString(), eq(event), anyString(), anyString(), eq("/admin/orders/" + number));
        } finally {workers.shutdownNow();}
    }
    @AfterEach void cleanup() {
        flags.setStaffOrderAlerts(true);
        if (TransactionSynchronizationManager.isActualTransactionActive()) return;
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM staff_alert_deliveries WHERE event_id IN (SELECT id FROM staff_order_alerts WHERE order_id = ?)", order);
            jdbc.update("DELETE FROM staff_order_alert_reads WHERE event_id IN (SELECT id FROM staff_order_alerts WHERE order_id = ?)", order);
            jdbc.update("DELETE FROM staff_order_alerts WHERE order_id = ?", order);
            jdbc.update("DELETE FROM staff_push_subscriptions WHERE staff_id IN (?, ?)", staff, otherStaff);
            jdbc.update("DELETE FROM staff_sessions WHERE staff_id IN (?, ?)", staff, otherStaff);
            jdbc.update("DELETE FROM payments WHERE order_id = ?", order);
            jdbc.update("DELETE FROM orders WHERE id = ?", order); jdbc.update("DELETE FROM pickup_slots WHERE id = ?", slot);
            jdbc.update("DELETE FROM staff_branch_access WHERE staff_user_id IN (?, ?)", staff, otherStaff);
            jdbc.update("DELETE FROM staff_users WHERE id IN (?, ?)", staff, otherStaff);
            jdbc.update("DELETE FROM role_permissions WHERE role_id = ?", role); jdbc.update("DELETE FROM roles WHERE id = ?", role);
            jdbc.update("DELETE FROM branches WHERE id IN (?, ?)", branch, otherBranch);
        });
    }
}
