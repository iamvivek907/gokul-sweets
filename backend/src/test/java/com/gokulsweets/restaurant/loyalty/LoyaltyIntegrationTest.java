package com.gokulsweets.restaurant.loyalty;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.entity.*;
import com.gokulsweets.restaurant.order.enums.*;
import com.gokulsweets.restaurant.product.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class LoyaltyIntegrationTest {
 @Autowired LoyaltyService loyalty;
 @Autowired LoyaltyCheckoutService checkout;
 @Autowired LoyaltyProperties rules;
 @Autowired com.gokulsweets.restaurant.rebate.RebateEligibilityService couponEligibility;
 @Autowired JdbcTemplate jdbc;
 @Autowired EnhancementProperties flags;
 @Autowired PlatformTransactionManager manager;
 private boolean rewards,identity,quote;
 private UUID subject;private long branch;private long slot;
 @BeforeEach void setup(){
  rewards=flags.isGokulRewards();identity=flags.isCustomerOtpIdentity();quote=flags.isAcceptedCheckoutQuote();flags.setGokulRewards(true);flags.setCustomerOtpIdentity(true);flags.setAcceptedCheckoutQuote(true);
  subject=UUID.randomUUID();branch=jdbc.queryForObject("INSERT INTO branches(code,name) VALUES (?,'Rewards test') RETURNING id",Long.class,"LOY-"+subject.toString().substring(0,8));
  slot=jdbc.queryForObject("INSERT INTO pickup_slots(branch_id,slot_date,start_time,end_time,capacity) VALUES (?,CURRENT_DATE,'10:00','10:30',500) RETURNING id",Long.class,branch);
  jdbc.update("INSERT INTO verified_customer_subjects(id,environment,verified_phone) VALUES (?,'DEV',?)",subject,"+91"+(6000000000L+Math.floorMod(subject.getLeastSignificantBits(),3999999999L)));
  loyalty.wallet("DEV",subject,null);
 }
 @AfterEach void restore(){flags.setGokulRewards(rewards);flags.setCustomerOtpIdentity(identity);flags.setAcceptedCheckoutQuote(quote);}
 @Test void rewardsEnabledQuoteCanRunInAPostgresReadOnlyTransaction(){
  var validation=org.mockito.Mockito.mock(com.gokulsweets.restaurant.order.service.OrderValidationService.class);
  var calculation=org.mockito.Mockito.mock(com.gokulsweets.restaurant.order.service.OrderCalculationService.class);
  var service=new com.gokulsweets.restaurant.order.service.CheckoutQuoteService(flags,validation,calculation,org.mockito.Mockito.mock(com.gokulsweets.restaurant.order.repository.OrderRepository.class),loyalty);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"signingKey","readonly-quote-regression-signing-key");
  var request=new com.gokulsweets.restaurant.order.dto.CreateOrderRequest(branch,slot,"Customer","9876543210",PickupType.NORMAL,List.of());
  var data=org.mockito.Mockito.mock(com.gokulsweets.restaurant.order.service.model.ValidatedOrderData.class);
  var amounts=new com.gokulsweets.restaurant.order.service.model.OrderCalculationResult(List.of(),new BigDecimal("149"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("149"));
  org.mockito.Mockito.when(validation.validate(request)).thenReturn(data);org.mockito.Mockito.when(calculation.calculate(data)).thenReturn(amounts);
  var transaction=new TransactionTemplate(manager);transaction.setReadOnly(true);
  var result=transaction.execute(status->{jdbc.execute("SET TRANSACTION READ ONLY");assertThat(jdbc.queryForObject("SHOW transaction_read_only",String.class)).isEqualTo("on");return service.preview(request,null);});
  assertThat(result.token()).isNotBlank();
  new TransactionTemplate(manager).executeWithoutResult(status->service.accept(request,null,amounts,result.token()));
 }
 private void grant(int coins){
  long ledger=jdbc.queryForObject("INSERT INTO loyalty_ledger(environment,subject_id,event_key,kind,coins,reason,expires_at) VALUES ('DEV',? ,?,'ADJUSTED',?,'Test fixture',CURRENT_TIMESTAMP+INTERVAL '180 days') RETURNING id",Long.class,subject,"fixture:"+UUID.randomUUID(),coins);
  jdbc.update("INSERT INTO loyalty_lots(ledger_id,remaining) VALUES (?,?)",ledger,coins);
 }
 private Order order(String status,boolean enrolled){
  long id=jdbc.queryForObject("INSERT INTO orders(order_number,branch_id,pickup_slot_id,customer_name,customer_phone,pickup_type,order_status,subtotal,tax_amount,total_amount,loyalty_enrolled,loyalty_eligible_subtotal,reservation_expires_at) VALUES (?,?,?,'Customer','9876543210','NORMAL',?,149,8,167,?,149,CURRENT_TIMESTAMP+INTERVAL '15 minutes') RETURNING id",Long.class,"GKS-LOY-"+UUID.randomUUID(),branch,slot,status,enrolled);
  jdbc.update("INSERT INTO verified_order_ownership(order_id,environment,verified_subject_id) VALUES (?,'DEV',?)",id,subject);
  var order=new Order();order.setId(id);order.setOrderNumber("GKS-"+id);order.setOrderStatus(OrderStatus.valueOf(status));order.setSubtotal(new BigDecimal("149"));order.setTaxAmount(new BigDecimal("8"));order.setConvenienceFee(BigDecimal.TEN);order.setPaymentFeeRate(new BigDecimal("2"));
  var item=new OrderItem();var product=new Product();product.setId(999999L);item.setProduct(product);item.setLineTotal(new BigDecimal("149"));item.setTaxAmount(BigDecimal.ZERO);order.getItems().add(item);return order;
 }
 private void paid(long id,String status){jdbc.update("INSERT INTO payments(order_id,provider,amount,payment_status) VALUES (?,'PHONEPE',167,?)",id,status);}
 @Test void holdsOnlySelectedCoinsAndPreservesUndiscountedTaxAndFees(){
  grant(1000);var order=order("PENDING_PAYMENT",true);loyalty.reserve(order,"SWEET_5");
  assertThat(order.getLoyaltyCoins()).isEqualTo(30);assertThat(order.getTotalAmount()).isEqualByComparingTo("165.24");assertThat(order.getTaxAmount()).isEqualByComparingTo("8");assertThat(order.getConvenienceFee()).isEqualByComparingTo("10");
  assertThat(loyalty.wallet("DEV",subject,null).balance()).isEqualTo(970);
  assertThatThrownBy(()->loyalty.reserve(order,"SWEET_5")).hasMessageContaining("already");
  loyalty.remove(order);loyalty.remove(order);assertThat(loyalty.wallet("DEV",subject,null).balance()).isEqualTo(1000);
  loyalty.reserve(order,"SWEET_5");assertThat(loyalty.wallet("DEV",subject,null).balance()).isEqualTo(970);
 }
 @Test void ladderMinimumAndTenPercentCapAreServerEnforced(){
  grant(1000);assertThatThrownBy(()->loyalty.preview("DEV",subject,new BigDecimal("100"),"SWEET_5")).hasMessageContaining("eligible");
 }
 @Test void capRejectsSeventyFiveRupeeRewardAtSixNinetyNineButAllowsSevenFifty(){
  grant(1000);assertThatThrownBy(()->loyalty.preview("DEV",subject,new BigDecimal("699"),"SWEET_75")).hasMessageContaining("eligible");
  assertThat(loyalty.preview("DEV",subject,new BigDecimal("750"),"SWEET_75").coins()).isEqualTo(300);
 }
 @Test void completedPaymentCreditsOnceAndFullRefundUsesCompensatingEvents(){
  var order=order("PICKED_UP",true);paid(order.getId(),"PAID");loyalty.reconcile(order.getId());loyalty.reconcile(order.getId());
  assertThat(loyalty.wallet("DEV",subject,null).balance()).isEqualTo(14);
  jdbc.update("UPDATE payments SET payment_status='REFUNDED' WHERE order_id=?",order.getId());loyalty.reconcile(order.getId());loyalty.reconcile(order.getId());
  assertThat(loyalty.wallet("DEV",subject,null).balance()).isZero();assertThat(loyalty.wallet("DEV",subject,null).completedOrders()).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loyalty_ledger WHERE order_id=? AND kind='EARNED'",Integer.class,order.getId())).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loyalty_ledger WHERE order_id=? AND kind='REVERSED'",Integer.class,order.getId())).isEqualTo(1);
 }
 @Test void noHistoricalOrUnpaidCreditsAndFlagOffRejectsNewRewards(){
  var historical=order("PICKED_UP",false);paid(historical.getId(),"PAID");loyalty.reconcile(historical.getId());
  var unpaid=order("PICKED_UP",true);loyalty.reconcile(unpaid.getId());assertThat(loyalty.wallet("DEV",subject,null).balance()).isZero();
  flags.setGokulRewards(false);assertThatThrownBy(()->loyalty.reserve(order("PENDING_PAYMENT",false),"SWEET_5")).hasMessageContaining("unavailable");
 }
 @Test void staleWalletCannotAcceptAChangedCoinCost(){
  grant(100);var pending=order("PENDING_PAYMENT",true);String number=jdbc.queryForObject("SELECT order_number FROM orders WHERE id=?",String.class,pending.getId());String version=loyalty.wallet("DEV",subject,new BigDecimal("149")).policyVersion();
  var before=jdbc.queryForMap("SELECT coins,version FROM loyalty_rewards WHERE code='SWEET_5'");
  try{
   jdbc.update("UPDATE loyalty_rewards SET coins=31,version=version+1 WHERE code='SWEET_5'");
   assertThatThrownBy(()->checkout.select(number,"SWEET_5",version)).hasMessageContaining("changed");
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loyalty_holds WHERE order_id=?",Integer.class,pending.getId())).isZero();
   assertThat(loyalty.wallet("DEV",subject,null).balance()).isEqualTo(100);
   loyalty.verifyPolicy(loyalty.wallet("DEV",subject,null).policyVersion());
  }finally{jdbc.update("UPDATE loyalty_rewards SET coins=?,version=? WHERE code='SWEET_5'",before.get("coins"),before.get("version"));}
 }
 @Test void rolloutOffStillCreditsEnrolledOrdersWithTheirPromisedRules(){
  var order=order("PICKED_UP",true);paid(order.getId(),"PAID");
  flags.setGokulRewards(false);loyalty.reconcile(order.getId());loyalty.reconcile(order.getId());
  assertThat(jdbc.queryForObject("SELECT SUM(coins)::integer FROM loyalty_ledger WHERE subject_id=? AND kind='EARNED'",Integer.class,subject)).isEqualTo(14);
  assertThat(jdbc.queryForObject("SELECT expiry_days FROM loyalty_ledger WHERE subject_id=? AND kind='EARNED'",Integer.class,subject)).isEqualTo(180);
 }
 @Test void configurationChangesDoNotRewriteEnrolledEarningOrExpiry(){
  var order=order("CONFIRMED",true);paid(order.getId(),"PAID");
  var divisor=rules.getRupeesPerCoin();var minimum=rules.getQualifyingSubtotal();int expiry=rules.getExpiryDays(),welcome=rules.getWelcomeCoins();
  try{
   rules.setRupeesPerCoin(new BigDecimal("20"));rules.setQualifyingSubtotal(new BigDecimal("200"));rules.setExpiryDays(30);rules.setWelcomeCoins(15);
   assertThat(loyalty.wallet("DEV",subject,null).pendingCoins()).isEqualTo(14);
   jdbc.update("UPDATE orders SET order_status='PICKED_UP' WHERE id=?",order.getId());loyalty.reconcile(order.getId());
   assertThat(loyalty.wallet("DEV",subject,null).balance()).isEqualTo(14);
   assertThat(loyalty.wallet("DEV",subject,null).completedOrders()).isEqualTo(1);
   assertThat(jdbc.queryForObject("SELECT expiry_days FROM loyalty_ledger WHERE subject_id=? AND kind='EARNED'",Integer.class,subject)).isEqualTo(180);
  }finally{rules.setRupeesPerCoin(divisor);rules.setQualifyingSubtotal(minimum);rules.setExpiryDays(expiry);rules.setWelcomeCoins(welcome);}
 }
 @Test void pendingAndCompletionUseTheSamePaidEligibilityAndProportionalCouponRules(){
  var unpaid=order("CONFIRMED",true);
  var belowMinimum=order("CONFIRMED",true);paid(belowMinimum.getId(),"PAID");
  jdbc.update("UPDATE orders SET subtotal=100,loyalty_eligible_subtotal=100 WHERE id=?",belowMinimum.getId());
  var testOrder=order("CONFIRMED",true);paid(testOrder.getId(),"PAID");jdbc.update("UPDATE orders SET loyalty_test_order=TRUE WHERE id=?",testOrder.getId());
  var excluded=order("CONFIRMED",true);paid(excluded.getId(),"PAID");String code="NO-EARN-"+subject;
  jdbc.update("INSERT INTO loyalty_excluded_rebates(code) VALUES (?)",code);jdbc.update("UPDATE orders SET rebate_code=? WHERE id=?",code,excluded.getId());
  var eligible=order("CONFIRMED",true);paid(eligible.getId(),"PAID");
  jdbc.update("UPDATE orders SET subtotal=400,loyalty_eligible_subtotal=200,rebate_discount_amount=80 WHERE id=?",eligible.getId());
  try{
   assertThat(loyalty.wallet("DEV",subject,null).pendingCoins()).isEqualTo(16);
   for(var completed:List.of(belowMinimum,testOrder,excluded,eligible)){
    jdbc.update("UPDATE orders SET order_status='PICKED_UP' WHERE id=?",completed.getId());loyalty.reconcile(completed.getId());loyalty.reconcile(completed.getId());
   }
   assertThat(loyalty.wallet("DEV",subject,null).balance()).isEqualTo(16);
   assertThat(loyalty.wallet("DEV",subject,null).pendingCoins()).isZero();
   assertThat(loyalty.wallet("DEV",subject,null).completedOrders()).isEqualTo(1);
   assertThat(jdbc.queryForObject("SELECT coins FROM loyalty_ledger WHERE order_id=? AND kind='EARNED'",Integer.class,belowMinimum.getId())).isZero();
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loyalty_ledger WHERE order_id=? AND kind='EARNED'",Integer.class,belowMinimum.getId())).isEqualTo(1);
  }finally{jdbc.update("DELETE FROM loyalty_excluded_rebates WHERE code=?",code);}
 }
 @Test void newProductExclusionInvalidatesExistingRewardBeforePayment(){
  grant(100);var order=order("PENDING_PAYMENT",true);loyalty.reserve(order,"SWEET_5");loyalty.verifyPayment(order);
  long category=jdbc.queryForObject("INSERT INTO categories(code,name) VALUES (?,?) RETURNING id",Long.class,"LOY-EX-"+subject,"Rewards exclusion "+subject);
  long product=jdbc.queryForObject("INSERT INTO products(code,name,category_id,sale_mode,base_price) VALUES (?, ?, ?, 'UNIT',149) RETURNING id",Long.class,"LOY-PRODUCT-"+subject,"Excluded reward product "+subject,category);
  order.getItems().getFirst().getProduct().setId(product);
  jdbc.update("INSERT INTO loyalty_excluded_products(product_id) VALUES (?)",product);
  try{
   flags.setGokulRewards(false);
   assertThatThrownBy(()->loyalty.verifyPayment(order)).hasMessageContaining("eligibility changed");
   assertThat(jdbc.queryForObject("SELECT state FROM loyalty_holds WHERE order_id=?",String.class,order.getId())).isEqualTo("RESERVED");
  }finally{jdbc.update("DELETE FROM loyalty_excluded_products WHERE product_id=?",product);}
 }
 @Test void anIneligibleSavedCouponDoesNotMarkRewardChangeTransactionRollbackOnly(){
  var order=order("PENDING_PAYMENT",true);
  new TransactionTemplate(manager).executeWithoutResult(tx->{
   assertThat(couponEligibility.findEligibleRebate(order,"MISSING-"+subject)).isEmpty();
   jdbc.update("UPDATE orders SET customer_name='Reward change committed' WHERE id=?",order.getId());
  });
  assertThat(jdbc.queryForObject("SELECT customer_name FROM orders WHERE id=?",String.class,order.getId())).isEqualTo("Reward change committed");
 }
 @Test void hundredConcurrentSpendsCannotOverdrawOneWallet() throws Exception {
  grant(1000);var orders=new ArrayList<Order>();for(int i=0;i<100;i++)orders.add(order("PENDING_PAYMENT",true));
  try(var pool=Executors.newFixedThreadPool(12)){
   var tasks=new ArrayList<Callable<Boolean>>();for(var order:orders)tasks.add(()->{try{new TransactionTemplate(manager).executeWithoutResult(tx->loyalty.reserve(order,"SWEET_5"));return true;}catch(org.springframework.web.server.ResponseStatusException insufficient){return false;}});
   long successful=0;for(var future:pool.invokeAll(tasks))if(future.get())successful++;
   assertThat(successful).isEqualTo(33);
  }
  assertThat(loyalty.wallet("DEV",subject,null).balance()).isEqualTo(10);
 }
 @Test void hundredCompletionRetriesEarnOneCredit() throws Exception {
  var order=order("PICKED_UP",true);paid(order.getId(),"PAID");
  try(var pool=Executors.newFixedThreadPool(12)){
   var tasks=new ArrayList<Callable<Void>>();for(int i=0;i<100;i++)tasks.add(()->{loyalty.reconcile(order.getId());return null;});
   for(var future:pool.invokeAll(tasks))future.get();
  }
  assertThat(loyalty.wallet("DEV",subject,null).balance()).isEqualTo(14);
 }
 @Test void expiryIsAnIdempotentDebitAndJournalCannotBeEdited(){
  long id=jdbc.queryForObject("INSERT INTO loyalty_ledger(environment,subject_id,event_key,kind,coins,reason,expires_at) VALUES ('DEV',?,'expired-fixture','EARNED',40,'Expiry fixture',CURRENT_TIMESTAMP-INTERVAL '1 day') RETURNING id",Long.class,subject);
  jdbc.update("INSERT INTO loyalty_lots(ledger_id,remaining) VALUES (?,40)",id);
  assertThat(loyalty.wallet("DEV",subject,null).balance()).isZero();assertThat(loyalty.wallet("DEV",subject,null).balance()).isZero();
  assertThatThrownBy(()->jdbc.update("UPDATE loyalty_ledger SET coins=400 WHERE id=?",id)).hasMessageContaining("append-only");
  assertThat(loyalty.wallet("PROD",subject,null).balance()).isZero();
 }
}
