package com.gokulsweets.restaurant.marketing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.customer.consent.*;
import com.gokulsweets.restaurant.customer.identity.VerifiedCustomerSessionStore;
import com.gokulsweets.restaurant.customer.notification.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@SpringBootTest(
        properties = {
            "gokul.features.notification-inbox=true",
            "gokul.features.notification-alerts=true",
            "gokul.features.customer-otp-identity=true",
            "gokul.features.customer-consent-controls=true",
            "gokul.notifications.marketing.enabled=true",
            "gokul.notifications.web-push.scheduler-enabled=false",
            "gokul.identity.provider-abuse-controls-verified=true",
            "gokul.consent.policy-version=marketing-test",
            "gokul.environment-isolation.environment=DEV",
            "gokul.environment-isolation.enabled=true",
            "gokul.web.environment-cors-enabled=true",
            "gokul.environment-isolation.api-origin=https://api-marketing-ci.example.invalid",
            "phonepe.redirect-url=https://dev.gokulsweets.in/checkout",
            "phonepe.webhook-url=https://api-marketing-ci.example.invalid/api/payments/webhooks/phonepe",
            "phonepe.webhook-checksum-key-id=marketing-test-key",
            "cloudflare.r2.bucket-name=gokul-marketing-test",
            "cloudflare.r2.public-url=https://images-marketing-ci.example.invalid"
        })
@Transactional
class MarketingPushIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;
    @Autowired CustomerAlertPreferences alerts;
    @Autowired OptionalProcessingGate gate;
    @Autowired ConsentLedger ledger;
    @Autowired TransactionTemplate transactions;
    @Autowired VerifiedCustomerSessionStore sessions;
    @Autowired WebPushProperties properties;
    @MockitoBean WebPushTransport transport;
    final Instant now = Instant.parse("2026-10-21T10:45:00Z");
    final UUID subject = UUID.randomUUID();
    MarketingPushService marketing;
    MarketingPushDispatcher dispatcher;
    long branch, order, category;
    UUID subscription;

    @BeforeEach
    void setup() {
        when(transport.configured()).thenReturn(true);
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        marketing =
                new MarketingPushService(
                        jdbc, environment, alerts, gate, transport, transactions, clock);
        dispatcher =
                new MarketingPushDispatcher(
                        marketing, jdbc, alerts, gate, transport, transactions, properties, clock);
        branch =
                jdbc.queryForObject(
                        "INSERT INTO branches(code,name,opening_time,closing_time) VALUES"
                                + " (?,'Marketing test','08:00','21:30') RETURNING id",
                        Long.class,
                        "MKT-" + UUID.randomUUID());
        category =
                jdbc.queryForObject(
                        "INSERT INTO categories(code,name) VALUES (?,'Marketing sweets') RETURNING"
                                + " id",
                        Long.class,
                        "MKT-" + UUID.randomUUID());
        long product =
                jdbc.queryForObject(
                        "INSERT INTO products(code,category_id,name,base_price) VALUES (?,?,'Test"
                                + " sweet',20) RETURNING id",
                        Long.class,
                        "MKT-" + UUID.randomUUID(),
                        category);
        jdbc.update(
                "INSERT INTO branch_products(branch_id,product_id) VALUES (?,?)", branch, product);
        jdbc.update(
                "INSERT INTO verified_customer_subjects(id,environment,verified_phone) VALUES"
                        + " (?,'DEV','+919876543210')",
                subject);
        // Both issuance and expiry belong to the fixture clock. Issuance at wall-clock
        // time makes the expired-session case violate expires_at > issued_at on later days.
        sessions.issue(ConsentEnvironment.DEV, subject, now.minus(Duration.ofDays(1)));
        long session =
                jdbc.queryForObject(
                        "SELECT id FROM verified_customer_sessions WHERE verified_subject_id=?"
                                + " ORDER BY id DESC LIMIT 1",
                        Long.class,
                        subject);
        jdbc.update(
                "UPDATE verified_customer_sessions SET expires_at=? WHERE id=?",
                Timestamp.from(now.plus(Duration.ofDays(30))),
                session);
        subscription = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " customer_push_subscriptions(id,environment,subject_id,session_id,endpoint,endpoint_hash,public_key,auth_secret)"
                    + " VALUES (?,'DEV',?,?,?,?,'test-key','test-auth')",
                subscription,
                subject,
                session,
                "https://fcm.googleapis.com/test-" + subscription,
                UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", ""));
        jdbc.update(
                "INSERT INTO"
                    + " customer_notification_preferences(environment,subject_id,offer_inbox_enabled)"
                    + " VALUES ('DEV',?,true)",
                subject);
        ledger.record(
                ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING, "marketing-test", true);
        long slot =
                jdbc.queryForObject(
                        "INSERT INTO pickup_slots(branch_id,slot_date,start_time,end_time,capacity)"
                                + " VALUES (?,'2026-10-11','12:00','12:30',20) RETURNING id",
                        Long.class,
                        branch);
        order =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " orders(order_number,branch_id,pickup_slot_id,customer_name,customer_phone,pickup_type,order_status,created_at,reservation_expires_at)"
                            + " VALUES"
                            + " (?,?,?,'Test','9876543210','NORMAL','PICKED_UP',?,CURRENT_TIMESTAMP)"
                            + " RETURNING id",
                        Long.class,
                        "MKT-" + UUID.randomUUID(),
                        branch,
                        slot,
                        Timestamp.valueOf(
                                LocalDateTime.ofInstant(
                                        now.minus(Duration.ofDays(10)), MarketingCopy.INDIA)));
        jdbc.update(
                "INSERT INTO verified_order_ownership(order_id,environment,verified_subject_id)"
                        + " VALUES (?,'DEV',?)",
                order,
                subject);
        jdbc.update(
                "INSERT INTO"
                    + " order_items(order_id,product_id,product_name,quantity,unit_price,line_total)"
                    + " VALUES (?,?,'Test sweet',1,20,20)",
                order,
                product);
        marketing.save(
                branch,
                new MarketingPushService.Settings(true, "HINGLISH", 3, true, true, true, true, 0));
    }

    @Test
    void intelligenceChoosesHistoricalFavouriteAndDailyReservationIsIdempotent() throws Exception {
        assertThat(marketing.evaluate(branch)).isEqualTo(1);
        assertThat(marketing.evaluate(branch)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT topic FROM marketing_push_deliveries WHERE subject_id=?",
                                String.class,
                                subject))
                .isEqualTo("SWEETS");
        when(transport.sendMarketing(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenReturn(201);
        dispatcher.dispatchBatch();
        dispatcher.dispatchBatch();
        verify(transport, times(1))
                .sendMarketing(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        eq("/branches/" + branch));
        assertThat(state()).isEqualTo("ACCEPTED");
    }

    @Test
    void withdrawalAfterQueueSkipsDelivery() throws Exception {
        assertThat(marketing.evaluate(branch)).isEqualTo(1);
        ledger.record(
                ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING, "marketing-test", false);
        dispatcher.dispatchBatch();
        assertThat(state()).isEqualTo("SKIPPED");
        verify(transport, never())
                .sendMarketing(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString());
    }

    @Test
    void pauseAndStaleAdminVersionCannotAccidentallyReenableCampaigns() {
        var saved = marketing.settings(branch);
        marketing.save(
                branch,
                new MarketingPushService.Settings(
                        false, "EN", 1, true, true, true, true, saved.version()));
        assertThatThrownBy(() -> marketing.save(branch, saved))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(marketing.evaluate(branch)).isZero();
    }

    @Test
    void quietHoursRecentOrdersAndClosedBranchesSuppressReservations() {
        alerts.save("DEV", subject, new CustomerAlertPreferences.Input(false, true, 900, 1100));
        assertThat(marketing.evaluate(branch)).isZero();
        alerts.save("DEV", subject, new CustomerAlertPreferences.Input(false, false, 1320, 480));
        jdbc.update(
                "UPDATE orders SET created_at=? WHERE id=?",
                Timestamp.valueOf(
                        LocalDateTime.ofInstant(now.minusSeconds(3600), MarketingCopy.INDIA)),
                order);
        assertThat(marketing.evaluate(branch)).isZero();
        jdbc.update(
                "UPDATE orders SET created_at=? WHERE id=?",
                Timestamp.valueOf(
                        LocalDateTime.ofInstant(
                                now.minus(Duration.ofDays(10)), MarketingCopy.INDIA)),
                order);
        jdbc.update("UPDATE branches SET closing_time='15:00' WHERE id=?", branch);
        assertThat(marketing.evaluate(branch)).isZero();
    }

    @Test
    void hiddenCategoriesCannotMakeAnEmptyCatalogueEligible() {
        jdbc.update("UPDATE categories SET active=false WHERE id=?", category);
        assertThat(marketing.evaluate(branch)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM marketing_push_deliveries WHERE subject_id=?",
                                Integer.class,
                                subject))
                .isZero();
    }

    @Test
    void categoryHiddenAfterQueueCancelsDelivery() throws Exception {
        assertThat(marketing.evaluate(branch)).isEqualTo(1);
        jdbc.update("UPDATE categories SET active=false WHERE id=?", category);
        dispatcher.dispatchBatch();
        assertThat(state()).isEqualTo("SKIPPED");
        verify(transport, never())
                .sendMarketing(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString());
    }

    @Test
    void hiddenRecommendedCategoryWithAnotherVisibleCategoryMustCancelPromotion() throws Exception {
        long otherCategory =
                jdbc.queryForObject(
                        "INSERT INTO categories(code,name) VALUES (?,'Meals') RETURNING id",
                        Long.class,
                        "REVIEW-" + UUID.randomUUID());
        long otherProduct =
                jdbc.queryForObject(
                        "INSERT INTO products(code,category_id,name,base_price) VALUES"
                                + " (?,?,'Meal',100) RETURNING id",
                        Long.class,
                        "REVIEW-" + UUID.randomUUID(),
                        otherCategory);
        jdbc.update(
                "INSERT INTO branch_products(branch_id,product_id) VALUES (?,?)",
                branch,
                otherProduct);
        assertThat(marketing.evaluate(branch)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT topic FROM marketing_push_deliveries WHERE subject_id=?",
                                String.class,
                                subject))
                .isEqualTo("SWEETS");
        jdbc.update("UPDATE categories SET active=false WHERE id=?", category);
        when(transport.sendMarketing(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenReturn(201);
        dispatcher.dispatchBatch();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT state FROM marketing_push_deliveries WHERE subject_id=?",
                                String.class,
                                subject))
                .as("The promoted sweets category is no longer visible")
                .isEqualTo("SKIPPED");
    }

    @Test
    void rollingWeeklyCapAppliesBeforeQueueing() {
        for (int i = 1; i <= 3; i++)
            jdbc.update(
                    "INSERT INTO"
                        + " marketing_push_deliveries(environment,branch_id,subject_id,subscription_id,send_date,template_key,topic,title,message,reason,state,expires_at,created_at)"
                        + " VALUES ('DEV',?,?,?,?,?,'SWEETS','Test','Test','Test','ACCEPTED',?,?)",
                    branch,
                    subject,
                    subscription,
                    java.sql.Date.valueOf(
                            now.atZone(MarketingCopy.INDIA).toLocalDate().minusDays(i)),
                    "old-" + i,
                    Timestamp.from(now),
                    Timestamp.from(now.minus(Duration.ofDays(i))));
        assertThat(marketing.evaluate(branch)).isZero();
    }

    @Test
    void preferenceWithdrawalAndNewPurchaseAreRecheckedBeforeSending() {
        assertThat(marketing.evaluate(branch)).isEqualTo(1);
        jdbc.update(
                "UPDATE customer_notification_preferences SET offer_inbox_enabled=false WHERE"
                        + " subject_id=?",
                subject);
        dispatcher.dispatchBatch();
        assertThat(state()).isEqualTo("SKIPPED");
    }

    @Test
    void transientFailureRetriesWithSameEventAndGoneDeviceIsRevoked() throws Exception {
        assertThat(marketing.evaluate(branch)).isEqualTo(1);
        when(transport.sendMarketing(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenReturn(503, 410);
        dispatcher.dispatchBatch();
        assertThat(state()).isEqualTo("QUEUED");
        jdbc.update(
                "UPDATE marketing_push_deliveries SET next_attempt_at=? WHERE subject_id=?",
                Timestamp.from(now),
                subject);
        dispatcher.dispatchBatch();
        assertThat(state()).isEqualTo("REVOKED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT revoked_at IS NOT NULL FROM customer_push_subscriptions"
                                        + " WHERE id=?",
                                Boolean.class,
                                subscription))
                .isTrue();
    }

    @Test
    void aNewPurchaseAfterQueueCancelsThePromotion() {
        assertThat(marketing.evaluate(branch)).isEqualTo(1);
        jdbc.update(
                "UPDATE orders SET created_at=? WHERE id=?",
                Timestamp.valueOf(
                        LocalDateTime.ofInstant(now.minusSeconds(300), MarketingCopy.INDIA)),
                order);
        dispatcher.dispatchBatch();
        assertThat(state()).isEqualTo("SKIPPED");
    }

    @Test
    void mostRecentBranchAndExpiredSessionRestrictTheAudience() {
        long other =
                jdbc.queryForObject(
                        "INSERT INTO branches(code,name) VALUES (?,'Other branch') RETURNING id",
                        Long.class,
                        "OTHER-" + UUID.randomUUID());
        jdbc.update("UPDATE orders SET branch_id=? WHERE id=?", other, order);
        assertThat(marketing.evaluate(branch)).isZero();
        jdbc.update("UPDATE orders SET branch_id=? WHERE id=?", branch, order);
        jdbc.update(
                "UPDATE verified_customer_sessions SET expires_at=? WHERE verified_subject_id=?",
                Timestamp.from(now.minusSeconds(1)),
                subject);
        assertThat(marketing.evaluate(branch)).isZero();
    }

    @Test
    void expiredPromotionsAreSkippedWithoutNetworkTraffic() {
        assertThat(marketing.evaluate(branch)).isEqualTo(1);
        jdbc.update(
                "UPDATE marketing_push_deliveries SET expires_at=? WHERE subject_id=?",
                Timestamp.from(now.minusSeconds(1)),
                subject);
        dispatcher.dispatchBatch();
        assertThat(state()).isEqualTo("SKIPPED");
    }

    @Test
    void aReducedWeeklyLimitIsRespectedForAlreadyQueuedMessages() {
        jdbc.update(
                "INSERT INTO"
                    + " marketing_push_deliveries(environment,branch_id,subject_id,subscription_id,send_date,template_key,topic,title,message,reason,state,expires_at,created_at)"
                    + " VALUES ('DEV',?,?,?,?,?,'SWEETS','Test','Test','Test','ACCEPTED',?,?)",
                branch,
                subject,
                subscription,
                java.sql.Date.valueOf(now.atZone(MarketingCopy.INDIA).toLocalDate().minusDays(1)),
                "old",
                Timestamp.from(now),
                Timestamp.from(now.minus(Duration.ofDays(1))));
        assertThat(marketing.evaluate(branch)).isEqualTo(1);
        var config = marketing.settings(branch);
        marketing.save(
                branch,
                new MarketingPushService.Settings(
                        true, "HINGLISH", 1, true, true, true, true, config.version()));
        dispatcher.dispatchBatch();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT state FROM marketing_push_deliveries WHERE subject_id=? AND"
                                        + " send_date=?",
                                String.class,
                                subject,
                                java.sql.Date.valueOf(
                                        now.atZone(MarketingCopy.INDIA).toLocalDate())))
                .isEqualTo("SKIPPED");
    }

    private String state() {
        return jdbc.queryForObject(
                "SELECT state FROM marketing_push_deliveries WHERE subject_id=?",
                String.class,
                subject);
    }
}
