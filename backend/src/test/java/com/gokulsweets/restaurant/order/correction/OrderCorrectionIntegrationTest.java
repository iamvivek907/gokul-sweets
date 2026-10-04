package com.gokulsweets.restaurant.order.correction;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.customer.identity.CustomerVisitPolicy;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(
    properties = {
      "spring.datasource.hikari.maximum-pool-size=3",
      "spring.datasource.hikari.minimum-idle=0",
      "payment.refund-check-interval-ms=3600000"
    })
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class OrderCorrectionIntegrationTest {
  @Autowired com.gokulsweets.restaurant.inventory.service.OrderInventoryLifecycleService inventory;
  @Autowired JdbcTemplate jdbc;
  @Autowired OrderCorrectionService corrections;
  @Autowired CustomerVisitPolicy visits;
  @Autowired com.gokulsweets.restaurant.rebate.RebateVisitRuleController visitRules;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  @MockitoBean StaffAuthorizationService staff;

  @MockitoSpyBean(name = "inventoryClock")
  Clock clock;

  long source,
      target,
      sourceSlot,
      targetSlot,
      product,
      order,
      payment,
      sourceBp,
      targetBp,
      sourceStock,
      targetStock;
  String reference;
  UUID subject;
  static final Instant NOW = Instant.parse("2026-10-05T04:00:00Z");

  @BeforeEach
  void setup() {
    doReturn(NOW).when(clock).instant();
    doReturn(ZoneId.of("Asia/Kolkata")).when(clock).getZone();
    var user = new com.gokulsweets.restaurant.staff.StaffUser();
    user.setId(
        jdbc.queryForObject(
            "INSERT INTO staff_users(username,password_hash,full_name,role_id) VALUES"
                + " (?,'test-only','Correction manager',(SELECT id FROM roles WHERE"
                + " name='MANAGER')) RETURNING id",
            Long.class,
            "correction-" + UUID.randomUUID()));
    when(staff.getCurrentStaff()).thenReturn(user);
    reference = ("FIX-" + UUID.randomUUID()).toUpperCase(Locale.ROOT);
    subject = UUID.randomUUID();
    source = branch(reference + "S");
    target = branch(reference + "T");
    sourceSlot = slot(source, 1);
    targetSlot = slot(target, 0);
    long category =
        jdbc.queryForObject(
            "INSERT INTO categories(code,name) VALUES (?,'Correction') RETURNING id",
            Long.class,
            reference);
    long tax =
        jdbc.queryForObject(
            "INSERT INTO tax_categories(code,name,cgst_rate,sgst_rate) VALUES (?,'Correction"
                + " tax',0,0) RETURNING id",
            Long.class,
            reference);
    product =
        jdbc.queryForObject(
            "INSERT INTO products(code,name,category_id,tax_category_id,sale_mode,base_price)"
                + " VALUES (?,'Correction item',?,?,'UNIT',100) RETURNING id",
            Long.class,
            reference,
            category,
            tax);
    sourceBp = bp(source);
    targetBp = bp(target);
    sourceStock = stock(sourceBp, 1);
    targetStock = stock(targetBp, 0);
    order =
        jdbc.queryForObject(
            "INSERT INTO"
                + " orders(order_number,branch_id,pickup_slot_id,customer_name,customer_phone,customer_phone_normalized,pickup_type,order_status,reservation_expires_at,subtotal,total_amount)"
                + " VALUES"
                + " (?,?,?,'Customer','9876543210','9876543210','NORMAL','CONFIRMED',TIMESTAMP"
                + " '2026-10-05 09:45:00',100,100) RETURNING id",
            Long.class,
            reference,
            source,
            sourceSlot);
    jdbc.update(
        "INSERT INTO"
            + " order_items(order_id,product_id,product_name,sale_mode,quantity,unit_price,tax_rate,tax_amount,line_total)"
            + " VALUES (?,?,'Correction item','UNIT',1,100,0,0,100)",
        order,
        product);
    payment =
        jdbc.queryForObject(
            "INSERT INTO"
                + " payments(order_id,provider,amount,payment_status,paid_at,provider_order_id,provider_payment_id)"
                + " VALUES (?,'RAZORPAY',100,'PAID',TIMESTAMP '2026-10-05 09:25:00',?,?) RETURNING"
                + " id",
            Long.class,
            order,
            reference + "-provider",
            reference + "-payment");
    jdbc.update(
        "INSERT INTO"
            + " inventory_reservations(allocation_id,reservation_key,order_number,quantity,status,confirmed_at,created_at,updated_at)"
            + " VALUES (?,?,?,1,'CONFIRMED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        sourceStock,
        reference + "-original",
        reference);
    jdbc.update(
        "INSERT INTO verified_order_ownership(order_id,environment,verified_subject_id) VALUES"
            + " (?,'DEV',?)",
        order,
        subject);
    jdbc.update("INSERT INTO order_pickup_codes(order_id,code) VALUES (?,'0042')", order);
  }

  long branch(String code) {
    return jdbc.queryForObject(
        "INSERT INTO branches(code,name) VALUES (?,'Correction branch') RETURNING id",
        Long.class,
        code);
  }

  long slot(long branch, int booked) {
    return jdbc.queryForObject(
        "INSERT INTO pickup_slots(branch_id,slot_date,start_time,end_time,capacity,booked_count)"
            + " VALUES (?,DATE '2026-10-05','18:00','19:00',20,?) RETURNING id",
        Long.class,
        branch,
        booked);
  }

  long bp(long branch) {
    long id =
        jdbc.queryForObject(
            "INSERT INTO branch_products(branch_id,product_id) VALUES (?,?) RETURNING id",
            Long.class,
            branch,
            product);
    jdbc.update(
        "INSERT INTO"
            + " branch_inventory_policies(branch_product_id,control_mode,inventory_unit,online_enabled,created_at,updated_at)"
            + " VALUES (?,'READY_STOCK','PIECE',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        id);
    return id;
  }

  long stock(long bp, int committed) {
    return jdbc.queryForObject(
        "INSERT INTO"
            + " inventory_daily_allocations(branch_product_id,service_date,status,inventory_unit,approved_quantity,ready_quantity,committed_quantity,created_at,updated_at)"
            + " VALUES (?,DATE"
            + " '2026-10-05','READY','PIECE',10,10,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) RETURNING"
            + " id",
        Long.class,
        bp,
        committed);
  }

  OrderCorrectionService.Cancellation cancel(UUID key) {
    var preview = corrections.preview(reference, false);
    return new OrderCorrectionService.Cancellation(
        key, "Wrong branch", preview.refundAmount(), preview.retainedCharges());
  }

  int booked(long slot) {
    return jdbc.queryForObject(
        "SELECT booked_count FROM pickup_slots WHERE id=?", Integer.class, slot);
  }

  long branchOf() {
    return jdbc.queryForObject("SELECT branch_id FROM orders WHERE id=?", Long.class, order);
  }

  @Test
  void customerFullTenMinutesDuringPreparationAndBoundaryIsServerClock() {
    jdbc.update("UPDATE orders SET order_status='PREPARING' WHERE id=?", order);
    doReturn(NOW.plusSeconds(299)).when(clock).instant();
    assertThat(corrections.preview(reference, false).canCancel()).isTrue();
    doReturn(NOW.plusSeconds(300)).when(clock).instant();
    assertThat(corrections.preview(reference, false).canCancel()).isFalse();
    assertThatThrownBy(() -> corrections.cancel(reference, cancel(UUID.randomUUID()), false))
        .hasMessageContaining("window");
    assertThat(booked(sourceSlot)).isEqualTo(1);
  }

  @Test
  void foodRefundExcludesInclusiveFeesAndRepeatedRequestReleasesExactlyOnce() {
    jdbc.update(
        "UPDATE orders SET"
            + " convenience_fee=10,convenience_fee_tax=1,payment_fee=2,payment_fee_tax=.20,priority_charge=5,total_amount=117"
            + " WHERE id=?",
        order);
    jdbc.update("UPDATE payments SET amount=117 WHERE id=?", payment);
    var input = cancel(UUID.randomUUID());
    assertThat(input.acceptedRefundAmount()).isEqualByComparingTo("100");
    assertThat(input.acceptedRetainedCharges()).isEqualByComparingTo("17");
    var result = corrections.cancel(reference, input, false);
    assertThat(result.refundStatus()).isEqualTo("REFUND_PENDING");
    corrections.cancel(reference, input, false);
    assertThat(booked(sourceSlot)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT amount FROM payments WHERE id=?", BigDecimal.class, payment))
        .isEqualByComparingTo("117");
    assertThat(
            jdbc.queryForObject(
                "SELECT refund_amount FROM payments WHERE id=?", BigDecimal.class, payment))
        .isEqualByComparingTo("100");
    assertThat(
            jdbc.queryForObject(
                "SELECT committed_quantity FROM inventory_daily_allocations WHERE id=?",
                BigDecimal.class,
                sourceStock))
        .isZero();
    assertThatThrownBy(
            () ->
                corrections.cancel(
                    reference,
                    new OrderCorrectionService.Cancellation(
                        input.requestKey(),
                        input.reason(),
                        BigDecimal.ONE,
                        input.acceptedRetainedCharges()),
                    false))
        .hasMessageContaining("request key");
  }

  @Test
  void refundReferencesAreUniqueAcrossOrdersAndStableOnRetry() {
    var key = UUID.randomUUID();
    var input = cancel(key);
    corrections.cancel(reference, input, false);
    String first = jdbc.queryForObject("SELECT refund_reference_id FROM payments WHERE id=?", String.class, payment);
    assertThat(UUID.fromString(first.substring(4))).isNotNull();
    corrections.cancel(reference, input, false);
    assertThat(jdbc.queryForObject("SELECT refund_reference_id FROM payments WHERE id=?", String.class, payment)).isEqualTo(first);
    // Request keys are scoped to an order and cannot be used as the provider's global refund id.
    setup();
    corrections.cancel(reference, cancel(key), false);
    String second = jdbc.queryForObject("SELECT refund_reference_id FROM payments WHERE id=?", String.class, payment);
    assertThat(UUID.fromString(second.substring(4))).isNotNull();
    assertThat(second).isNotEqualTo(first);
  }

  @Test
  void staleRefundReviewAndCollectedOrderCannotCancel() {
    var stale = cancel(UUID.randomUUID());
    jdbc.update("UPDATE orders SET convenience_fee=5 WHERE id=?", order);
    assertThatThrownBy(() -> corrections.cancel(reference, stale, false))
        .hasMessageContaining("Review");
    jdbc.update("UPDATE orders SET order_status='PICKED_UP' WHERE id=?", order);
    assertThat(corrections.preview(reference, true).canCancel()).isFalse();
  }

  @Test
  void transferMovesStockCapacityAndRetainsPublicNumberAndPickupCode() {
    Long number =
        jdbc.queryForObject(
            "SELECT customer_order_number FROM orders WHERE id=?", Long.class, order);
    var input =
        new OrderCorrectionService.Transfer(UUID.randomUUID(), target, targetSlot, "Wrong branch");
    corrections.transfer(reference, input);
    corrections.transfer(reference, input);
    assertThat(branchOf()).isEqualTo(target);
    assertThat(booked(sourceSlot)).isZero();
    assertThat(booked(targetSlot)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT committed_quantity FROM inventory_daily_allocations WHERE id=?",
                BigDecimal.class,
                sourceStock))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT committed_quantity FROM inventory_daily_allocations WHERE id=?",
                BigDecimal.class,
                targetStock))
        .isEqualByComparingTo("1");
    assertThat(
            jdbc.queryForObject(
                "SELECT customer_order_number FROM orders WHERE id=?", Long.class, order))
        .isEqualTo(number);
    assertThat(
            jdbc.queryForObject(
                "SELECT code FROM order_pickup_codes WHERE order_id=?", String.class, order))
        .isEqualTo("0042");
    inventory.fulfilOrderInventory(reference, "test staff");
    assertThat(
            jdbc.queryForObject(
                "SELECT fulfilled_quantity FROM inventory_daily_allocations WHERE id=?",
                BigDecimal.class,
                targetStock))
        .isEqualByComparingTo("1");
  }

  @Test
  void differingPriceOrUnavailableStockRollsBackSource() {
    jdbc.update("UPDATE branch_products SET price_override=110 WHERE id=?", targetBp);
    assertThatThrownBy(
            () ->
                corrections.transfer(
                    reference,
                    new OrderCorrectionService.Transfer(
                        UUID.randomUUID(), target, targetSlot, "Wrong branch")))
        .hasMessageContaining("prices");
    jdbc.update("UPDATE branch_products SET price_override=NULL WHERE id=?", targetBp);
    jdbc.update(
        "UPDATE inventory_daily_allocations SET approved_quantity=0,ready_quantity=0 WHERE id=?",
        targetStock);
    assertThatThrownBy(
            () ->
                corrections.transfer(
                    reference,
                    new OrderCorrectionService.Transfer(
                        UUID.randomUUID(), target, targetSlot, "Wrong branch")))
        .isInstanceOf(RuntimeException.class);
    assertThat(branchOf()).isEqualTo(source);
    assertThat(booked(sourceSlot)).isEqualTo(1);
    assertThat(booked(targetSlot)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT committed_quantity FROM inventory_daily_allocations WHERE id=?",
                BigDecimal.class,
                sourceStock))
        .isEqualByComparingTo("1");
  }

  @Test
  void receivingBranchAuthorizationAndPreparationAreChecked() {
    doThrow(
            new org.springframework.security.access.AccessDeniedException(
                "Receiving branch denied"))
        .when(staff)
        .requireBranchAccess(target);
    assertThatThrownBy(
            () ->
                corrections.transfer(
                    reference,
                    new OrderCorrectionService.Transfer(
                        UUID.randomUUID(), target, targetSlot, "Wrong branch")))
        .hasMessageContaining("denied");
    reset(staff);
    jdbc.update("UPDATE orders SET order_status='PREPARING' WHERE id=?", order);
    assertThatThrownBy(
            () ->
                corrections.transfer(
                    reference,
                    new OrderCorrectionService.Transfer(
                        UUID.randomUUID(), target, targetSlot, "Wrong branch")))
        .hasMessageContaining("before preparation");
  }

  @Test
  void simultaneousCancellationOnlyRequestsOneRefund() throws Exception {
    var input = cancel(UUID.randomUUID());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var first =
          executor.submit(
              () -> {
                start.await();
                return corrections.cancel(reference, input, false);
              });
      var second =
          executor.submit(
              () -> {
                start.await();
                return corrections.cancel(reference, input, false);
              });
      start.countDown();
      assertThat(first.get(15, TimeUnit.SECONDS).refundStatus()).isEqualTo("REFUND_PENDING");
      assertThat(second.get(15, TimeUnit.SECONDS).refundStatus()).isEqualTo("REFUND_PENDING");
    }
    assertThat(booked(sourceSlot)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_corrections WHERE order_id=? AND kind='CANCEL'",
                Integer.class,
                order))
        .isEqualTo(1);
  }

  long rebate() {
    return jdbc.queryForObject(
        "INSERT INTO"
            + " rebates(code,name,scope,rebate_type,rebate_value,branch_id,valid_from,valid_until,created_by)"
            + " VALUES (?,'Visit offer','GENERAL','FIXED_AMOUNT',5,?,TIMESTAMP '2026-10-01"
            + " 00:00:00',TIMESTAMP '2026-10-31 00:00:00',?) RETURNING id",
        Long.class,
        reference,
        source,
        staff.getCurrentStaff().getId());
  }

  @Test
  void visitOfferRequiresCompletedOwnerAndDefaultsRemainOpen() {
    long rebate = rebate();
    var draft = new com.gokulsweets.restaurant.order.entity.Order();
    draft.setVerifiedOfferSubject(
        new com.gokulsweets.restaurant.customer.identity.VerifiedOrderOwnership.Subject(
            "DEV", subject));
    assertThat(visits.offerEligible(rebate, draft)).isTrue();
    visitRules.save(
        rebate,
        new com.gokulsweets.restaurant.rebate.RebateVisitRuleController.Input(1, "First visit"));
    assertThat(visits.offerEligible(rebate, draft)).isFalse();
    jdbc.update("UPDATE orders SET order_status='PICKED_UP' WHERE id=?", order);
    assertThat(visits.offerEligible(rebate, draft)).isTrue();
    assertThat(visits.offerEligible(rebate, new com.gokulsweets.restaurant.order.entity.Order()))
        .isFalse();
    draft.setVerifiedOfferSubject(
        new com.gokulsweets.restaurant.customer.identity.VerifiedOrderOwnership.Subject(
            "PROD", subject));
    assertThat(visits.offerEligible(rebate, draft)).isFalse();
  }

  @Test
  void acceptedEligibilityLocksConcurrentPolicyCreationButReadOnlyPreviewDoesNot()
      throws Exception {
    long rebate = rebate();
    var draft = new com.gokulsweets.restaurant.order.entity.Order();
    var read = new org.springframework.transaction.support.TransactionTemplate(transactions);
    read.setReadOnly(true);
    Boolean preview =
        read.execute(
            tx -> {
              jdbc.execute("SET TRANSACTION READ ONLY");
              return visits.offerEligible(rebate, draft);
            });
    assertThat(preview).isTrue();
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var accepting =
          executor.submit(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(transactions)
                      .execute(
                          tx -> {
                            assertThat(visits.offerEligible(rebate, draft)).isTrue();
                            locked.countDown();
                            try {
                              if (!release.await(5, TimeUnit.SECONDS))
                                throw new IllegalStateException("Policy test timeout");
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                              throw new RuntimeException(e);
                            }
                            return true;
                          }));
      assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
      var update =
          executor.submit(
              () -> {
                started.countDown();
                visitRules.save(
                    rebate,
                    new com.gokulsweets.restaurant.rebate.RebateVisitRuleController.Input(
                        5, "Regular visit offer"));
                return true;
              });
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      try {
        assertThatThrownBy(() -> update.get(150, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
      } finally {
        release.countDown();
      }
      assertThat(accepting.get(5, TimeUnit.SECONDS)).isTrue();
      assertThat(update.get(5, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(visits.offerEligible(rebate, draft)).isFalse();
  }

  @Test
  void badgeOnlyCountsCompletedPaidOwnedVisits() {
    assertThat(visits.completed("DEV", subject)).isZero();
    jdbc.update("UPDATE orders SET order_status='PICKED_UP' WHERE id=?", order);
    assertThat(visits.completed("DEV", subject)).isEqualTo(1);
    assertThat(visits.completed("PROD", subject)).isZero();
    assertThat(visits.completed("DEV", UUID.randomUUID())).isZero();
    jdbc.update("UPDATE payments SET payment_status='REFUND_PENDING' WHERE id=?", payment);
    assertThat(visits.completed("DEV", subject)).isZero();
  }
}
