package com.gokulsweets.restaurant.order.service;
import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"gokul.features.admin-preparation-board=true","gokul.notifications.staff.scheduler-enabled=false"})
@Transactional
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class KitchenPlanningIntegrationTest {
 @Autowired KitchenPlanningService service;
 @Autowired com.gokulsweets.restaurant.order.repository.OrderRepository orders;
 @Autowired JdbcTemplate jdbc;
 @Autowired EnhancementProperties flags;
 @MockitoBean StaffAuthorizationService staff;
 @MockitoSpyBean ApplicationClock clock;
 long branch,slot;
 @BeforeEach void setup(){
  SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("test","test","ORDER_VIEW"));
  doReturn(LocalDateTime.of(2026,10,1,17,0)).when(clock).now();
  branch=jdbc.queryForObject("INSERT INTO branches(code,name) VALUES (?,'Kitchen test') RETURNING id",Long.class,"KITCHEN-"+UUID.randomUUID());
  slot=jdbc.queryForObject("INSERT INTO pickup_slots(branch_id,slot_date,start_time,end_time,capacity) VALUES (?,'2026-10-01','18:00','18:30',100) RETURNING id",Long.class,branch);
 }
 @AfterEach void clear(){flags.setAdminPreparationBoard(true);SecurityContextHolder.clearContext();}
 void order(String status,long branchId,long slotId){jdbc.update("INSERT INTO orders(order_number,branch_id,pickup_slot_id,customer_name,customer_phone,pickup_type,order_status,reservation_expires_at) VALUES (?,?,?,'Test','9876543210','NORMAL',?,CURRENT_TIMESTAMP)","KITCHEN-"+UUID.randomUUID(),branchId,slotId,status);}
 @Test void paginatesAndGroupsAllOrdersAtTheIstPreparationBoundary(){
  for(int i=0;i<23;i++)order("CONFIRMED",branch,slot);
  order("PREPARING",branch,slot);order("READY_FOR_PICKUP",branch,slot);order("CANCELLED",branch,slot);order("PENDING_PAYMENT",branch,slot);
  var first=service.get(branch,KitchenPlanningService.Filter.ELIGIBLE,LocalDate.of(2026,10,1),null,0);
  assertThat(queue(LocalDate.of(2026,10,1),LocalTime.of(18,0),0)).hasSize(20);
  assertThat(queue(LocalDate.of(2026,10,1),LocalTime.of(18,0),1)).hasSize(3);
  assertThat(queueCount(LocalDate.of(2026,10,1),LocalTime.of(18,0))).isEqualTo(23);
  assertThat(first.total()).isEqualTo(23);assertThat(first.orders()).hasSize(20);assertThat(first.slots()).hasSize(1);
  assertThat(first.slots().getFirst().total()).isEqualTo(25);assertThat(first.counts().get("PREPARING")).isEqualTo(1);
  assertThat(service.get(branch,KitchenPlanningService.Filter.ELIGIBLE,null,null,1).orders()).hasSize(3);
  doReturn(LocalDateTime.of(2026,10,1,16,59)).when(clock).now();
  assertThat(service.get(branch,KitchenPlanningService.Filter.SCHEDULED,null,null,0).total()).isEqualTo(23);
  doReturn(LocalDateTime.of(2026,10,1,18,0)).when(clock).now();
  assertThat(service.get(branch,KitchenPlanningService.Filter.OVERDUE,null,null,0).total()).isEqualTo(24);
 }
 @Test void alarmCountsOnlyActionableOrdersInTheAuthorizedBranch(){
  order("CONFIRMED",branch,slot);order("PREPARING",branch,slot);order("READY_FOR_PICKUP",branch,slot);
  long other=jdbc.queryForObject("INSERT INTO branches(code,name) VALUES (?,'Other alarm') RETURNING id",Long.class,"ALARM-"+UUID.randomUUID());
  order("CONFIRMED",other,slot);
  doReturn(LocalDateTime.of(2026,10,1,16,59)).when(clock).now();
  assertThat(service.alerts(branch)).isEqualTo(new KitchenPlanningService.AlertCounts(0,0));
  doReturn(LocalDateTime.of(2026,10,1,17,0)).when(clock).now();
  assertThat(service.alerts(branch)).isEqualTo(new KitchenPlanningService.AlertCounts(1,0));
  doReturn(LocalDateTime.of(2026,10,1,18,0)).when(clock).now();
  assertThat(service.alerts(branch)).isEqualTo(new KitchenPlanningService.AlertCounts(1,1));
  var overdue=service.get(branch,KitchenPlanningService.Filter.OVERDUE,null,null,0);
  assertThat(overdue.orders()).anyMatch(row->row.orderStatus().equals("PREPARING"));
  jdbc.update("UPDATE orders SET order_status='READY_FOR_PICKUP' WHERE branch_id=?",branch);
  assertThat(service.alerts(branch)).isEqualTo(new KitchenPlanningService.AlertCounts(0,0));
 }
 @Test void permissionFlagAndInputChecksRunBeforeQueries(){
  doThrow(new IllegalStateException("Other branch")).when(staff).requireBranchAccess(999L);
  assertThatThrownBy(()->service.get(999,KitchenPlanningService.Filter.ALL,null,null,0)).hasMessage("Other branch");
  assertThatThrownBy(()->service.get(branch,KitchenPlanningService.Filter.ALL,null,LocalTime.NOON,0)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.alerts(999)).hasMessage("Other branch");
  flags.setAdminPreparationBoard(false);
  assertThatThrownBy(()->service.alerts(branch)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->service.get(branch,KitchenPlanningService.Filter.ALL,null,null,0)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }

 @Test void operationalFiltersKeepOverduePreparationAndReadyPickupInTheirOwnLanes(){
  order("CONFIRMED",branch,slot);order("PREPARING",branch,slot);order("READY_FOR_PICKUP",branch,slot);
  doReturn(LocalDateTime.of(2026,10,1,18,5)).when(clock).now();
  assertThat(service.get(branch,KitchenPlanningService.Filter.WAITING,null,null,0).orders()).extracting(KitchenPlanningService.Row::orderStatus).containsExactly("CONFIRMED");
  assertThat(service.get(branch,KitchenPlanningService.Filter.IN_PROGRESS,null,null,0).orders()).extracting(KitchenPlanningService.Row::orderStatus).containsExactly("PREPARING");
  assertThat(service.get(branch,KitchenPlanningService.Filter.HANDOVER,null,null,0).orders()).extracting(KitchenPlanningService.Row::orderStatus).containsExactly("READY_FOR_PICKUP");
 }
 @Test void counterOnlyOrdersOpenEarlyButMixedBasketsAndFutureDaysStayScheduled(){
  long category=jdbc.queryForObject("INSERT INTO categories(code,name) VALUES (?,'Desk') RETURNING id",Long.class,"DESK-"+UUID.randomUUID());
  long tax=jdbc.queryForObject("INSERT INTO tax_categories(code,name,cgst_rate,sgst_rate) VALUES (?,'Desk',0,0) RETURNING id",Long.class,"DESK-"+UUID.randomUUID());
  long sweet=jdbc.queryForObject("INSERT INTO products(code,name,category_id,tax_category_id,base_price,sale_mode) VALUES (?,'Sweet',?,?,100,'WEIGHT') RETURNING id",Long.class,"DESK-"+UUID.randomUUID(),category,tax);
  long kitchen=jdbc.queryForObject("INSERT INTO products(code,name,category_id,tax_category_id,base_price,sale_mode) VALUES (?,'Samosa',?,?,10,'UNIT') RETURNING id",Long.class,"DESK-"+UUID.randomUUID(),category,tax);
  jdbc.update("INSERT INTO branch_products(branch_id,product_id,early_preparation_allowed) VALUES (?,?,TRUE),(?,?,FALSE)",branch,sweet,branch,kitchen);
  order("CONFIRMED",branch,slot);order("CONFIRMED",branch,slot);
  var ids=jdbc.queryForList("SELECT id FROM orders WHERE branch_id=? ORDER BY id",Long.class,branch);
  for(long id:ids)jdbc.update("INSERT INTO order_items(order_id,product_id,product_name,sale_mode,quantity,weight_grams,unit_price,tax_rate,tax_amount,line_total) VALUES (?,?,'Sweet','WEIGHT',1,2000,100,0,0,200)",id,sweet);
  jdbc.update("INSERT INTO order_items(order_id,product_id,product_name,sale_mode,quantity,unit_price,tax_rate,tax_amount,line_total) VALUES (?,?,'Samosa','UNIT',4,10,0,0,40)",ids.getLast(),kitchen);
  doReturn(LocalDateTime.of(2026,10,1,12,0)).when(clock).now();
  var waiting=service.get(branch,KitchenPlanningService.Filter.WAITING,null,null,0);
  var earlyQueue=queue(LocalDate.of(2026,10,1),LocalTime.NOON,0);
  assertThat(earlyQueue).hasSize(1);assertThat(earlyQueue.getFirst().getId()).isEqualTo(ids.getFirst());
  assertThat(org.hibernate.Hibernate.isInitialized(earlyQueue.getFirst().getBranch())).isTrue();
  assertThat(org.hibernate.Hibernate.isInitialized(earlyQueue.getFirst().getPickupSlot())).isTrue();
  assertThat(queueCount(LocalDate.of(2026,10,1),LocalTime.NOON)).isEqualTo(1);
  // Empty baskets and absent policies must not accidentally qualify for early packing.
  order("CONFIRMED",branch,slot);jdbc.update("DELETE FROM branch_products WHERE branch_id=? AND product_id=?",branch,kitchen);
  assertThat(queueCount(LocalDate.of(2026,10,1),LocalTime.NOON)).isEqualTo(1);
  assertThat(queue(LocalDate.of(2026,9,30),LocalTime.NOON,0)).isEmpty();
  jdbc.update("DELETE FROM orders WHERE branch_id=? AND id NOT IN (?,?)",branch,ids.getFirst(),ids.getLast());
  assertThat(waiting.orders()).hasSize(1);assertThat(waiting.orders().getFirst().earlyPreparation()).isTrue();
  assertThat(waiting.orders().getFirst().items().getFirst().weightGrams()).isEqualTo(2000);
  assertThat(service.get(branch,KitchenPlanningService.Filter.SCHEDULED,null,null,0).orders()).hasSize(1);
  doReturn(LocalDateTime.of(2026,9,30,12,0)).when(clock).now();
  assertThat(service.get(branch,KitchenPlanningService.Filter.SCHEDULED,null,null,0).orders()).hasSize(2);
 }
 @Test void excludesOtherBranchOrdersAndHonoursTimeFilter(){
  long other=jdbc.queryForObject("INSERT INTO branches(code,name) VALUES (?,'Other kitchen') RETURNING id",Long.class,"KITCHEN-"+UUID.randomUUID());
  order("CONFIRMED",branch,slot);order("READY_FOR_PICKUP",other,slot);
  var result=service.get(branch,KitchenPlanningService.Filter.ALL,LocalDate.of(2026,10,1),LocalTime.of(18,0),0);
  assertThat(result.total()).isEqualTo(1);verify(staff).requireBranchAccess(branch);
  assertThat(service.get(branch,KitchenPlanningService.Filter.ALL,LocalDate.of(2026,10,1),LocalTime.of(19,0),0).orders()).isEmpty();
 }
 java.util.List<com.gokulsweets.restaurant.order.entity.Order> queue(LocalDate day,LocalTime cutoff,int page){
  return orders.findPreparationQueueCandidates(branch,com.gokulsweets.restaurant.order.enums.OrderStatus.CONFIRMED,
   com.gokulsweets.restaurant.order.enums.PickupType.NORMAL,day,cutoff,
   com.gokulsweets.restaurant.order.enums.PickupType.PRIORITY,day,cutoff,
   com.gokulsweets.restaurant.order.enums.PickupType.ADMIN_OVERRIDE,day,cutoff,day,
   org.springframework.data.domain.PageRequest.of(page,20));
 }
 long queueCount(LocalDate day,LocalTime cutoff){
  return orders.countPreparationQueueCandidates(branch,com.gokulsweets.restaurant.order.enums.OrderStatus.CONFIRMED,
   com.gokulsweets.restaurant.order.enums.PickupType.NORMAL,day,cutoff,
   com.gokulsweets.restaurant.order.enums.PickupType.PRIORITY,day,cutoff,
   com.gokulsweets.restaurant.order.enums.PickupType.ADMIN_OVERRIDE,day,cutoff,day);
 }

}
