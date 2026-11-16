package com.gokulsweets.restaurant.staff.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.notification.WebPushTransport;
import com.gokulsweets.restaurant.security.StaffSessionService;
import com.gokulsweets.restaurant.staff.StaffUser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest(
        properties = {
            "gokul.features.staff-order-alerts=true",
                    "gokul.notifications.staff.scheduler-enabled=false",
            "gokul.environment-isolation.environment=DEV",
                    "gokul.environment-isolation.enabled=true",
            "gokul.web.environment-cors-enabled=true",
                    "gokul.environment-isolation.api-origin=https://api-staff-alerts-ci.example.invalid",
            "phonepe.redirect-url=https://dev.gokulsweets.in/checkout",
                    "phonepe.webhook-url=https://api-staff-alerts-ci.example.invalid/api/payments/webhooks/phonepe",
            "phonepe.webhook-checksum-key-id=staff-alert-test",
                    "cloudflare.r2.bucket-name=gokul-staff-alert-test",
            "cloudflare.r2.public-url=https://images-staff-alert-ci.example.invalid"
        })
@Transactional
class StaffOrderAlertsIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Autowired
    com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox customerInbox;

    @Autowired jakarta.persistence.EntityManager entities;
    @Autowired EnhancementProperties flags;
    @MockitoSpyBean StaffOrderAlerts alerts;
    @Autowired StaffAlertDispatcher dispatcher;
    @Autowired StaffPushSubscriptions subscriptions;
    @MockitoSpyBean StaffSessionService sessions;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean WebPushTransport push;
    @MockitoBean StaffAlertEmail email;
    long role, branch, otherBranch, staff, otherStaff, order, slot, payment;
    String number, cookie, publicKey;

    @BeforeEach
    void setup() throws Exception {
        flags.setStaffOrderAlerts(true);
        doReturn(LocalDateTime.of(2026, 10, 1, 16, 49)).when(alerts).now();
        role =
                jdbc.queryForObject(
                        "INSERT INTO roles(name) VALUES (?) RETURNING id",
                        Long.class,
                        "ALERT-" + UUID.randomUUID());
        jdbc.update(
                "INSERT INTO role_permissions(role_id, permission_id) SELECT ?, id FROM permissions"
                    + " WHERE name IN ('ORDER_VIEW','ORDER_START_PREPARATION','ORDER_MARK_READY')",
                role);
        branch =
                jdbc.queryForObject(
                        "INSERT INTO branches(code, name) VALUES (?, 'Alerts branch') RETURNING id",
                        Long.class,
                        "ALERT-" + UUID.randomUUID().toString().substring(0, 8));
        otherBranch =
                jdbc.queryForObject(
                        "INSERT INTO branches(code, name) VALUES (?, 'Other branch') RETURNING id",
                        Long.class,
                        "ALERT-" + UUID.randomUUID().toString().substring(0, 8));
        staff = staff(branch);
        otherStaff = staff(otherBranch);
        slot =
                jdbc.queryForObject(
                        "INSERT INTO pickup_slots(branch_id, slot_date, start_time, end_time,"
                            + " capacity) VALUES (?, '2026-10-01', '18:00', '18:30', 20) RETURNING"
                            + " id",
                        Long.class,
                        branch);
        number = "ALERT-" + UUID.randomUUID();
        order =
                jdbc.queryForObject(
                        """
INSERT INTO orders(order_number, branch_id, pickup_slot_id, customer_name, customer_phone, pickup_type, order_status, reservation_expires_at)
VALUES (?, ?, ?, 'Test', '9876543210', 'NORMAL', 'CONFIRMED', CURRENT_TIMESTAMP) RETURNING id
""",
                        Long.class,
                        number,
                        branch,
                        slot);
        payment =
                jdbc.queryForObject(
                        "INSERT INTO payments(order_id, provider, amount, payment_status) VALUES"
                                + " (?, 'PHONEPE', 100, 'PAID') RETURNING id",
                        Long.class,
                        order);
        cookie = issueFor(staff);
        when(push.configured()).thenReturn(true);
        when(email.configured()).thenReturn(true);
        when(email.recipient(staff)).thenReturn("staff-test@example.invalid");
        when(email.send(anyLong(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(200);
        if (java.security.Security.getProvider("BC") == null)
            java.security.Security.addProvider(
                    new org.bouncycastle.jce.provider.BouncyCastleProvider());
        var generator = java.security.KeyPairGenerator.getInstance("EC", "BC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        publicKey =
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                ((org.bouncycastle.jce.interfaces.ECPublicKey)
                                                generator.generateKeyPair().getPublic())
                                        .getQ()
                                        .getEncoded(false));
    }

    private String issueFor(long staffId) {
        StaffUser user = new StaffUser();
        user.setId(staffId);
        var staffRole = new com.gokulsweets.restaurant.staff.Role();
        staffRole.setName(
                jdbc.queryForObject("SELECT name FROM roles WHERE id = ?", String.class, role));
        user.setRole(staffRole);
        user.setUpdatedAt(
                jdbc.queryForObject(
                        "SELECT updated_at FROM staff_users WHERE id = ?",
                        LocalDateTime.class,
                        staff));
        return sessions.issue(user).token();
    }

    private long staff(long branch) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO staff_users(username, password_hash, full_name, role_id)"
                                + " VALUES (?, 'test-only', 'Alert staff', ?) RETURNING id",
                        Long.class,
                        "alert-" + UUID.randomUUID(),
                        role);
        jdbc.update(
                "INSERT INTO staff_branch_access(staff_user_id, branch_id) VALUES (?, ?)",
                id,
                branch);
        return id;
    }

    private StaffPushSubscriptions.Input input(String token) {
        return new StaffPushSubscriptions.Input(
                "https://fcm.googleapis.com/fcm/send/" + token,
                publicKey,
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]));
    }

    private void now(int hour, int minute) {
        doReturn(LocalDateTime.of(2026, 10, 1, hour, minute)).when(alerts).now();
    }

    private void status(String status) {
        jdbc.update("UPDATE orders SET order_status = ? WHERE id = ?", status, order);
        entities.clear();
    }

    private long latest() {
        return alerts.page(staff, null).messages().getFirst().event().id();
    }

    @Test
    void occasionRequestsUseScopedDeduplicatedStaffInboxAndPushTargets() throws Exception {
        jdbc.update(
                "INSERT INTO role_permissions(role_id,permission_id) SELECT ?,id FROM permissions"
                        + " WHERE name='APPROVAL_MANAGE'",
                role);
        subscriptions.subscribe(staff, cookie, input("occasion-" + UUID.randomUUID()));
        UUID subject = UUID.randomUUID(), request = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO verified_customer_subjects(id,environment,verified_phone)"
                        + " VALUES(?,'DEV','+919876543299')",
                subject);
        jdbc.update(
                "INSERT INTO"
                    + " occasion_enquiries(id,environment,subject_id,branch_id,occasion_type,service_date,guest_count,fulfilment,status)"
                    + " VALUES(?,'DEV',?,?,'Mixed gifts','2026-10-03',1000,'PICKUP','REQUESTED')",
                request,
                subject,
                branch);
        alerts.occasionChanged(request, false);
        alerts.occasionChanged(request, false);
        var inbox = alerts.page(staff, null);
        assertThat(inbox.messages()).hasSize(1);
        assertThat(inbox.unreadCount()).isEqualTo(1);
        var event = inbox.messages().getFirst().event();
        assertThat(event.enquiryId()).isEqualTo(request);
        assertThat(event.targetUrl())
                .isEqualTo("/admin/occasion-enquiries?branch=" + branch + "&enquiry=" + request);
        assertThat(inbox.messages().getFirst().actionRequired()).isTrue();
        assertThat(alerts.page(otherStaff, null).messages()).isEmpty();
        when(push.sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(event.id()),
                        anyString(),
                        anyString(),
                        eq(event.targetUrl()),
                        any(UUID.class)))
                .thenReturn(201);
        dispatcher.dispatchBatch();
        verify(push)
                .sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(event.id()),
                        anyString(),
                        anyString(),
                        eq(event.targetUrl()),
                        any(UUID.class));
        jdbc.update("UPDATE occasion_enquiries SET status='QUOTED' WHERE id=?", request);
        alerts.occasionReviewed(request);
        assertThat(alerts.page(staff, null).unreadCount()).isZero();
        assertThat(alerts.actionable(event)).isFalse();
    }

    @Test
    void markAllReadPreservesLaterAlertsOtherStaffAndUnresolvedActions() {
        alerts.paymentConfirmed(payment);
        long snapshot = alerts.page(staff, null).readThrough();
        now(18, 5);
        alerts.generateReminders();
        alerts.markAllRead(staff, snapshot);
        assertThat(alerts.page(staff, null).unreadCount()).isEqualTo(1);
        assertThat(alerts.page(staff, null).messages()).anyMatch(m -> m.actionRequired());
        alerts.markAllRead(staff, alerts.page(staff, null).readThrough());
        assertThat(alerts.page(staff, null).unreadCount()).isZero();
        assertThat(alerts.page(staff, null).messages()).anyMatch(m -> m.actionRequired());
        assertThat(alerts.page(otherStaff, null).messages()).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT order_status FROM orders WHERE id=?", String.class, order))
                .isEqualTo("CONFIRMED");
    }

    @Test
    void committedStagesAcknowledgeEligibleTaskAlertsButReadAloneDoesNotResolve() {
        long colleague = staff(branch);
        alerts.paymentConfirmed(payment);
        now(18, 5);
        alerts.generateReminders();
        alerts.markRead(staff, latest());
        assertThat(alerts.page(staff, null).messages()).anyMatch(m -> m.actionRequired());
        status("PREPARING");
        customerInbox.orderReady(order);
        assertThat(alerts.page(staff, null).unreadCount()).isZero();
        assertThat(alerts.page(colleague, null).unreadCount()).isZero();
        assertThat(alerts.page(otherStaff, null).messages()).isEmpty();
        alerts.generateReminders();
        assertThat(alerts.page(staff, null).messages().getFirst().event().kind())
                .isEqualTo("READY_OVERDUE");
        assertThat(alerts.page(staff, null).messages().getFirst().readAt()).isNull();
        status("READY_FOR_PICKUP");
        customerInbox.orderReady(order);
        customerInbox.orderReady(order);
        assertThat(alerts.page(staff, null).unreadCount()).isZero();
        assertThat(alerts.page(colleague, null).messages())
                .allMatch(m -> m.readAt() != null && !m.actionRequired());
    }

    @Test
    void paidReplayIsOneExactBranchEventAndReadIsIdempotentWithoutChangingOrder() throws Exception {
        jdbc.update("UPDATE payments SET payment_status = 'PENDING' WHERE id = ?", payment);
        alerts.paymentConfirmed(payment);
        assertThat(alerts.page(staff, null).messages()).isEmpty();
        jdbc.update("UPDATE payments SET payment_status = 'PAID' WHERE id = ?", payment);
        alerts.paymentConfirmed(payment);
        alerts.paymentConfirmed(payment);
        assertThat(alerts.page(staff, null).messages()).hasSize(1);
        Long customerNumber =
                jdbc.queryForObject(
                        "SELECT customer_order_number FROM orders WHERE id=?", Long.class, order);
        var event = alerts.page(staff, null).messages().getFirst().event();
        assertThat(event.message())
                .contains("#" + customerNumber, "06:00 PM", "IST")
                .doesNotContain(number);
        assertThat(event.customerOrderNumber()).isEqualTo(customerNumber);
        assertThat(event.orderNumber()).isEqualTo(number);
        assertThat(alerts.page(otherStaff, null).messages()).isEmpty();
        assertThatThrownBy(() -> alerts.markRead(otherStaff, latest()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        alerts.markRead(staff, latest());
        alerts.markRead(staff, latest());
        assertThat(alerts.page(staff, null).unreadCount()).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT order_status FROM orders WHERE id = ?",
                                String.class,
                                order))
                .isEqualTo("CONFIRMED");
        flags.setStaffOrderAlerts(false);
        alerts.paymentConfirmed(payment);
        alerts.generateReminders();
        dispatcher.dispatchBatch();
        verify(push, never())
                .sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(UUID.class));
    }

    @Test
    void reminderBoundariesUseExistingEligibilityAndActionSuppressesQueuedPush() throws Exception {
        subscriptions.subscribe(staff, cookie, input("reminder-" + UUID.randomUUID()));
        alerts.generateReminders();
        assertThat(alerts.page(staff, null).messages()).isEmpty();
        now(16, 50);
        alerts.generateReminders();
        alerts.generateReminders();
        assertThat(alerts.page(staff, null).messages()).hasSize(1);
        assertThat(alerts.page(staff, null).messages().getFirst().event().kind())
                .isEqualTo("PREPARATION_SOON");
        now(17, 0);
        alerts.generateReminders();
        assertThat(alerts.page(staff, null).messages().getFirst().event().kind())
                .isEqualTo("PREPARATION_DUE");
        status("PREPARING");
        dispatcher.dispatchBatch();
        verify(push, never())
                .sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(UUID.class));
        assertThat(alerts.page(staff, null).messages())
                .allMatch(message -> !message.actionRequired());
    }

    @Test
    void sharedPhonePausesPreviousStaffAndRequiresNewOptIn() {
        var browser = input("shared-" + UUID.randomUUID());
        var first = subscriptions.subscribe(staff, cookie, browser);
        var nextCookie = issueFor(otherStaff);
        jdbc.update(
                "UPDATE staff_sessions SET expires_at=CURRENT_TIMESTAMP - INTERVAL '1 minute'"
                        + " WHERE token_hash=?",
                StaffSessionService.hash(cookie));
        var pause =
                new StaffPushSubscriptions.Input(
                        browser.endpoint(),
                        browser.publicKey(),
                        browser.authSecret(),
                        null,
                        otherStaff,
                        true);
        assertThatThrownBy(
                        () ->
                                subscriptions.subscribe(
                                        otherStaff,
                                        nextCookie,
                                        new StaffPushSubscriptions.Input(
                                                browser.endpoint(),
                                                browser.publicKey(),
                                                browser.authSecret(),
                                                null,
                                                staff,
                                                true)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(subscriptions.subscribe(otherStaff, nextCookie, pause).id()).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT revoked_at IS NOT NULL FROM staff_push_subscriptions WHERE"
                                        + " id=?",
                                Boolean.class,
                                first.id()))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM staff_push_subscriptions WHERE staff_id=?",
                                Long.class,
                                otherStaff))
                .isZero();
        var second = subscriptions.subscribe(otherStaff, nextCookie, browser);
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(subscriptions.live(otherStaff, second.id(), nextCookie)).isTrue();
    }

    @Test
    void retryingTheSameSetupKeepsItsRegistrationAndRollbackOwnership() {
        var browser = input("setup-retry-" + UUID.randomUUID());
        var setupId = UUID.randomUUID();
        var request =
                new StaffPushSubscriptions.Input(
                        browser.endpoint(),
                        browser.publicKey(),
                        browser.authSecret(),
                        null,
                        staff,
                        false,
                        setupId);

        var initial = subscriptions.subscribe(staff, cookie, request);
        var retry = subscriptions.subscribe(staff, cookie, request);
        var anotherSetup = subscriptions.subscribe(staff, cookie, browser);

        assertThat(initial.id()).isEqualTo(setupId);
        assertThat(retry).isEqualTo(initial);
        assertThat(retry.created()).isTrue();
        assertThat(anotherSetup.id()).isEqualTo(setupId);
        assertThat(anotherSetup.created()).isFalse();
    }

    @Test
    void cancellingAReusedSetupRevokesItsCanonicalRegistration() {
        var browser = input("reused-setup-" + UUID.randomUUID());
        var existing = subscriptions.subscribe(staff, cookie, browser);
        var alias = UUID.randomUUID();
        var setup =
                new StaffPushSubscriptions.Input(
                        browser.endpoint(),
                        browser.publicKey(),
                        browser.authSecret(),
                        null,
                        staff,
                        false,
                        alias);
        var restore =
                new StaffPushSubscriptions.Input(
                        browser.endpoint(), browser.publicKey(), browser.authSecret(), alias);

        assertThat(subscriptions.subscribe(staff, cookie, setup))
                .isEqualTo(new StaffPushSubscriptions.Result(existing.id(), false));
        assertThat(subscriptions.subscribe(staff, cookie, setup).id()).isEqualTo(existing.id());

        sessions.signOutDevices(staff, java.util.List.of(alias), cookie);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT paused_at IS NOT NULL FROM staff_push_subscriptions WHERE"
                                        + " id=?",
                                Boolean.class,
                                existing.id()))
                .isTrue();
        var nextCookie = issueFor(staff);
        alerts.paymentConfirmed(payment);
        long event = latest();
        assertThat(subscriptions.subscribe(staff, nextCookie, restore))
                .isEqualTo(new StaffPushSubscriptions.Result(existing.id(), false, true));
        assertThat(subscriptions.live(staff, existing.id(), nextCookie)).isFalse();
        String catchUpCount =
                "SELECT COUNT(*) FROM staff_alert_deliveries WHERE event_id=?"
                        + " AND subscription_id=? AND catch_up";
        assertThat(jdbc.queryForObject(catchUpCount, Long.class, event, existing.id())).isZero();

        var ready =
                new StaffPushSubscriptions.Input(
                        browser.endpoint(),
                        browser.publicKey(),
                        browser.authSecret(),
                        existing.id());
        assertThat(subscriptions.subscribe(staff, nextCookie, ready))
                .isEqualTo(new StaffPushSubscriptions.Result(existing.id(), false));
        assertThat(subscriptions.live(staff, existing.id(), nextCookie)).isTrue();
        assertThat(jdbc.queryForObject(catchUpCount, Long.class, event, existing.id()))
                .isEqualTo(1L);
        subscriptions.revoke(staff, alias);
        assertThat(subscriptions.live(staff, existing.id(), nextCookie)).isFalse();

        var replacement = subscriptions.subscribe(staff, nextCookie, browser);
        subscriptions.revoke(staff, alias);
        assertThat(subscriptions.live(staff, replacement.id(), nextCookie)).isTrue();

        for (var request : java.util.List.of(setup, restore)) {
            assertThatThrownBy(() -> subscriptions.subscribe(staff, nextCookie, request))
                    .isInstanceOfSatisfying(
                            org.springframework.web.server.ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(410));
        }
    }

    @Test
    void disableBeforeSetupCommitsRemainsIdempotentAndBlocksTheOldId() {
        var browser = input("cancelled-setup-" + UUID.randomUUID());
        var cancelledId = UUID.randomUUID();
        var late =
                new StaffPushSubscriptions.Input(
                        browser.endpoint(),
                        browser.publicKey(),
                        browser.authSecret(),
                        null,
                        staff,
                        false,
                        cancelledId);

        subscriptions.revoke(staff, cancelledId);
        subscriptions.revoke(staff, cancelledId);

        assertThatThrownBy(() -> subscriptions.subscribe(staff, cookie, late))
                .isInstanceOfSatisfying(
                        org.springframework.web.server.ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(410));
        assertThat(subscriptions.live(staff, cancelledId, cookie)).isFalse();

        var fresh = subscriptions.subscribe(staff, cookie, browser);
        assertThat(fresh.id()).isNotEqualTo(cancelledId);
        assertThat(subscriptions.live(staff, fresh.id(), cookie)).isTrue();
    }

    @Test
    void revokedRegistrationCannotBeRestoredOrReusedAsASetupId() {
        var browser = input("revoked-setup-" + UUID.randomUUID());
        var registration = subscriptions.subscribe(staff, cookie, browser);
        var restore =
                new StaffPushSubscriptions.Input(
                        browser.endpoint(),
                        browser.publicKey(),
                        browser.authSecret(),
                        registration.id());
        var replay =
                new StaffPushSubscriptions.Input(
                        browser.endpoint(),
                        browser.publicKey(),
                        browser.authSecret(),
                        null,
                        staff,
                        false,
                        registration.id());

        subscriptions.revoke(staff, registration.id());

        for (var request : java.util.List.of(restore, replay)) {
            assertThatThrownBy(() -> subscriptions.subscribe(staff, cookie, request))
                    .isInstanceOfSatisfying(
                            org.springframework.web.server.ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(410));
        }
        assertThat(subscriptions.live(staff, registration.id(), cookie)).isFalse();
        assertThat(subscriptions.subscribe(staff, cookie, browser).id())
                .isNotEqualTo(registration.id());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void setupVerifiedBeforeSignOutCannotReplaceTheNewSessionsRegistration() throws Exception {
        var checked = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        var browser = input("delayed-setup-" + UUID.randomUUID());
        doAnswer(
                        invocation -> {
                            var verified = invocation.callRealMethod();
                            checked.countDown();
                            if (!resume.await(10, TimeUnit.SECONDS))
                                throw new IllegalStateException("Setup did not resume");
                            return verified;
                        })
                .when(sessions)
                .verify(cookie);

        var worker = Executors.newSingleThreadExecutor();
        try {
            var delayed =
                    worker.submit(
                            () -> {
                                try {
                                    subscriptions.subscribe(staff, cookie, browser);
                                    return 200;
                                } catch (
                                        org.springframework.web.server.ResponseStatusException
                                                error) {
                                    return error.getStatusCode().value();
                                }
                            });
            assertThat(checked.await(10, TimeUnit.SECONDS)).isTrue();
            sessions.signOut(staff, null, cookie);
            var current = issueFor(staff);
            var registration = subscriptions.subscribe(staff, current, browser);
            resume.countDown();

            assertThat(delayed.get(10, TimeUnit.SECONDS)).isEqualTo(401);
            assertThat(subscriptions.live(staff, registration.id(), current)).isTrue();
        } finally {
            resume.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void signingOutPausesSavedAndStagedRegistrationsButLeavesOtherPhonesActive() {
        var remembered =
                subscriptions.subscribe(staff, cookie, input("saved-" + UUID.randomUUID()));
        var staged = subscriptions.subscribe(staff, cookie, input("staged-" + UUID.randomUUID()));
        var otherCookie = issueFor(staff);
        var other =
                subscriptions.subscribe(staff, otherCookie, input("other-" + UUID.randomUUID()));

        sessions.signOutDevices(staff, java.util.List.of(remembered.id(), staged.id()), cookie);

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM staff_push_subscriptions WHERE id IN (?,?)"
                                        + " AND paused_at IS NOT NULL",
                                Long.class,
                                remembered.id(),
                                staged.id()))
                .isEqualTo(2);
        assertThat(sessions.verify(cookie)).isNull();
        assertThat(subscriptions.live(staff, other.id(), otherCookie)).isTrue();
    }

    @Test
    void signingOutPausesTheOlderRegistrationWithoutRevokingAnotherPhone() {
        var browser = input("old-device-" + UUID.randomUUID());
        var old = subscriptions.subscribe(staff, cookie, browser);
        var otherCookie = issueFor(staff);
        var other =
                subscriptions.subscribe(
                        staff, otherCookie, input("other-device-" + UUID.randomUUID()));
        var newCookie = issueFor(staff);

        sessions.signOut(staff, old.id(), newCookie);

        assertThat(sessions.verify(newCookie)).isNull();
        assertThat(subscriptions.live(staff, old.id(), cookie)).isFalse();
        assertThat(subscriptions.live(staff, other.id(), otherCookie)).isTrue();
        var nextLogin = issueFor(staff);
        var restoration =
                new StaffPushSubscriptions.Input(
                        browser.endpoint(), browser.publicKey(), browser.authSecret(), old.id());
        assertThat(subscriptions.subscribe(staff, nextLogin, restoration).id()).isEqualTo(old.id());
        assertThat(subscriptions.live(staff, old.id(), nextLogin)).isTrue();
        subscriptions.revoke(staff, old.id());
        assertThatThrownBy(() -> subscriptions.subscribe(staff, nextLogin, restoration))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void expiredStaffSessionCannotQueueOrSendPhoneAlerts() throws Exception {
        var device =
                subscriptions.subscribe(staff, cookie, input("expired-phone-" + UUID.randomUUID()));
        jdbc.update(
                "UPDATE staff_sessions SET expires_at=CURRENT_TIMESTAMP - INTERVAL '1 minute'"
                        + " WHERE token_hash=?",
                StaffSessionService.hash(cookie));
        assertThat(sessions.verify(cookie)).isNull();
        alerts.paymentConfirmed(payment);
        long paid = latest();
        dispatcher.dispatchBatch();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM staff_alert_deliveries WHERE event_id=? AND"
                                        + " subscription_id=?",
                                Long.class,
                                paid,
                                device.id()))
                .isZero();

        jdbc.update(
                "INSERT INTO staff_alert_deliveries(event_id, staff_id, channel, subscription_id)"
                        + " VALUES (?, ?, 'PUSH', ?)",
                paid,
                staff,
                device.id());
        dispatcher.dispatchBatch();
        verify(push, never())
                .sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(paid),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(UUID.class));
    }

    @Test
    void sameAccountSignInTransfersPhoneBeforeOldSessionIsRevoked() throws Exception {
        String username =
                jdbc.queryForObject(
                        "SELECT username FROM staff_users WHERE id=?", String.class, staff);
        String password = "Staff-password-2026";
        jdbc.update(
                "UPDATE staff_users SET password_hash=? WHERE id=?",
                passwords.encode(password),
                staff);
        String oldCookie = issueFor(staff);
        var device =
                subscriptions.subscribe(
                        staff, oldCookie, input("rotated-phone-" + UUID.randomUUID()));
        var otherCookie = issueFor(staff);
        var otherDevice =
                subscriptions.subscribe(
                        staff, otherCookie, input("other-phone-" + UUID.randomUUID()));
        alerts.paymentConfirmed(payment);

        var next = sessions.login(username, password, null, "127.0.0.1", oldCookie);

        assertThat(sessions.verify(oldCookie)).isNull();
        assertThat(subscriptions.live(staff, device.id(), next.token())).isTrue();
        assertThat(subscriptions.live(staff, otherDevice.id(), otherCookie)).isTrue();
        dispatcher.dispatchBatch();
        long event = latest();
        verify(push)
                .sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(event),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(device.id()));
    }

    @Test
    void restoredPhoneAlertsOnceForAnOlderOrderThatStillNeedsAction() throws Exception {
        var browser = input("catch-up-phone-" + UUID.randomUUID());
        var device = subscriptions.subscribe(staff, cookie, browser);
        var restoration =
                new StaffPushSubscriptions.Input(
                        browser.endpoint(), browser.publicKey(), browser.authSecret(), device.id());
        when(push.sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(device.id())))
                .thenReturn(201);
        sessions.signOut(staff, device.id(), cookie);
        alerts.paymentConfirmed(payment);
        long event = latest();
        jdbc.update(
                "UPDATE staff_order_alerts SET created_at=CURRENT_TIMESTAMP - INTERVAL '1 hour'"
                        + " WHERE id=?",
                event);
        dispatcher.dispatchBatch();
        verify(push, never())
                .sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(event),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(device.id()));

        var next = issueFor(staff);
        subscriptions.subscribe(staff, next, restoration);
        dispatcher.dispatchBatch();
        sessions.signOut(staff, device.id(), next);
        subscriptions.subscribe(staff, issueFor(staff), restoration);
        dispatcher.dispatchBatch();

        verify(push, times(1))
                .sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(event),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(device.id()));
    }

    @Test
    void restoredPhoneSkipsCatchUpWhenAnotherDeviceStartsTheOrder() throws Exception {
        var browser = input("handled-order-" + UUID.randomUUID());
        var device = subscriptions.subscribe(staff, cookie, browser);
        sessions.signOut(staff, device.id(), cookie);
        alerts.paymentConfirmed(payment);
        var restoration =
                new StaffPushSubscriptions.Input(
                        browser.endpoint(), browser.publicKey(), browser.authSecret(), device.id());
        subscriptions.subscribe(staff, issueFor(staff), restoration);

        status("PREPARING");
        dispatcher.dispatchBatch();
        long event = latest();
        verify(push, never())
                .sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(event),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(device.id()));
    }

    @Test
    void reservesOneCatchUpSlotAfterLivePreparationAlertsFillTheBatch() throws Exception {
        var browser = input("priority-phone-" + UUID.randomUUID());
        var device = subscriptions.subscribe(staff, cookie, browser);
        sessions.signOut(staff, device.id(), cookie);
        alerts.paymentConfirmed(payment);
        long paid = latest();
        subscriptions.subscribe(
                staff,
                issueFor(staff),
                new StaffPushSubscriptions.Input(
                        browser.endpoint(),
                        browser.publicKey(),
                        browser.authSecret(),
                        device.id()));
        now(17, 0);
        alerts.generateReminders();
        long due = latest();
        jdbc.update(
                """
INSERT INTO staff_order_alerts(environment, event_key, order_id, branch_id, kind, required_permission, title, message, scheduled_at)
SELECT e.environment, e.event_key || ':test:' || i, e.order_id, e.branch_id, e.kind, e.required_permission, e.title, e.message, e.scheduled_at
FROM staff_order_alerts e CROSS JOIN generate_series(1, 9) AS slot(i) WHERE e.id=?
""",
                due);

        dispatcher.dispatchBatch();

        assertThat(due).isNotEqualTo(paid);
        var order = inOrder(push);
        order.verify(push, times(9))
                .sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        longThat(id -> id != paid),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(device.id()));
        order.verify(push)
                .sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(paid),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(device.id()));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM staff_alert_deliveries WHERE"
                                        + " subscription_id=? AND state='QUEUED' AND NOT catch_up",
                                Long.class,
                                device.id()))
                .isEqualTo(1);
    }

    @Test
    void skippingAPausedPhonePreservesAttemptsAndLastFailure() throws Exception {
        var browser = input("paused-retry-" + UUID.randomUUID());
        var device = subscriptions.subscribe(staff, cookie, browser);
        alerts.paymentConfirmed(payment);
        long event = latest();
        jdbc.update(
                """
INSERT INTO staff_alert_deliveries(event_id, staff_id, channel, subscription_id, attempts, last_http_status)
VALUES (?, ?, 'PUSH', ?, 2, 503)
""",
                event,
                staff,
                device.id());
        sessions.signOut(staff, device.id(), cookie);

        dispatcher.dispatchBatch();
        assertThat(
                        jdbc.queryForMap(
                                "SELECT state, attempts, last_http_status FROM"
                                        + " staff_alert_deliveries WHERE event_id=? AND"
                                        + " subscription_id=?",
                                event,
                                device.id()))
                .containsEntry("state", "SKIPPED")
                .containsEntry("attempts", 2)
                .containsEntry("last_http_status", 503);
        subscriptions.subscribe(
                staff,
                issueFor(staff),
                new StaffPushSubscriptions.Input(
                        browser.endpoint(),
                        browser.publicKey(),
                        browser.authSecret(),
                        device.id()));

        assertThat(
                        jdbc.queryForMap(
                                "SELECT state, attempts, last_http_status FROM"
                                        + " staff_alert_deliveries WHERE event_id=? AND"
                                        + " subscription_id=?",
                                event,
                                device.id()))
                .containsEntry("state", "QUEUED")
                .containsEntry("attempts", 2)
                .containsEntry("last_http_status", 503);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"503, 1", "429, 1", "400, 0"})
    void reconnectGrantsOnlyOneExtraAttemptForAnExhaustedTransientFailure(
            int previousStatus, int extraAttempts) throws Exception {
        var browser = input("retry-phone-" + UUID.randomUUID());
        var device = subscriptions.subscribe(staff, cookie, browser);
        var restoration =
                new StaffPushSubscriptions.Input(
                        browser.endpoint(), browser.publicKey(), browser.authSecret(), device.id());
        alerts.paymentConfirmed(payment);
        long event = latest();
        jdbc.update(
                """
INSERT INTO staff_alert_deliveries(event_id, staff_id, channel, subscription_id, state, attempts, last_http_status)
VALUES (?, ?, 'PUSH', ?, 'FAILED', 3, ?)
""",
                event,
                staff,
                device.id(),
                previousStatus);
        when(push.sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(event),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(device.id())))
                .thenReturn(503);

        sessions.signOut(staff, device.id(), cookie);
        String next = issueFor(staff);
        subscriptions.subscribe(staff, next, restoration);
        dispatcher.dispatchBatch();
        sessions.signOut(staff, device.id(), next);
        subscriptions.subscribe(staff, issueFor(staff), restoration);
        dispatcher.dispatchBatch();

        verify(push, times(extraAttempts))
                .sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(event),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(device.id()));
    }

    @Test
    void readingADueReminderStillSendsItsPhoneAlertUntilPreparationStarts() throws Exception {
        subscriptions.subscribe(staff, cookie, input("read-due-" + UUID.randomUUID()));
        now(17, 0);
        alerts.generateReminders();
        long due = latest();
        alerts.markRead(staff, due);

        dispatcher.dispatchBatch();

        verify(push)
                .sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(due),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(UUID.class));
        now(17, 2);
        alerts.generateReminders();
        status("PREPARING");
        dispatcher.dispatchBatch();
        verify(push, times(1))
                .sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(UUID.class));
    }

    @Test
    void reminderCrossesIndiaMidnightWithoutUsingBrowserOrServerTimezone() {
        jdbc.update(
                "UPDATE pickup_slots SET start_time = '00:30', end_time = '01:00' WHERE id = ?",
                slot);
        doReturn(LocalDateTime.of(2026, 9, 30, 23, 19)).when(alerts).now();
        alerts.generateReminders();
        assertThat(alerts.page(staff, null).messages()).isEmpty();
        doReturn(LocalDateTime.of(2026, 9, 30, 23, 20)).when(alerts).now();
        alerts.generateReminders();
        assertThat(alerts.page(staff, null).messages().getFirst().event().kind())
                .isEqualTo("PREPARATION_SOON");
        doReturn(LocalDateTime.of(2026, 9, 30, 23, 30)).when(alerts).now();
        alerts.generateReminders();
        assertThat(alerts.page(staff, null).messages().getFirst().event().kind())
                .isEqualTo("PREPARATION_DUE");
    }

    @Test
    void readDoesNotStopOverdueEmailButPreparationAndReadyActionsDo() throws Exception {
        now(18, 0);
        alerts.generateReminders();
        long waiting = latest();
        alerts.markRead(staff, waiting);
        now(18, 4);
        dispatcher.dispatchBatch();
        verify(email, never()).send(anyLong(), anyString(), anyString(), anyString(), anyLong());
        now(18, 5);
        dispatcher.dispatchBatch();
        dispatcher.dispatchBatch();
        verify(email, times(1)).send(eq(staff), anyString(), anyString(), eq(number), eq(waiting));
        verify(email, never())
                .send(eq(otherStaff), anyString(), anyString(), anyString(), anyLong());
        status("PREPARING");
        alerts.generateReminders();
        long ready = latest();
        status("READY_FOR_PICKUP");
        dispatcher.dispatchBatch();
        verify(email, never()).send(eq(staff), anyString(), anyString(), anyString(), eq(ready));
        assertThat(alerts.page(staff, null).messages())
                .allMatch(message -> !message.actionRequired());
    }

    @Test
    void liveBrowserRegistrationIsIdempotentAndPermissionRevocationOrGoneEndpointStopsSend()
            throws Exception {
        var input = input("live-" + UUID.randomUUID());
        var registered = subscriptions.subscribe(staff, cookie, input);
        assertThat(subscriptions.subscribe(staff, cookie, input).id()).isEqualTo(registered.id());
        assertThatThrownBy(() -> subscriptions.subscribe(otherStaff, cookie, input))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        alerts.paymentConfirmed(payment);
        long event = latest();
        jdbc.update(
                "DELETE FROM role_permissions WHERE role_id = ? AND permission_id = (SELECT id FROM"
                        + " permissions WHERE name = 'ORDER_VIEW')",
                role);
        dispatcher.dispatchBatch();
        verify(push, never())
                .sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(UUID.class));
        jdbc.update(
                "INSERT INTO role_permissions(role_id, permission_id) SELECT ?, id FROM permissions"
                        + " WHERE name = 'ORDER_VIEW'",
                role);
        when(push.sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(event),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(UUID.class)))
                .thenReturn(410);
        dispatcher.dispatchBatch();
        assertThat(subscriptions.live(staff, registered.id(), cookie)).isFalse();
        subscriptions.revoke(staff, registered.id());
        subscriptions.revoke(staff, registered.id());
        sessions.revoke(cookie);
        assertThatThrownBy(() -> subscriptions.subscribe(staff, cookie, input("revoked")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void emailRetriesAreBounded() throws Exception {
        boolean recurring = reminderProperties.isRecurringPreparationReminders();
        reminderProperties.setRecurringPreparationReminders(true);
        try {
            now(18, 5);
            alerts.generateReminders();
            long event = latest();
            when(email.send(eq(staff), anyString(), anyString(), anyString(), eq(event)))
                    .thenReturn(503);
            dispatcher.dispatchBatch();
            dispatcher.dispatchBatch();
            verify(email, times(1))
                    .send(eq(staff), anyString(), anyString(), anyString(), eq(event));
            for (int i = 0; i < 2; i++) {
                jdbc.update(
                        "UPDATE staff_alert_deliveries SET next_attempt_at = CURRENT_TIMESTAMP"
                                + " WHERE event_id = ?",
                        event);
                dispatcher.dispatchBatch();
            }
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT state FROM staff_alert_deliveries WHERE event_id = ?"
                                            + " AND channel = 'EMAIL'",
                                    String.class,
                                    event))
                    .isEqualTo("FAILED");
            verify(email, times(3))
                    .send(eq(staff), anyString(), anyString(), anyString(), eq(event));
        } finally {
            reminderProperties.setRecurringPreparationReminders(recurring);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoWorkersAcceptOneDeliveryAndSourceRollbackNeverQueuesAnAlert() throws Exception {
        var transaction = new TransactionTemplate(manager);
        subscriptions.subscribe(staff, cookie, input("concurrent-" + UUID.randomUUID()));
        transaction.executeWithoutResult(
                status -> {
                    alerts.paymentConfirmed(payment);
                    status.setRollbackOnly();
                });
        assertThat(alerts.page(staff, null).messages()).isEmpty();
        transaction.executeWithoutResult(status -> alerts.paymentConfirmed(payment));
        long event = latest();
        when(push.sendStaff(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(event),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(UUID.class)))
                .thenReturn(201);
        var workers = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var first =
                    workers.submit(
                            () -> {
                                gate.await();
                                dispatcher.dispatchBatch();
                                return null;
                            });
            var second =
                    workers.submit(
                            () -> {
                                gate.await();
                                dispatcher.dispatchBatch();
                                return null;
                            });
            gate.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            verify(push, times(1))
                    .sendStaff(
                            anyString(),
                            anyString(),
                            anyString(),
                            eq(event),
                            anyString(),
                            anyString(),
                            eq("/admin/orders/" + number),
                            any(UUID.class));
        } finally {
            workers.shutdownNow();
        }
    }

    @AfterEach
    void cleanup() {
        flags.setStaffOrderAlerts(true);
        if (TransactionSynchronizationManager.isActualTransactionActive()) return;
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        status -> {
                            jdbc.update(
                                    "DELETE FROM staff_alert_deliveries WHERE event_id IN (SELECT"
                                            + " id FROM staff_order_alerts WHERE order_id = ?)",
                                    order);
                            jdbc.update(
                                    "DELETE FROM staff_order_alert_reads WHERE event_id IN (SELECT"
                                            + " id FROM staff_order_alerts WHERE order_id = ?)",
                                    order);
                            jdbc.update("DELETE FROM staff_order_alerts WHERE order_id = ?", order);
                            jdbc.update(
                                    "DELETE FROM staff_push_subscriptions WHERE staff_id IN (?, ?)",
                                    staff,
                                    otherStaff);
                            jdbc.update(
                                    "DELETE FROM staff_sessions WHERE staff_id IN (?, ?)",
                                    staff,
                                    otherStaff);
                            jdbc.update("DELETE FROM payments WHERE order_id = ?", order);
                            jdbc.update("DELETE FROM orders WHERE id = ?", order);
                            jdbc.update("DELETE FROM pickup_slots WHERE id = ?", slot);
                            jdbc.update(
                                    "DELETE FROM staff_branch_access WHERE staff_user_id IN (?, ?)",
                                    staff,
                                    otherStaff);
                            jdbc.update(
                                    "DELETE FROM staff_users WHERE id IN (?, ?)",
                                    staff,
                                    otherStaff);
                            jdbc.update("DELETE FROM role_permissions WHERE role_id = ?", role);
                            jdbc.update("DELETE FROM roles WHERE id = ?", role);
                            jdbc.update(
                                    "DELETE FROM branches WHERE id IN (?, ?)", branch, otherBranch);
                        });
    }

    @Autowired StaffAlertProperties reminderProperties;

    @Test
    void lateFirstOverdueReminderEscalatesOnceEvenWithRecurringPush() throws Exception {
        boolean recurring = reminderProperties.isRecurringPreparationReminders();
        reminderProperties.setRecurringPreparationReminders(true);
        try {
            now(18, 5);
            alerts.generateReminders();
            long first = latest();
            dispatcher.dispatchBatch();
            now(18, 7);
            alerts.generateReminders();
            dispatcher.dispatchBatch();
            verify(email, times(1))
                    .send(eq(staff), anyString(), anyString(), eq(number), eq(first));
            verify(email, times(1))
                    .send(anyLong(), anyString(), anyString(), anyString(), anyLong());
        } finally {
            reminderProperties.setRecurringPreparationReminders(recurring);
        }
    }

    @Test
    void preparingOverdueRemainsActionableAfterADayUntilMarkedReady() {
        boolean recurring = reminderProperties.isRecurringPreparationReminders();
        reminderProperties.setRecurringPreparationReminders(true);
        try {
            status("PREPARING");
            doReturn(LocalDateTime.of(2026, 10, 3, 18, 0)).when(alerts).now();
            alerts.generateReminders();
            var event = alerts.page(staff, null).messages().getFirst().event();
            assertThat(event.kind()).isEqualTo("READY_OVERDUE");
            assertThat(alerts.actionable(event)).isTrue();
            status("READY_FOR_PICKUP");
            assertThat(alerts.actionable(event)).isFalse();
        } finally {
            reminderProperties.setRecurringPreparationReminders(recurring);
        }
    }

    @Test
    void recurringDueNotificationsDeduplicateAndStopAfterPreparationStarts() {
        boolean recurring = reminderProperties.isRecurringPreparationReminders();
        int minutes = reminderProperties.getRepeatMinutes();
        reminderProperties.setRecurringPreparationReminders(true);
        reminderProperties.setRepeatMinutes(2);
        try {
            doReturn(LocalDateTime.of(2026, 10, 1, 17, 0)).when(alerts).now();
            alerts.generateReminders();
            alerts.generateReminders();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM staff_order_alerts WHERE order_id=? AND"
                                            + " kind='PREPARATION_DUE'",
                                    Long.class,
                                    order))
                    .isEqualTo(1);
            doReturn(LocalDateTime.of(2026, 10, 1, 17, 2)).when(alerts).now();
            alerts.generateReminders();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM staff_order_alerts WHERE order_id=? AND"
                                            + " kind='PREPARATION_DUE'",
                                    Long.class,
                                    order))
                    .isEqualTo(2);
            doReturn(LocalDateTime.of(2026, 10, 3, 18, 0)).when(alerts).now();
            alerts.generateReminders();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM staff_order_alerts WHERE order_id=? AND"
                                            + " kind='PREPARATION_OVERDUE'",
                                    Long.class,
                                    order))
                    .isEqualTo(1);
            jdbc.update("UPDATE orders SET order_status='PREPARING' WHERE id=?", order);
            entities.clear();
            doReturn(LocalDateTime.of(2026, 10, 1, 17, 4)).when(alerts).now();
            alerts.generateReminders();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM staff_order_alerts WHERE order_id=? AND"
                                            + " kind='PREPARATION_DUE'",
                                    Long.class,
                                    order))
                    .isEqualTo(2);
        } finally {
            reminderProperties.setRecurringPreparationReminders(recurring);
            reminderProperties.setRepeatMinutes(minutes);
        }
    }
}
