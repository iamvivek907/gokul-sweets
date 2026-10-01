package com.gokulsweets.restaurant.order.service;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=1", "spring.datasource.hikari.connection-timeout=1000"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class PickupCodeIntegrationTest {
 @Autowired AdminOrderWorkflowService workflow;
 @Autowired com.gokulsweets.restaurant.order.lifecycle.service.AdminOrderLifecycleCoordinator lifecycle;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.gokulsweets.restaurant.inventory.service.OrderInventoryLifecycleService inventory;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.gokulsweets.restaurant.security.StaffAuthorizationService staff;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox notifications;
 @Test void everyStandardPickupTransitionRequiresCodeAndStaffPermission() {
  codes.issueForPaidPayment(payment);jdbc.update("UPDATE order_pickup_codes SET code='0042' WHERE order_id=?",id);
  org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException("Not authorized")).when(staff).requirePermission(com.gokulsweets.restaurant.staff.PermissionName.ORDER_MARK_PICKED_UP);
  assertThatThrownBy(()->workflow.transitionStatus(number,com.gokulsweets.restaurant.order.enums.OrderStatus.PICKED_UP,"0042")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  assertThat(jdbc.queryForObject("SELECT failed_attempts FROM order_pickup_codes WHERE order_id=?",Integer.class,id)).isZero();
  org.mockito.Mockito.reset(staff);
  assertThatThrownBy(()->workflow.transitionStatus(number,com.gokulsweets.restaurant.order.enums.OrderStatus.PICKED_UP)).isInstanceOf(IllegalArgumentException.class);
  assertThat(jdbc.queryForObject("SELECT order_status FROM orders WHERE id=?",String.class,id)).isEqualTo("READY_FOR_PICKUP");
  var response=workflow.transitionStatus(number,com.gokulsweets.restaurant.order.enums.OrderStatus.PICKED_UP,"0042");
  assertThat(response.orderStatus()).isEqualTo(com.gokulsweets.restaurant.order.enums.OrderStatus.PICKED_UP);
  assertThat(response.toString()).doesNotContain("0042");
  assertThat(codes.customerCode(number).code()).isNull();
  assertThat(workflow.transitionStatus(number,com.gokulsweets.restaurant.order.enums.OrderStatus.PICKED_UP,"9999").orderStatus()).isEqualTo(com.gokulsweets.restaurant.order.enums.OrderStatus.PICKED_UP);
 }

 @Test void inventoryFailureRollsBackValidCodeAndStatusWithOneConnection() {
  codes.issueForPaidPayment(payment);jdbc.update("UPDATE order_pickup_codes SET code='0042' WHERE order_id=?",id);
  org.mockito.Mockito.doThrow(new IllegalStateException("Inventory failed")).when(inventory).fulfilOrderInventory(org.mockito.ArgumentMatchers.eq(number),org.mockito.ArgumentMatchers.anyString());
  assertThatThrownBy(()->lifecycle.transitionStatus(number,com.gokulsweets.restaurant.order.enums.OrderStatus.PICKED_UP,"0042")).hasMessageContaining("Inventory failed");
  assertThat(jdbc.queryForObject("SELECT order_status FROM orders WHERE id=?",String.class,id)).isEqualTo("READY_FOR_PICKUP");
  assertThat(codes.customerCode(number).code()).isEqualTo("0042");
 }
 @Test void lateCollectionCommitsWrongAttemptsButRequiresCodeBeforeHandover() {
  codes.issueForPaidPayment(payment);jdbc.update("UPDATE order_pickup_codes SET code='0042' WHERE order_id=?",id);
  jdbc.update("UPDATE orders SET order_status='PICKUP_WINDOW_EXPIRED' WHERE id=?",id);
  assertThatThrownBy(()->lifecycle.collectLateOrder(number,"9999")).isInstanceOf(PickupCodeRejectedException.class);
  assertThat(jdbc.queryForObject("SELECT failed_attempts FROM order_pickup_codes WHERE order_id=?",Integer.class,id)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT order_status FROM orders WHERE id=?",String.class,id)).isEqualTo("PICKUP_WINDOW_EXPIRED");
  org.mockito.Mockito.verifyNoInteractions(inventory);
  assertThat(lifecycle.collectLateOrder(number,"0042").orderStatus()).isEqualTo(com.gokulsweets.restaurant.order.enums.OrderStatus.PICKED_UP);
 }

 @Autowired JdbcTemplate jdbc;@Autowired PickupCodeService codes;@Autowired PickupCodeAttempts attempts;@Autowired OrderRepository orders;@Autowired PlatformTransactionManager transactions;
 long branch,slot,id,payment;String number;
 @BeforeEach void setup(){org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("pickup-test","test",java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ORDER_VIEW"))));number="OTP-"+UUID.randomUUID();branch=jdbc.queryForObject("INSERT INTO branches(code,name) VALUES (?, 'Code branch') RETURNING id",Long.class,number);slot=jdbc.queryForObject("INSERT INTO pickup_slots(branch_id,slot_date,start_time,end_time,capacity) VALUES (?,CURRENT_DATE,'10:00','11:00',50) RETURNING id",Long.class,branch);id=jdbc.queryForObject("INSERT INTO orders(order_number,branch_id,pickup_slot_id,customer_name,customer_phone,pickup_type,order_status,reservation_expires_at) VALUES (?,?,?,'Customer','9876543210','NORMAL','READY_FOR_PICKUP',CURRENT_TIMESTAMP) RETURNING id",Long.class,number,branch,slot);payment=jdbc.queryForObject("INSERT INTO payments(order_id,provider,amount,payment_status) VALUES (?,'PHONEPE',100,'PAID') RETURNING id",Long.class,id);}
 @AfterEach void cleanup(){org.springframework.security.core.context.SecurityContextHolder.clearContext();jdbc.update("DELETE FROM order_pickup_codes WHERE order_id=?",id);jdbc.update("DELETE FROM payments WHERE order_id=?",id);jdbc.update("DELETE FROM orders WHERE id=?",id);jdbc.update("DELETE FROM pickup_slots WHERE id=?",slot);jdbc.update("DELETE FROM branches WHERE id=?",branch);}
 @Test void confirmedPaymentIssuesStableCodeAndRollbackDoesNotConsumeIt(){codes.issueForPaidPayment(payment);String code=codes.customerCode(number).code();assertThat(code).matches("[0-9]{4}");codes.issueForPaidPayment(payment);assertThat(codes.customerCode(number).code()).isEqualTo(code);jdbc.update("UPDATE order_pickup_codes SET code='0042' WHERE order_id=?",id);var tx=new TransactionTemplate(transactions);assertThatThrownBy(()->tx.executeWithoutResult(status->{codes.verifyAndConsume(orders.findForUpdate(number).orElseThrow(),"0042");throw new IllegalStateException("Simulated inventory failure");})).isInstanceOf(IllegalStateException.class);assertThat(codes.customerCode(number).code()).isEqualTo("0042");tx.executeWithoutResult(status->codes.verifyAndConsume(orders.findForUpdate(number).orElseThrow(),"0042"));assertThat(codes.customerCode(number).code()).isNull();assertThat(attempts.check(id,"0042")).contains("already been used");}
 @Test void missingWrongAndRepeatedGuessesCommitWithoutChangingOrderWithOnePooledConnection(){codes.issueForPaidPayment(payment);jdbc.update("UPDATE order_pickup_codes SET code='0042' WHERE order_id=?",id);var tx=new TransactionTemplate(transactions);for(int i=0;i<4;i++)assertThatThrownBy(()->workflow.transitionStatus(number,com.gokulsweets.restaurant.order.enums.OrderStatus.PICKED_UP,"9999")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Incorrect");assertThatThrownBy(()->workflow.transitionStatus(number,com.gokulsweets.restaurant.order.enums.OrderStatus.PICKED_UP,null)).hasMessageContaining("15 minutes");assertThat(jdbc.queryForObject("SELECT order_status FROM orders WHERE id=?",String.class,id)).isEqualTo("READY_FOR_PICKUP");assertThat(jdbc.queryForObject("SELECT consumed_at IS NULL FROM order_pickup_codes WHERE order_id=?",Boolean.class,id)).isTrue();assertThat(attempts.check(id,"0042")).contains("15 minutes");assertThat(jdbc.queryForObject("SELECT failed_attempts FROM order_pickup_codes WHERE order_id=?",Integer.class,id)).isEqualTo(5);jdbc.update("UPDATE order_pickup_codes SET locked_until=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE order_id=?",id);assertThat(attempts.check(id,"0042")).isNull();}
 @Test void partiallyPaidOrdersCannotIssueOrUsePickupCode(){jdbc.update("UPDATE orders SET total_amount=120 WHERE id=?",id);assertThat(codes.customerCode(number).code()).isNull();codes.issueForPaidPayment(payment);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_pickup_codes WHERE order_id=?",Integer.class,id)).isZero();assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(status->codes.verifyAndConsume(orders.findForUpdate(number).orElseThrow(),"0042"))).hasMessageContaining("Payment must be confirmed");}
 @Test void unpaidCancelledAndDeliveryOrdersCannotRevealOrUsePickupCode(){jdbc.update("UPDATE payments SET payment_status='PENDING' WHERE id=?",payment);assertThat(codes.customerCode(number).code()).isNull();assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(status->codes.verifyAndConsume(orders.findForUpdate(number).orElseThrow(),"0042"))).hasMessageContaining("Payment must be confirmed");jdbc.update("UPDATE payments SET payment_status='PAID' WHERE id=?",payment);jdbc.update("UPDATE orders SET order_status='CANCELLED' WHERE id=?",id);assertThat(codes.customerCode(number).code()).isNull();}
}
