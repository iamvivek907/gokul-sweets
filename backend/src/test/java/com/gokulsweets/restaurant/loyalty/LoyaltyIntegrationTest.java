package com.gokulsweets.restaurant.loyalty;

import static org.assertj.core.api.Assertions.*;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.entity.*;
import com.gokulsweets.restaurant.order.enums.*;
import com.gokulsweets.restaurant.product.Product;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(
        properties = {
            "spring.datasource.hikari.maximum-pool-size=5",
            "spring.datasource.hikari.minimum-idle=1"
        })
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class LoyaltyIntegrationTest {
    @Autowired
    com.gokulsweets.restaurant.order.lifecycle.service.AdminOrderLifecycleCoordinator lifecycle;

    @Autowired com.gokulsweets.restaurant.order.service.PickupCodeService pickupCodes;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.gokulsweets.restaurant.inventory.service.OrderInventoryLifecycleService inventory;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.gokulsweets.restaurant.security.StaffAuthorizationService staff;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox notifications;

    @Autowired LoyaltyService loyalty;
    @Autowired LoyaltyCheckoutService checkout;
    @Autowired LoyaltyProperties rules;
    @Autowired com.gokulsweets.restaurant.rebate.RebateEligibilityService couponEligibility;
    @Autowired JdbcTemplate jdbc;
    @Autowired EnhancementProperties flags;
    @Autowired PlatformTransactionManager manager;
    @Autowired tools.jackson.databind.ObjectMapper mapper;
    private boolean rewards, identity, quote;
    private UUID subject;
    private long branch;
    private long slot;

    @Autowired com.gokulsweets.restaurant.badges.CustomerBadgeService badges;
    @Autowired com.gokulsweets.restaurant.badges.CustomerBadgeAdminController badgeAdmin;
    private final List<Long> testBadges = new ArrayList<>();

    private long badge(int orders, String minimum, String bonus) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " customer_badges(code,name,description,required_orders,minimum_subtotal,bonus_percent)"
                            + " VALUES(?,'Test tier','A funded test tier',?,?,?) RETURNING id",
                        Long.class,
                        "TEST_"
                                + UUID.randomUUID()
                                        .toString()
                                        .replace("-", "")
                                        .toUpperCase(java.util.Locale.ROOT),
                        orders,
                        new BigDecimal(minimum),
                        new BigDecimal(bonus));
        testBadges.add(id);
        return id;
    }

    @AfterEach
    void removeTestBadges() {
        for (long id : testBadges) {
            jdbc.update("DELETE FROM customer_badge_awards WHERE badge_id=?", id);
            jdbc.update("DELETE FROM customer_badge_audit WHERE badge_id=?", id);
            jdbc.update("DELETE FROM customer_badges WHERE id=?", id);
        }
    }

    @Test
    void badgeProgressRequiresCompletedPaidOwnedThresholdOrders() {
        long tier = badge(1, "149", "20");
        var qualified = order("PICKED_UP", false);
        paid(qualified.getId(), "PAID");
        var below = order("PICKED_UP", false);
        paid(below.getId(), "PAID");
        jdbc.update("UPDATE orders SET subtotal=148.99 WHERE id=?", below.getId());
        var pending = order("CONFIRMED", false);
        paid(pending.getId(), "PAID");
        order("PICKED_UP", false);
        var refund = order("PICKED_UP", false);
        paid(refund.getId(), "PAID");
        paid(refund.getId(), "REFUND_PENDING");
        var test = order("PICKED_UP", false);
        paid(test.getId(), "PAID");
        jdbc.update("UPDATE orders SET loyalty_test_order=true WHERE id=?", test.getId());
        var progress =
                badges.snapshot("DEV", subject).badges().stream()
                        .filter(b -> b.id() == tier)
                        .findFirst()
                        .orElseThrow();
        assertThat(progress.qualifyingOrders()).isEqualTo(1);
        assertThat(progress.earned()).isTrue();
        assertThat(badges.benefit("DEV", subject).percent()).isEqualByComparingTo("20");
        assertThat(badges.benefit("QA", subject).percent()).isZero();
        assertThat(badges.benefit("DEV", UUID.randomUUID()).percent()).isZero();
    }

    @Test
    void badgeBonusUsesOneTierSavedAtPlacementAndRefundsWholeEarning() {
        badge(1, "149", "20");
        badge(1, "148", "70");
        var prior = order("PICKED_UP", false);
        paid(prior.getId(), "PAID");
        var future = order("PENDING_PAYMENT", false);
        new TransactionTemplate(manager).executeWithoutResult(tx -> loyalty.reserve(future, null));
        assertThat(future.getLoyaltyBadgeBonusPercent()).isEqualByComparingTo("20");
        jdbc.update(
                "UPDATE orders SET"
                    + " loyalty_enrolled=true,loyalty_badge_bonus_percent=?,loyalty_badge_name=?"
                    + " WHERE id=?",
                future.getLoyaltyBadgeBonusPercent(),
                future.getLoyaltyBadgeName(),
                future.getId());
        loyalty.reconcile(future.getId());
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isZero();
        paid(future.getId(), "PAID");
        loyalty.reconcile(future.getId());
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isZero();
        jdbc.update("UPDATE customer_badges SET bonus_percent=99 WHERE code LIKE 'TEST_%'");
        jdbc.update("UPDATE orders SET order_status='PICKED_UP' WHERE id=?", future.getId());
        loyalty.reconcile(future.getId());
        loyalty.reconcile(future.getId());
        var earned =
                loyalty.wallet("DEV", subject, null).history().stream()
                        .filter(e -> e.kind().equals("EARNED"))
                        .findFirst()
                        .orElseThrow();
        assertThat(earned.coins()).isEqualTo(16);
        assertThat(earned.badgeBonusCoins()).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM loyalty_ledger WHERE order_id=? AND"
                                        + " kind='EARNED'",
                                Integer.class,
                                future.getId()))
                .isEqualTo(1);
        paid(future.getId(), "REFUNDED");
        loyalty.reconcile(future.getId());
        loyalty.reconcile(future.getId());
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isZero();
    }

    @Test
    void badgePresentationClaimIsScopedDurableAndDoesNotReplay() {
        badge(1, "149", "20");
        var completed = order("PICKED_UP", false);
        paid(completed.getId(), "PAID");
        var first = badges.claim("DEV", subject);
        assertThat(first).isNotNull();
        assertThatThrownBy(
                        () ->
                                badges.acknowledge(
                                        "DEV", UUID.randomUUID(), first.awardId(), first.claimId()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        badges.acknowledge("DEV", subject, first.awardId(), first.claimId());
        badges.acknowledge("DEV", subject, first.awardId(), first.claimId());
        var next = badges.claim("DEV", subject);
        assertThat(next).isNotNull();
        assertThat(badges.claim("DEV", subject)).isNull();
        jdbc.update(
                "UPDATE customer_badge_awards SET claim_until=CURRENT_TIMESTAMP-INTERVAL '1 second'"
                        + " WHERE id=?",
                next.awardId());
        var retry = badges.claim("DEV", subject);
        assertThat(retry.awardId()).isEqualTo(next.awardId());
        assertThat(retry.claimId()).isNotEqualTo(next.claimId());
        badges.acknowledge("DEV", subject, retry.awardId(), retry.claimId());
        assertThat(badges.claim("DEV", subject)).isNull();
    }

    @Test
    void queuedRecognitionUsesCurrentHighestBenefitWithoutRewritingItsEarnedSnapshot() {
        long tier = badge(1, "149", "20");
        var completed = order("PICKED_UP", false);
        paid(completed.getId(), "PAID");
        badges.snapshot("DEV", subject);
        jdbc.update(
                "UPDATE customer_badge_awards SET celebrated_at=CURRENT_TIMESTAMP WHERE"
                        + " subject_id=? AND badge_id<>?",
                subject,
                tier);
        jdbc.update("UPDATE customer_badges SET bonus_percent=10 WHERE id=?", tier);
        var first = badges.claim("DEV", subject);
        assertThat(first.name()).isEqualTo("Test tier");
        assertThat(first.bonusPercent()).isEqualByComparingTo("10");
        assertThat(first.bonusPercent())
                .isEqualByComparingTo(badges.benefit("DEV", subject).percent());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT bonus_percent FROM customer_badge_awards WHERE id=?",
                                BigDecimal.class,
                                first.awardId()))
                .isEqualByComparingTo("20");

        badge(2, "149", "40");
        var second = order("PICKED_UP", false);
        paid(second.getId(), "PAID");
        jdbc.update(
                "UPDATE customer_badge_awards SET claim_until=CURRENT_TIMESTAMP-INTERVAL '1 second'"
                        + " WHERE id=?",
                first.awardId());
        var retry = badges.claim("DEV", subject);
        assertThat(retry.awardId()).isEqualTo(first.awardId());
        assertThat(retry.bonusPercent()).isEqualByComparingTo("40");
        assertThat(retry.bonusPercent())
                .isEqualByComparingTo(badges.benefit("DEV", subject).percent());
    }

    @Test
    void refundRemovesUnshownBadgeAndLegacyOrdersHaveNoBonus() {
        long tier = badge(1, "149", "20");
        var completed = order("PICKED_UP", true);
        paid(completed.getId(), "PAID");
        loyalty.reconcile(completed.getId());
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isEqualTo(14);
        paid(completed.getId(), "REFUND_PENDING");
        assertThat(
                        badges.snapshot("DEV", subject).badges().stream()
                                .filter(b -> b.id() == tier)
                                .findFirst()
                                .orElseThrow()
                                .earned())
                .isFalse();
        assertThat(badges.claim("DEV", subject)).isNull();
    }

    @Test
    void badgeAdminRequiresOwnerAndChecksVersionAndAudits() {
        long id = badge(1, "149", "20");
        var user = new com.gokulsweets.restaurant.staff.StaffUser();
        user.setId(77L);
        var role = new com.gokulsweets.restaurant.staff.Role();
        role.setName("BRANCH_ADMIN");
        user.setRole(role);
        org.mockito.Mockito.when(staff.getCurrentStaff()).thenReturn(user);
        assertThatThrownBy(() -> badgeAdmin.list())
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        role.setName("OWNER_ADMIN");
        String code =
                jdbc.queryForObject(
                        "SELECT code FROM customer_badges WHERE id=?", String.class, id);
        var input =
                new com.gokulsweets.restaurant.badges.CustomerBadgeAdminController.Input(
                        id,
                        1,
                        code,
                        "Regular test",
                        "Benefits",
                        2,
                        new BigDecimal("500"),
                        new BigDecimal("25"),
                        "GOLD",
                        true,
                        "Owner approved benefit");
        badgeAdmin.save(input);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT version FROM customer_badges WHERE id=?",
                                Integer.class,
                                id))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM customer_badge_audit WHERE badge_id=?",
                                Integer.class,
                                id))
                .isEqualTo(1);
        assertThatThrownBy(() -> badgeAdmin.save(input))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @BeforeEach
    void setup() {
        rewards = flags.isGokulRewards();
        identity = flags.isCustomerOtpIdentity();
        quote = flags.isAcceptedCheckoutQuote();
        flags.setGokulRewards(true);
        flags.setCustomerOtpIdentity(true);
        flags.setAcceptedCheckoutQuote(true);
        subject = UUID.randomUUID();
        branch =
                jdbc.queryForObject(
                        "INSERT INTO branches(code,name) VALUES (?,'Rewards test') RETURNING id",
                        Long.class,
                        "LOY-" + subject.toString().substring(0, 8));
        slot =
                jdbc.queryForObject(
                        "INSERT INTO pickup_slots(branch_id,slot_date,start_time,end_time,capacity)"
                                + " VALUES (?,CURRENT_DATE,'10:00','10:30',500) RETURNING id",
                        Long.class,
                        branch);
        jdbc.update(
                "INSERT INTO verified_customer_subjects(id,environment,verified_phone) VALUES"
                        + " (?,'DEV',?)",
                subject,
                "+91"
                        + (6000000000L
                                + Math.floorMod(subject.getLeastSignificantBits(), 3999999999L)));
        loyalty.wallet("DEV", subject, null);
    }

    @AfterEach
    void restore() {
        flags.setGokulRewards(rewards);
        flags.setCustomerOtpIdentity(identity);
        flags.setAcceptedCheckoutQuote(quote);
    }

    @Test
    void rewardsEnabledQuoteCanRunInAPostgresReadOnlyTransaction() {
        var validation =
                org.mockito.Mockito.mock(
                        com.gokulsweets.restaurant.order.service.OrderValidationService.class);
        var calculation =
                org.mockito.Mockito.mock(
                        com.gokulsweets.restaurant.order.service.OrderCalculationService.class);
        var service =
                new com.gokulsweets.restaurant.order.service.CheckoutQuoteService(
                        flags,
                        validation,
                        calculation,
                        org.mockito.Mockito.mock(
                                com.gokulsweets.restaurant.order.repository.OrderRepository.class),
                        loyalty);
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "signingKey", "readonly-quote-regression-signing-key");
        var request =
                new com.gokulsweets.restaurant.order.dto.CreateOrderRequest(
                        branch, slot, "Customer", "9876543210", PickupType.NORMAL, List.of());
        var data =
                org.mockito.Mockito.mock(
                        com.gokulsweets.restaurant.order.service.model.ValidatedOrderData.class);
        var amounts =
                new com.gokulsweets.restaurant.order.service.model.OrderCalculationResult(
                        List.of(),
                        new BigDecimal("149"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("149"));
        org.mockito.Mockito.when(validation.validate(request)).thenReturn(data);
        org.mockito.Mockito.when(calculation.calculate(data)).thenReturn(amounts);
        var transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        var result =
                transaction.execute(
                        status -> {
                            jdbc.execute("SET TRANSACTION READ ONLY");
                            assertThat(
                                            jdbc.queryForObject(
                                                    "SHOW transaction_read_only", String.class))
                                    .isEqualTo("on");
                            return service.preview(request, null);
                        });
        assertThat(result.token()).isNotBlank();
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        status -> service.accept(request, null, amounts, result.token()));
    }

    @Test
    void catalogueInsertionWaitsForAcceptedRewardReservation() throws Exception {
        assertPolicyMutationWaits(true);
    }

    @Test
    void exclusionReplacementWaitsForAcceptedRewardReservation() throws Exception {
        assertPolicyMutationWaits(false);
    }

    @Test
    void paymentValidationLocksCatalogueWithRolloutOff() throws Exception {
        assertPolicyMutationWaits(true, true);
    }

    @Test
    void paymentValidationLocksExclusionsWithRolloutOff() throws Exception {
        assertPolicyMutationWaits(false, true);
    }

    private void assertPolicyMutationWaits(boolean catalogue) throws Exception {
        assertPolicyMutationWaits(catalogue, false);
    }

    private void assertPolicyMutationWaits(boolean catalogue, boolean payment) throws Exception {
        grant(100);
        var pending = order("PENDING_PAYMENT", true);
        String accepted = loyalty.policyVersion();
        String code = "LOCK_" + subject.toString().replace("-", "").toUpperCase(Locale.ROOT);
        var products =
                jdbc.queryForList("SELECT product_id FROM loyalty_excluded_products", Long.class);
        var promotions =
                jdbc.queryForList("SELECT code FROM loyalty_excluded_rebates", String.class);
        long category =
                jdbc.queryForObject(
                        "INSERT INTO categories(code,name) VALUES (?,?) RETURNING id",
                        Long.class,
                        code,
                        code);
        long product =
                jdbc.queryForObject(
                        "INSERT INTO products(code,name,category_id,sale_mode,base_price) VALUES"
                                + " (?, ?, ?, 'UNIT',149) RETURNING id",
                        Long.class,
                        code,
                        code,
                        category);
        if (payment) {
            pending.getItems().getFirst().getProduct().setId(product);
            loyalty.reserve(pending, "SWEET_5");
            flags.setGokulRewards(false);
        }
        long role =
                jdbc.queryForObject("SELECT id FROM roles WHERE name='OWNER_ADMIN'", Long.class);
        long actor =
                jdbc.queryForObject(
                        "INSERT INTO staff_users(username,password_hash,full_name,role_id) VALUES"
                                + " (?,'test','Policy lock test',?) RETURNING id",
                        Long.class,
                        code,
                        role);
        var staff =
                org.mockito.Mockito.mock(
                        com.gokulsweets.restaurant.security.StaffAuthorizationService.class);
        var user = new com.gokulsweets.restaurant.staff.StaffUser();
        user.setId(actor);
        var owner = new com.gokulsweets.restaurant.staff.Role();
        owner.setName("OWNER_ADMIN");
        user.setRole(owner);
        org.mockito.Mockito.when(staff.getCurrentStaff()).thenReturn(user);
        var admin = new LoyaltyAdminController(staff, jdbc, loyalty, rules, mapper);
        var productIds = new ArrayList<>(products);
        productIds.add(product);
        var rebateCodes = new ArrayList<>(promotions);
        rebateCodes.add(code);
        var connected = new CountDownLatch(1);
        var pid = new java.util.concurrent.atomic.AtomicInteger();
        var writer = new java.util.concurrent.atomic.AtomicReference<Future<?>>();
        try (var pool = Executors.newSingleThreadExecutor()) {
            try {
                new TransactionTemplate(manager)
                        .executeWithoutResult(
                                tx -> {
                                    if (payment) loyalty.verifyPayment(pending);
                                    else loyalty.verifyPolicy(accepted);
                                    assertThat(
                                                    jdbc.queryForObject(
                                                            "SELECT COUNT(*) FROM pg_locks WHERE"
                                                                    + " pid=pg_backend_pid() AND"
                                                                    + " locktype='advisory' AND"
                                                                    + " granted",
                                                            Integer.class))
                                            .as("accepted transaction holds a policy lock")
                                            .isPositive();
                                    writer.set(
                                            pool.submit(
                                                    () ->
                                                            new TransactionTemplate(manager)
                                                                    .executeWithoutResult(
                                                                            write -> {
                                                                                jdbc.execute(
                                                                                        "SET LOCAL"
                                                                                            + " lock_timeout='5s'");
                                                                                pid.set(
                                                                                        jdbc
                                                                                                .queryForObject(
                                                                                                        "SELECT"
                                                                                                            + " pg_backend_pid()",
                                                                                                        Integer
                                                                                                                .class));
                                                                                connected
                                                                                        .countDown();
                                                                                if (catalogue)
                                                                                    admin.reward(
                                                                                            new LoyaltyAdminController
                                                                                                    .RewardInput(
                                                                                                    code,
                                                                                                    "Concurrent"
                                                                                                        + " catalogue"
                                                                                                        + " insertion",
                                                                                                    10,
                                                                                                    BigDecimal
                                                                                                            .ONE,
                                                                                                    new BigDecimal(
                                                                                                            "149"),
                                                                                                    true,
                                                                                                    "Policy"
                                                                                                        + " lock"
                                                                                                        + " regression"));
                                                                                else
                                                                                    admin
                                                                                            .exclusions(
                                                                                                    new LoyaltyAdminController
                                                                                                            .Exclusions(
                                                                                                            productIds,
                                                                                                            rebateCodes,
                                                                                                            "Policy"
                                                                                                                + " lock"
                                                                                                                + " regression"));
                                                                            })));
                                    try {
                                        assertThat(connected.await(3, TimeUnit.SECONDS)).isTrue();
                                        long deadline =
                                                System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                                        while (jdbc.queryForObject(
                                                                "SELECT COUNT(*) FROM pg_locks"
                                                                    + " WHERE pid=? AND"
                                                                    + " locktype='advisory' AND NOT"
                                                                    + " granted",
                                                                Integer.class,
                                                                pid.get())
                                                        == 0
                                                && System.nanoTime() < deadline) Thread.sleep(10);
                                        if (writer.get().isDone()) writer.get().get();
                                        assertThat(
                                                        jdbc.queryForObject(
                                                                "SELECT COUNT(*) FROM pg_locks"
                                                                    + " WHERE pid=? AND"
                                                                    + " locktype='advisory' AND NOT"
                                                                    + " granted",
                                                                Integer.class,
                                                                pid.get()))
                                                .as(
                                                        "waiting writer %s; advisory locks %s",
                                                        pid.get(),
                                                        jdbc.queryForList(
                                                                "SELECT pid,mode,granted FROM"
                                                                        + " pg_locks WHERE"
                                                                        + " locktype='advisory'"))
                                                .isEqualTo(1);
                                    } catch (InterruptedException interrupted) {
                                        Thread.currentThread().interrupt();
                                        throw new IllegalStateException(interrupted);
                                    } catch (ExecutionException failed) {
                                        throw new IllegalStateException(
                                                "Policy writer failed before acquiring its lock",
                                                failed.getCause());
                                    }
                                    if (!payment) {
                                        assertThat(loyalty.policyVersion()).isEqualTo(accepted);
                                        loyalty.reserve(pending, "SWEET_5");
                                    }
                                });
                writer.get().get(6, TimeUnit.SECONDS);
                if (!payment) {
                    assertThat(loyalty.policyVersion()).isNotEqualTo(accepted);
                    assertThatThrownBy(
                                    () ->
                                            new TransactionTemplate(manager)
                                                    .executeWithoutResult(
                                                            tx -> loyalty.verifyPolicy(accepted)))
                            .hasMessageContaining("changed");
                } else if (!catalogue)
                    assertThatThrownBy(() -> loyalty.verifyPayment(pending))
                            .hasMessageContaining("eligibility changed");
                assertThat(
                                jdbc.queryForObject(
                                        "SELECT state FROM loyalty_holds WHERE order_id=?",
                                        String.class,
                                        pending.getId()))
                        .isEqualTo("RESERVED");
            } finally {
                new TransactionTemplate(manager)
                        .executeWithoutResult(
                                tx -> {
                                    loyalty.lockPolicyForUpdate();
                                    jdbc.update("DELETE FROM loyalty_rewards WHERE code=?", code);
                                    jdbc.update("DELETE FROM loyalty_excluded_products");
                                    jdbc.update("DELETE FROM loyalty_excluded_rebates");
                                    for (long id : products)
                                        jdbc.update(
                                                "INSERT INTO loyalty_excluded_products VALUES (?)",
                                                id);
                                    for (String promotion : promotions)
                                        jdbc.update(
                                                "INSERT INTO loyalty_excluded_rebates VALUES (?)",
                                                promotion);
                                });
            }
        }
    }

    private void grant(int coins) {
        grant(coins, 180);
    }

    private void grant(int coins, int days) {
        long ledger =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " loyalty_ledger(environment,subject_id,event_key,kind,coins,reason,expires_at)"
                            + " VALUES ('DEV',? ,?,'ADJUSTED',?,'Test"
                            + " fixture',CURRENT_TIMESTAMP+(?*INTERVAL '1 day')) RETURNING id",
                        Long.class,
                        subject,
                        "fixture:" + UUID.randomUUID(),
                        coins,
                        days);
        jdbc.update("INSERT INTO loyalty_lots(ledger_id,remaining) VALUES (?,?)", ledger, coins);
    }

    private Order order(String status, boolean enrolled) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " orders(order_number,branch_id,pickup_slot_id,customer_name,customer_phone,pickup_type,order_status,subtotal,tax_amount,total_amount,loyalty_enrolled,loyalty_eligible_subtotal,reservation_expires_at)"
                            + " VALUES"
                            + " (?,?,?,'Customer','9876543210','NORMAL',?,149,8,167,?,149,CURRENT_TIMESTAMP+INTERVAL"
                            + " '15 minutes') RETURNING id",
                        Long.class,
                        "GKS-LOY-" + UUID.randomUUID(),
                        branch,
                        slot,
                        status,
                        enrolled);
        jdbc.update(
                "INSERT INTO verified_order_ownership(order_id,environment,verified_subject_id)"
                        + " VALUES (?,'DEV',?)",
                id,
                subject);
        var order = new Order();
        order.setId(id);
        order.setOrderNumber("GKS-" + id);
        order.setOrderStatus(OrderStatus.valueOf(status));
        order.setSubtotal(new BigDecimal("149"));
        order.setTaxAmount(new BigDecimal("8"));
        order.setConvenienceFee(BigDecimal.TEN);
        order.setPaymentFeeRate(new BigDecimal("2"));
        var item = new OrderItem();
        var product = new Product();
        product.setId(999999L);
        item.setProduct(product);
        item.setLineTotal(new BigDecimal("149"));
        item.setTaxAmount(BigDecimal.ZERO);
        order.getItems().add(item);
        return order;
    }

    private void paid(long id, String status) {
        jdbc.update(
                "INSERT INTO payments(order_id,provider,amount,payment_status) VALUES"
                        + " (?,'PHONEPE',167,?)",
                id,
                status);
    }

    @Test
    void holdsOnlySelectedCoinsAndPreservesUndiscountedTaxAndFees() {
        grant(1000);
        var order = order("PENDING_PAYMENT", true);
        loyalty.reserve(order, "SWEET_5");
        assertThat(order.getLoyaltyCoins()).isEqualTo(30);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("165.24");
        assertThat(order.getTaxAmount()).isEqualByComparingTo("8");
        assertThat(order.getConvenienceFee()).isEqualByComparingTo("10");
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isEqualTo(970);
        assertThatThrownBy(() -> loyalty.reserve(order, "SWEET_5")).hasMessageContaining("already");
        loyalty.remove(order);
        loyalty.remove(order);
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isEqualTo(1000);
        loyalty.reserve(order, "SWEET_5");
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isEqualTo(970);
    }

    @Test
    void ladderMinimumAndTenPercentCapAreServerEnforced() {
        grant(1000);
        assertThatThrownBy(() -> loyalty.preview("DEV", subject, new BigDecimal("100"), "SWEET_5"))
                .hasMessageContaining("eligible");
    }

    @Test
    void capRejectsSeventyFiveRupeeRewardAtSixNinetyNineButAllowsSevenFifty() {
        grant(1000);
        assertThatThrownBy(() -> loyalty.preview("DEV", subject, new BigDecimal("699"), "SWEET_75"))
                .hasMessageContaining("eligible");
        assertThat(loyalty.preview("DEV", subject, new BigDecimal("750"), "SWEET_75").coins())
                .isEqualTo(300);
    }

    private String prepareLatePickup(Order order) {
        paid(order.getId(), "PAID");
        long payment =
                jdbc.queryForObject(
                        "SELECT id FROM payments WHERE order_id=?", Long.class, order.getId());
        pickupCodes.issueForPaidPayment(payment);
        jdbc.update("UPDATE order_pickup_codes SET code='0042' WHERE order_id=?", order.getId());
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(
                        new org.springframework.security.authentication
                                .UsernamePasswordAuthenticationToken(
                                "loyalty-test",
                                "test",
                                List.of(
                                        new org.springframework.security.core.authority
                                                .SimpleGrantedAuthority("ORDER_VIEW"))));
        return jdbc.queryForObject(
                "SELECT order_number FROM orders WHERE id=?", String.class, order.getId());
    }

    @Test
    void latePickupCreditsOnceEvenWithRolloutOffAndRepeatedHandover() {
        var order = order("PICKUP_WINDOW_EXPIRED", true);
        String number = prepareLatePickup(order);
        flags.setGokulRewards(false);
        try {
            assertThatThrownBy(() -> lifecycle.collectLateOrder(number, "9999"))
                    .hasMessageContaining("Incorrect");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM loyalty_ledger WHERE order_id=?",
                                    Integer.class,
                                    order.getId()))
                    .isZero();
            assertThat(lifecycle.collectLateOrder(number, "0042").orderStatus())
                    .isEqualTo(OrderStatus.PICKED_UP);
            lifecycle.collectLateOrder(number, "0042");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT SUM(coins)::integer FROM loyalty_ledger WHERE"
                                            + " order_id=? AND kind='EARNED'",
                                    Integer.class,
                                    order.getId()))
                    .isEqualTo(14);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM loyalty_ledger WHERE order_id=? AND"
                                            + " kind='EARNED'",
                                    Integer.class,
                                    order.getId()))
                    .isEqualTo(1);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void latePickupRollbackKeepsCoinsStatusAndPickupCodeThenAllowsRetry() {
        var order = order("PICKUP_WINDOW_EXPIRED", true);
        String number = prepareLatePickup(order);
        org.mockito.Mockito.doThrow(new IllegalStateException("Simulated completion failure"))
                .when(notifications)
                .orderReady(order.getId());
        try {
            assertThatThrownBy(() -> lifecycle.collectLateOrder(number, "0042"))
                    .hasMessageContaining("Simulated completion failure");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT order_status FROM orders WHERE id=?",
                                    String.class,
                                    order.getId()))
                    .isEqualTo("PICKUP_WINDOW_EXPIRED");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM loyalty_ledger WHERE order_id=?",
                                    Integer.class,
                                    order.getId()))
                    .isZero();
            assertThat(pickupCodes.customerCode(number).code()).isEqualTo("0042");
            org.mockito.Mockito.reset(notifications);
            lifecycle.collectLateOrder(number, "0042");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM loyalty_ledger WHERE order_id=? AND"
                                            + " kind='EARNED'",
                                    Integer.class,
                                    order.getId()))
                    .isEqualTo(1);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void completedPaymentCreditsOnceAndFullRefundUsesCompensatingEvents() {
        var order = order("PICKED_UP", true);
        paid(order.getId(), "PAID");
        loyalty.reconcile(order.getId());
        loyalty.reconcile(order.getId());
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isEqualTo(14);
        jdbc.update(
                "UPDATE payments SET payment_status='REFUNDED' WHERE order_id=?", order.getId());
        loyalty.reconcile(order.getId());
        loyalty.reconcile(order.getId());
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isZero();
        assertThat(loyalty.wallet("DEV", subject, null).completedOrders()).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM loyalty_ledger WHERE order_id=? AND"
                                        + " kind='EARNED'",
                                Integer.class,
                                order.getId()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM loyalty_ledger WHERE order_id=? AND"
                                        + " kind='REVERSED'",
                                Integer.class,
                                order.getId()))
                .isEqualTo(1);
    }

    @Test
    void refundReversesItsOwnLotsAndPreservesOlderCredits() {
        grant(100, 90);
        var completed = order("PICKED_UP", true);
        paid(completed.getId(), "PAID");
        loyalty.reconcile(completed.getId());
        jdbc.update(
                "UPDATE payments SET payment_status='REFUNDED' WHERE order_id=?",
                completed.getId());
        loyalty.reconcile(completed.getId());
        loyalty.reconcile(completed.getId());
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isEqualTo(100);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT p.remaining FROM loyalty_lots p JOIN loyalty_ledger l ON"
                                        + " l.id=p.ledger_id WHERE l.subject_id=? AND"
                                        + " l.kind='ADJUSTED'",
                                Integer.class,
                                subject))
                .isEqualTo(100);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT p.remaining FROM loyalty_lots p JOIN loyalty_ledger l ON"
                                    + " l.id=p.ledger_id WHERE l.order_id=? AND l.kind='EARNED'",
                                Integer.class,
                                completed.getId()))
                .isZero();
    }

    @Test
    void refundTargetsEarnedWelcomeAndRestoredOriginsBeforeOtherCredits() {
        grant(10, 90);
        var completed = order("PICKED_UP", true);
        jdbc.update(
                "UPDATE orders SET"
                    + " subtotal=400,loyalty_eligible_subtotal=400,loyalty_welcome_coins=10 WHERE"
                    + " id=?",
                completed.getId());
        paid(completed.getId(), "PAID");
        loyalty.reconcile(completed.getId());
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isEqualTo(60);
        var spending = order("PENDING_PAYMENT", true);
        loyalty.reserve(spending, "SWEET_5");
        loyalty.remove(spending);
        jdbc.update(
                "UPDATE payments SET payment_status='REFUNDED' WHERE order_id=?",
                completed.getId());
        loyalty.reconcile(completed.getId());
        loyalty.reconcile(completed.getId());
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isEqualTo(10);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COALESCE(SUM(p.remaining),0)::integer FROM loyalty_lots p"
                                        + " JOIN loyalty_ledger l ON l.id=p.ledger_id JOIN"
                                        + " loyalty_ledger original ON"
                                        + " original.id=COALESCE(l.source_credit_id,l.id) WHERE"
                                        + " original.order_id=? AND original.kind IN"
                                        + " ('EARNED','WELCOME')",
                                Integer.class,
                                completed.getId()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT p.remaining FROM loyalty_lots p JOIN loyalty_ledger l ON"
                                        + " l.id=p.ledger_id JOIN loyalty_ledger original ON"
                                        + " original.id=l.source_credit_id WHERE l.subject_id=? AND"
                                        + " l.kind='RESTORED' AND original.kind='ADJUSTED'",
                                Integer.class,
                                subject))
                .isEqualTo(10);
    }

    @Test
    void spentRefundCreditsBecomeDebtAndOffsetFutureEarnings() {
        var completed = order("PICKED_UP", true);
        jdbc.update(
                "UPDATE orders SET subtotal=400,loyalty_eligible_subtotal=400 WHERE id=?",
                completed.getId());
        paid(completed.getId(), "PAID");
        loyalty.reconcile(completed.getId());
        var spending = order("PENDING_PAYMENT", true);
        loyalty.reserve(spending, "SWEET_5");
        jdbc.update(
                "UPDATE payments SET payment_status='REFUNDED' WHERE order_id=?",
                completed.getId());
        loyalty.reconcile(completed.getId());
        loyalty.reconcile(completed.getId());
        var reversed = loyalty.wallet("DEV", subject, null);
        assertThat(reversed.balance()).isZero();
        assertThat(reversed.debt()).isEqualTo(30);
        var next = order("PICKED_UP", true);
        jdbc.update(
                "UPDATE orders SET subtotal=400,loyalty_eligible_subtotal=400 WHERE id=?",
                next.getId());
        paid(next.getId(), "PAID");
        loyalty.reconcile(next.getId());
        var replenished = loyalty.wallet("DEV", subject, null);
        assertThat(replenished.balance()).isEqualTo(10);
        assertThat(replenished.debt()).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT p.remaining FROM loyalty_lots p JOIN loyalty_ledger l ON"
                                    + " l.id=p.ledger_id WHERE l.order_id=? AND l.kind='EARNED'",
                                Integer.class,
                                next.getId()))
                .isEqualTo(10);
    }

    @Test
    void noHistoricalOrUnpaidCreditsAndFlagOffRejectsNewRewards() {
        var historical = order("PICKED_UP", false);
        paid(historical.getId(), "PAID");
        loyalty.reconcile(historical.getId());
        var unpaid = order("PICKED_UP", true);
        loyalty.reconcile(unpaid.getId());
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isZero();
        flags.setGokulRewards(false);
        assertThatThrownBy(() -> loyalty.reserve(order("PENDING_PAYMENT", false), "SWEET_5"))
                .hasMessageContaining("unavailable");
    }

    @Test
    void staleWalletCannotAcceptAChangedCoinCost() {
        grant(100);
        var pending = order("PENDING_PAYMENT", true);
        String number =
                jdbc.queryForObject(
                        "SELECT order_number FROM orders WHERE id=?",
                        String.class,
                        pending.getId());
        String version = loyalty.wallet("DEV", subject, new BigDecimal("149")).policyVersion();
        var before =
                jdbc.queryForMap("SELECT coins,version FROM loyalty_rewards WHERE code='SWEET_5'");
        try {
            jdbc.update(
                    "UPDATE loyalty_rewards SET coins=31,version=version+1 WHERE code='SWEET_5'");
            assertThatThrownBy(() -> checkout.select(number, "SWEET_5", version))
                    .hasMessageContaining("changed");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM loyalty_holds WHERE order_id=?",
                                    Integer.class,
                                    pending.getId()))
                    .isZero();
            assertThat(loyalty.wallet("DEV", subject, null).balance()).isEqualTo(100);
            loyalty.verifyPolicy(loyalty.wallet("DEV", subject, null).policyVersion());
        } finally {
            jdbc.update(
                    "UPDATE loyalty_rewards SET coins=?,version=? WHERE code='SWEET_5'",
                    before.get("coins"),
                    before.get("version"));
        }
    }

    @Test
    void rolloutOffStillCreditsEnrolledOrdersWithTheirPromisedRules() {
        var order = order("PICKED_UP", true);
        paid(order.getId(), "PAID");
        flags.setGokulRewards(false);
        loyalty.reconcile(order.getId());
        loyalty.reconcile(order.getId());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT SUM(coins)::integer FROM loyalty_ledger WHERE subject_id=?"
                                        + " AND kind='EARNED'",
                                Integer.class,
                                subject))
                .isEqualTo(14);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT expiry_days FROM loyalty_ledger WHERE subject_id=? AND"
                                        + " kind='EARNED'",
                                Integer.class,
                                subject))
                .isEqualTo(180);
    }

    @Test
    void configurationChangesDoNotRewriteEnrolledEarningOrExpiry() {
        var order = order("CONFIRMED", true);
        paid(order.getId(), "PAID");
        var divisor = rules.getRupeesPerCoin();
        var minimum = rules.getQualifyingSubtotal();
        int expiry = rules.getExpiryDays(), welcome = rules.getWelcomeCoins();
        try {
            rules.setRupeesPerCoin(new BigDecimal("20"));
            rules.setQualifyingSubtotal(new BigDecimal("200"));
            rules.setExpiryDays(30);
            rules.setWelcomeCoins(15);
            assertThat(loyalty.wallet("DEV", subject, null).pendingCoins()).isEqualTo(14);
            jdbc.update("UPDATE orders SET order_status='PICKED_UP' WHERE id=?", order.getId());
            loyalty.reconcile(order.getId());
            assertThat(loyalty.wallet("DEV", subject, null).balance()).isEqualTo(14);
            assertThat(loyalty.wallet("DEV", subject, null).completedOrders()).isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT expiry_days FROM loyalty_ledger WHERE subject_id=? AND"
                                            + " kind='EARNED'",
                                    Integer.class,
                                    subject))
                    .isEqualTo(180);
        } finally {
            rules.setRupeesPerCoin(divisor);
            rules.setQualifyingSubtotal(minimum);
            rules.setExpiryDays(expiry);
            rules.setWelcomeCoins(welcome);
        }
    }

    @Test
    void pendingAndCompletionUseTheSamePaidEligibilityAndProportionalCouponRules() {
        var unpaid = order("CONFIRMED", true);
        var belowMinimum = order("CONFIRMED", true);
        paid(belowMinimum.getId(), "PAID");
        jdbc.update(
                "UPDATE orders SET subtotal=100,loyalty_eligible_subtotal=100 WHERE id=?",
                belowMinimum.getId());
        var testOrder = order("CONFIRMED", true);
        paid(testOrder.getId(), "PAID");
        jdbc.update("UPDATE orders SET loyalty_test_order=TRUE WHERE id=?", testOrder.getId());
        var excluded = order("CONFIRMED", true);
        paid(excluded.getId(), "PAID");
        String code = "NO-EARN-" + subject;
        jdbc.update("INSERT INTO loyalty_excluded_rebates(code) VALUES (?)", code);
        jdbc.update("UPDATE orders SET rebate_code=? WHERE id=?", code, excluded.getId());
        var eligible = order("CONFIRMED", true);
        paid(eligible.getId(), "PAID");
        jdbc.update(
                "UPDATE orders SET"
                    + " subtotal=400,loyalty_eligible_subtotal=200,rebate_discount_amount=80 WHERE"
                    + " id=?",
                eligible.getId());
        try {
            assertThat(loyalty.wallet("DEV", subject, null).pendingCoins()).isEqualTo(16);
            for (var completed : List.of(belowMinimum, testOrder, excluded, eligible)) {
                jdbc.update(
                        "UPDATE orders SET order_status='PICKED_UP' WHERE id=?", completed.getId());
                loyalty.reconcile(completed.getId());
                loyalty.reconcile(completed.getId());
            }
            assertThat(loyalty.wallet("DEV", subject, null).balance()).isEqualTo(16);
            assertThat(loyalty.wallet("DEV", subject, null).pendingCoins()).isZero();
            assertThat(loyalty.wallet("DEV", subject, null).completedOrders()).isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT coins FROM loyalty_ledger WHERE order_id=? AND"
                                            + " kind='EARNED'",
                                    Integer.class,
                                    belowMinimum.getId()))
                    .isZero();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM loyalty_ledger WHERE order_id=? AND"
                                            + " kind='EARNED'",
                                    Integer.class,
                                    belowMinimum.getId()))
                    .isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM loyalty_excluded_rebates WHERE code=?", code);
        }
    }

    @Test
    void newProductExclusionInvalidatesExistingRewardBeforePayment() {
        grant(100);
        var order = order("PENDING_PAYMENT", true);
        loyalty.reserve(order, "SWEET_5");
        loyalty.verifyPayment(order);
        long category =
                jdbc.queryForObject(
                        "INSERT INTO categories(code,name) VALUES (?,?) RETURNING id",
                        Long.class,
                        "LOY-EX-" + subject,
                        "Rewards exclusion " + subject);
        long product =
                jdbc.queryForObject(
                        "INSERT INTO products(code,name,category_id,sale_mode,base_price) VALUES"
                                + " (?, ?, ?, 'UNIT',149) RETURNING id",
                        Long.class,
                        "LOY-PRODUCT-" + subject,
                        "Excluded reward product " + subject,
                        category);
        order.getItems().getFirst().getProduct().setId(product);
        jdbc.update("INSERT INTO loyalty_excluded_products(product_id) VALUES (?)", product);
        try {
            flags.setGokulRewards(false);
            assertThatThrownBy(() -> loyalty.verifyPayment(order))
                    .hasMessageContaining("eligibility changed");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT state FROM loyalty_holds WHERE order_id=?",
                                    String.class,
                                    order.getId()))
                    .isEqualTo("RESERVED");
        } finally {
            jdbc.update("DELETE FROM loyalty_excluded_products WHERE product_id=?", product);
        }
    }

    @Test
    void anIneligibleSavedCouponDoesNotMarkRewardChangeTransactionRollbackOnly() {
        var order = order("PENDING_PAYMENT", true);
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        tx -> {
                            assertThat(
                                            couponEligibility.findEligibleRebate(
                                                    order, "MISSING-" + subject))
                                    .isEmpty();
                            jdbc.update(
                                    "UPDATE orders SET customer_name='Reward change committed'"
                                            + " WHERE id=?",
                                    order.getId());
                        });
        assertThat(
                        jdbc.queryForObject(
                                "SELECT customer_name FROM orders WHERE id=?",
                                String.class,
                                order.getId()))
                .isEqualTo("Reward change committed");
    }

    @Test
    void hundredConcurrentSpendsCannotOverdrawOneWallet() throws Exception {
        grant(1000);
        var orders = new ArrayList<Order>();
        for (int i = 0; i < 100; i++) orders.add(order("PENDING_PAYMENT", true));
        try (var pool = Executors.newFixedThreadPool(12)) {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (var order : orders)
                tasks.add(
                        () -> {
                            try {
                                new TransactionTemplate(manager)
                                        .executeWithoutResult(
                                                tx -> loyalty.reserve(order, "SWEET_5"));
                                return true;
                            } catch (
                                    org.springframework.web.server.ResponseStatusException
                                            insufficient) {
                                return false;
                            }
                        });
            long successful = 0;
            for (var future : pool.invokeAll(tasks)) if (future.get()) successful++;
            assertThat(successful).isEqualTo(33);
        }
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isEqualTo(10);
    }

    @Test
    void hundredCompletionRetriesEarnOneCredit() throws Exception {
        var order = order("PICKED_UP", true);
        paid(order.getId(), "PAID");
        try (var pool = Executors.newFixedThreadPool(12)) {
            var tasks = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 100; i++)
                tasks.add(
                        () -> {
                            loyalty.reconcile(order.getId());
                            return null;
                        });
            for (var future : pool.invokeAll(tasks)) future.get();
        }
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isEqualTo(14);
    }

    @Test
    void expiryIsAnIdempotentDebitAndJournalCannotBeEdited() {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " loyalty_ledger(environment,subject_id,event_key,kind,coins,reason,expires_at)"
                            + " VALUES ('DEV',?,'expired-fixture','EARNED',40,'Expiry"
                            + " fixture',CURRENT_TIMESTAMP-INTERVAL '1 day') RETURNING id",
                        Long.class,
                        subject);
        jdbc.update("INSERT INTO loyalty_lots(ledger_id,remaining) VALUES (?,40)", id);
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isZero();
        assertThat(loyalty.wallet("DEV", subject, null).balance()).isZero();
        assertThatThrownBy(() -> jdbc.update("UPDATE loyalty_ledger SET coins=400 WHERE id=?", id))
                .hasMessageContaining("append-only");
        assertThat(loyalty.wallet("PROD", subject, null).balance()).isZero();
    }
}
