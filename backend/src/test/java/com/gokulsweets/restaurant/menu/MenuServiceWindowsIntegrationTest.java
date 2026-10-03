package com.gokulsweets.restaurant.menu;
import com.gokulsweets.restaurant.branch.BranchOperations;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.service.OrderValidationService;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest
class MenuServiceWindowsIntegrationTest {
 @Autowired MenuServiceWindows windows;
 @Autowired BranchOperations operations;
 @Autowired MenuService menu;
 @Autowired OrderValidationService validation;
 @Autowired JdbcTemplate jdbc;
 @Autowired PlatformTransactionManager manager;
 @MockitoBean StaffAuthorizationService authorization;
 @MockitoBean(name="inventoryClock") Clock clock;
 long branch,category,samosa,chola,samosaBp,cholaBp;
 @BeforeEach void setup(){
  time("2026-10-05T05:30:00Z");String code="SERVICE-"+UUID.randomUUID();
  branch=jdbc.queryForObject("INSERT INTO branches(code,name) VALUES (?,'Service test') RETURNING id",Long.class,code);
  category=jdbc.queryForObject("INSERT INTO categories(code,name) VALUES (?,'Snacks') RETURNING id",Long.class,code);
  samosa=product(code+"-S","Samosa");chola=product(code+"-C","Chola samosa");
  samosaBp=bp(samosa);cholaBp=bp(chola);
 }
 void time(String instant){when(clock.instant()).thenReturn(Instant.parse(instant));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Kolkata"));}
 long product(String code,String name){return jdbc.queryForObject("INSERT INTO products(code,name,category_id,sale_mode,base_price) VALUES (?, ?, ?, 'UNIT',20) RETURNING id",Long.class,code,name,category);}
 long bp(long product){return jdbc.queryForObject("INSERT INTO branch_products(branch_id,product_id) VALUES (?,?) RETURNING id",Long.class,branch,product);}
 MenuServiceWindows.Item rule(long id,boolean sold,Long dependency){return new MenuServiceWindows.Item(id,LocalTime.of(10,0),LocalTime.of(17,0),127,sold,dependency);}
 MenuServiceWindows.Settings save(List<MenuServiceWindows.Item> items){return windows.save(branch,new MenuServiceWindows.Settings(true,windows.settings(branch).revision(),items));}
 @Test void clockGuardsCustomerMenuAndDirectCartValidation(){
  save(List.of(rule(samosaBp,false,null)));
  time("2026-10-05T04:29:59Z");
  assertThat(menu.getMenu(branch).getFirst().products().stream().filter(p->p.id()==samosa).findFirst().orElseThrow().available()).isFalse();
  assertThatThrownBy(()->validation.validateCart(branch,List.of(new CreateOrderItemRequest(samosa,1,null)))).hasMessageContaining("available from");
  time("2026-10-05T04:30:00Z");assertThat(validation.validateCart(branch,List.of(new CreateOrderItemRequest(samosa,1,null)))).hasSize(1);
  time("2026-10-05T11:30:00Z");assertThat(windows.snapshot(branch).status(samosa).available()).isFalse();
 }
 @Test void soldOutIngredientBlocksAndRestoresDependentDish(){
  save(List.of(rule(samosaBp,true,null),rule(cholaBp,false,samosaBp)));
  assertThat(windows.snapshot(branch).status(chola).code()).isEqualTo("SOLD_OUT");
  time("2026-10-05T03:00:00Z");
  assertThat(windows.snapshot(branch).status(chola).code()).isEqualTo("SOLD_OUT");
  time("2026-10-05T05:30:00Z");
  save(List.of(rule(samosaBp,false,null),rule(cholaBp,false,samosaBp)));
  assertThat(windows.snapshot(branch).status(chola).available()).isTrue();
 }
 @Test void rejectsCyclesCrossBranchItemsAndLostUpdates(){
  assertThatThrownBy(()->save(List.of(rule(samosaBp,false,cholaBp),rule(cholaBp,false,samosaBp)))).hasMessageContaining("loop");
  assertThatThrownBy(()->save(List.of(rule(samosaBp,false,Long.MAX_VALUE)))).hasMessageContaining("branch items");
  var stale=new MenuServiceWindows.Settings(true,0,List.of(rule(samosaBp,false,null)));windows.save(branch,stale);
  assertThatThrownBy(()->windows.save(branch,stale)).hasMessageContaining("409");
 }
 @Test void offKeepsOriginalMenuAndReadOnlyQuoteDoesNotTakeWriteLocks(){
  jdbc.update("UPDATE branch_products SET available=false WHERE id=?",samosaBp);
  assertThat(menu.getMenu(branch).getFirst().products()).noneMatch(p->p.id()==samosa);
  save(List.of(rule(samosaBp,false,null)));
  var transaction=new TransactionTemplate(manager);transaction.setReadOnly(true);
  String code=transaction.execute(status->{jdbc.execute("SET TRANSACTION READ ONLY");return windows.snapshot(branch).status(samosa).code();});assertThat(code).isEqualTo("SOLD_OUT");
 }
 @Test void unauthorizedBranchCannotReadOrChangeRulesOrOperations(){
  doThrow(new org.springframework.security.access.AccessDeniedException("Other branch")).when(authorization).requireBranchAccess(branch);
  assertThatThrownBy(()->windows.settings(branch)).hasMessageContaining("Other branch");
  assertThatThrownBy(()->windows.save(branch,new MenuServiceWindows.Settings(true,0,List.of()))).hasMessageContaining("Other branch");
  assertThatThrownBy(()->operations.set(branch,new BranchOperations.Status(false))).hasMessageContaining("Other branch");
  assertThat(jdbc.queryForObject("SELECT operational FROM branches WHERE id=?",Boolean.class,branch)).isTrue();
 }
 @Test void unchangedOwnedReservationSurvivesClosureButItemChangesAreBlocked(){
  var selected=new com.gokulsweets.restaurant.branch.Branch();selected.setId(branch);selected.setActive(true);
  var slot=new com.gokulsweets.restaurant.pickup.PickupSlot();slot.setId(123L);slot.setBranch(selected);slot.setActive(true);slot.setSlotDate(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));slot.setEndTime(LocalTime.of(18,0));
  var product=new com.gokulsweets.restaurant.product.Product();product.setId(samosa);
  var item=new com.gokulsweets.restaurant.order.entity.OrderItem();item.setProduct(product);item.setQuantity(1);
  var order=new com.gokulsweets.restaurant.order.entity.Order();order.setBranch(selected);order.setPickupSlot(slot);order.setPickupType(com.gokulsweets.restaurant.order.enums.PickupType.NORMAL);order.setItems(new ArrayList<>(List.of(item)));
  operations.set(branch,new BranchOperations.Status(false));
  assertThat(validation.validateExistingReservationUpdate(order,slot.getId(),order.getPickupType(),List.of(new CreateOrderItemRequest(samosa,1,null))).items()).hasSize(1);
  assertThatThrownBy(()->validation.validateExistingReservationUpdate(order,slot.getId(),order.getPickupType(),List.of(new CreateOrderItemRequest(samosa,2,null)))).hasMessageContaining("not operational");
 }
 @Test void closureBlocksNewOrdersAndAcceptanceLocksPolicyChanges() throws Exception {
  save(List.of(rule(samosaBp,false,null)));var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
   var accepting=executor.submit(()->new TransactionTemplate(manager).execute(status->{windows.snapshot(branch).requireAvailable(samosa);locked.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Lock test timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}return null;}));
   assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
   var closing=executor.submit(()->{started.countDown();return operations.set(branch,new BranchOperations.Status(false));});
   assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();try{assertThatThrownBy(()->closing.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{release.countDown();}
   accepting.get(5,TimeUnit.SECONDS);assertThat(closing.get(5,TimeUnit.SECONDS).operational()).isFalse();
  }
  assertThatThrownBy(()->validation.validateCart(branch,List.of(new CreateOrderItemRequest(samosa,1,null)))).hasMessageContaining("not operational");
  operations.set(branch,new BranchOperations.Status(true));assertThat(windows.snapshot(branch).status(samosa).available()).isTrue();
 }
}
