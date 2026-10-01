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
  order("PREPARING",branch,slot);order("READY",branch,slot);order("CANCELLED",branch,slot);order("PENDING_PAYMENT",branch,slot);
  var first=service.get(branch,KitchenPlanningService.Filter.ELIGIBLE,LocalDate.of(2026,10,1),null,0);
  assertThat(first.total()).isEqualTo(23);assertThat(first.orders()).hasSize(20);assertThat(first.slots()).hasSize(1);
  assertThat(first.slots().getFirst().total()).isEqualTo(25);assertThat(first.counts().get("PREPARING")).isEqualTo(1);
  assertThat(service.get(branch,KitchenPlanningService.Filter.ELIGIBLE,null,null,1).orders()).hasSize(3);
  doReturn(LocalDateTime.of(2026,10,1,16,59)).when(clock).now();
  assertThat(service.get(branch,KitchenPlanningService.Filter.SCHEDULED,null,null,0).total()).isEqualTo(23);
  doReturn(LocalDateTime.of(2026,10,1,18,0)).when(clock).now();
  assertThat(service.get(branch,KitchenPlanningService.Filter.OVERDUE,null,null,0).total()).isEqualTo(23);
 }
 @Test void permissionFlagAndInputChecksRunBeforeQueries(){
  doThrow(new IllegalStateException("Other branch")).when(staff).requireBranchAccess(999L);
  assertThatThrownBy(()->service.get(999,KitchenPlanningService.Filter.ALL,null,null,0)).hasMessage("Other branch");
  assertThatThrownBy(()->service.get(branch,KitchenPlanningService.Filter.ALL,null,LocalTime.NOON,0)).isInstanceOf(IllegalArgumentException.class);
  flags.setAdminPreparationBoard(false);
  assertThatThrownBy(()->service.get(branch,KitchenPlanningService.Filter.ALL,null,null,0)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void excludesOtherBranchOrdersAndHonoursTimeFilter(){
  long other=jdbc.queryForObject("INSERT INTO branches(code,name) VALUES (?,'Other kitchen') RETURNING id",Long.class,"KITCHEN-"+UUID.randomUUID());
  order("CONFIRMED",branch,slot);order("READY",other,slot);
  var result=service.get(branch,KitchenPlanningService.Filter.ALL,LocalDate.of(2026,10,1),LocalTime.of(18,0),0);
  assertThat(result.total()).isEqualTo(1);verify(staff).requireBranchAccess(branch);
  assertThat(service.get(branch,KitchenPlanningService.Filter.ALL,LocalDate.of(2026,10,1),LocalTime.of(19,0),0).orders()).isEmpty();
 }
}
