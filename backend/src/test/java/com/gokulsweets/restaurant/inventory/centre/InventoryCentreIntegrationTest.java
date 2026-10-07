package com.gokulsweets.restaurant.inventory.centre;

import com.gokulsweets.restaurant.inventory.centre.InventoryCentreJobs.*;
import com.gokulsweets.restaurant.menu.MobileMenuOptionsService;
import com.gokulsweets.restaurant.menu.workspace.MenuWorkspaceService;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import com.gokulsweets.restaurant.security.*;
import com.gokulsweets.restaurant.staff.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL transactions exercise durable queue recovery and atomic item writes. */
@SpringBootTest(properties={"inventory.centre.poll-ms=3600000","inventory.centre.cleanup-ms=3600000"})
class InventoryCentreIntegrationTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired InventoryCentreJobs jobs;
 @Autowired InventoryCentreWorker worker;
 @Autowired MenuWorkspaceService workspace;
 @Autowired MobileMenuOptionsService groups;
 @Autowired StaffUserRepository staff;
 @Autowired Clock inventoryClock;
 @Autowired com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository policies;
 @Autowired com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository allocations;
 @Autowired org.springframework.transaction.PlatformTransactionManager manager;
 @MockitoBean StaffAuthorizationService authorization;
 @MockitoBean StaffUserDetailsService users;
 long branch,category,actor;String username;LocalDate today;
 @BeforeEach void setup(){
  username="centre-"+UUID.randomUUID();today=LocalDate.now(inventoryClock);
  branch=jdbc.queryForObject("INSERT INTO branches(code,name) VALUES(?,'Centre') RETURNING id",Long.class,username);
  category=jdbc.queryForObject("INSERT INTO categories(code,name) VALUES(?,'Sweets') RETURNING id",Long.class,username);
  actor=jdbc.queryForObject("INSERT INTO staff_users(username,password_hash,full_name,role_id) SELECT ?,'disabled','Centre',id FROM roles WHERE name='OWNER_ADMIN' RETURNING id",Long.class,username);
  when(authorization.getCurrentStaff()).thenReturn(staff.findDetailedById(actor).orElseThrow());
  when(users.loadUserByUsername(username)).thenReturn(User.withUsername(username).password("disabled").authorities("MENU_MANAGE","INVENTORY_MANAGE","INVENTORY_VIEW").build());
 }
 @AfterEach void cleanup(){jdbc.update("DELETE FROM inventory_centre_jobs WHERE staff_id=?",actor);}
 long product(){return workspace.create(branch,new MenuWorkspaceService.Details("Variant",UUID.randomUUID().toString(),category,"Fresh",BigDecimal.TEN,ProductSaleMode.UNIT,null,null,null,0L),List.of(branch));}
 long bp(long product){return jdbc.queryForObject("SELECT id FROM branch_products WHERE branch_id=? AND product_id=?",Long.class,branch,product);}
 Entry entry(long product,LocalDate date,String quantity,String ready){
  var r=jdbc.queryForMap("SELECT bp.workspace_version,p.version AS policy_version,a.version AS allocation_version FROM branch_products bp LEFT JOIN branch_inventory_policies p ON p.branch_product_id=bp.id LEFT JOIN inventory_daily_allocations a ON a.branch_product_id=bp.id AND a.service_date=? WHERE bp.branch_id=? AND bp.product_id=?",date,branch,product);
  return new Entry(product,((Number)r.get("workspace_version")).longValue(),number(r.get("policy_version")),number(r.get("allocation_version")),jdbc.queryForObject("SELECT sale_mode FROM products WHERE id=?",String.class,product),quantity==null?null:new BigDecimal(quantity),ready==null?null:new BigDecimal(ready));
 }
 Long number(Object v){return v==null?null:((Number)v).longValue();}
 Options options(LocalDate from,LocalDate through,String method,boolean inventory,boolean hours){return new Options(from,through,method,inventory,false,false,hours,LocalTime.of(11,0),LocalTime.of(21,30),127,true,"Reviewed physical quantities");}
 UUID enqueue(Options o,Entry... entries){UUID id=UUID.randomUUID();jobs.submit(branch,new Submit(id,o,List.of(entries)));return id;}
 @Test void groupedVariantsKeepSeparateStockAndHoursPreserveManualOverridesAndDependencies(){
  long a=product(),b=product();groups.saveOne(branch,0,new MobileMenuOptionsService.Group("sizes","Sizes",List.of(new MobileMenuOptionsService.Choice(a,"Small"),new MobileMenuOptionsService.Choice(b,"Large"))),false);
  jdbc.update("INSERT INTO menu_service_items(branch_product_id,starts_at,ends_at,weekdays,sold_out,requires_branch_product_id) VALUES (?, '08:00','09:00',127,true,?)",bp(a),bp(b));
  UUID id=enqueue(options(today,today,"READY_STOCK",true,true),entry(a,today,"10","8"),entry(b,today,"20","15"));worker.runBatch();
  assertThat(jobs.summary(branch,id).get("succeeded")).isEqualTo(2);
  assertThat(jdbc.queryForObject("SELECT ready_quantity FROM inventory_daily_allocations WHERE branch_product_id=?",BigDecimal.class,bp(a))).isEqualByComparingTo("8");
  assertThat(jdbc.queryForObject("SELECT ready_quantity FROM inventory_daily_allocations WHERE branch_product_id=?",BigDecimal.class,bp(b))).isEqualByComparingTo("15");
  assertThat(jdbc.queryForObject("SELECT available FROM branch_products WHERE id=?",Boolean.class,bp(a))).isFalse();
  assertThat(jobs.timing(bp(a))).isEqualTo(new Timing(LocalTime.of(11,0),LocalTime.of(21,30),127,true,bp(b)));
  assertThat(groups.adminRead(branch).groups()).hasSize(1);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_workspace_audit WHERE branch_id=? AND action='CENTRE_SERVICE_HOURS'",Integer.class,branch)).isEqualTo(2);
 }
 @Test void hoursOnlyDoesNotCreatePoliciesAllocationsOrReadyStock(){
  long p=product();UUID id=enqueue(options(today,today,"READY_STOCK",false,true),entry(p,today,null,null));worker.runBatch();
  assertThat(jobs.summary(branch,id).get("succeeded")).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM branch_inventory_policies WHERE branch_product_id=?",Integer.class,bp(p))).isZero();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_daily_allocations WHERE branch_product_id=?",Integer.class,bp(p))).isZero();
 }
 @Test void futurePreparedAllocationsDoNotFabricateReadinessAndSubmissionRetriesDeduplicate(){
  long p=product();var from=today.plusDays(1);var o=options(from,from.plusDays(2),"READY_STOCK",true,false);
  var input=new Submit(UUID.randomUUID(),o,List.of(entry(p,from,"10",null)));jobs.submit(branch,input);worker.runBatch();
  assertThat(jobs.submit(branch,input).get("succeeded")).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_daily_allocations WHERE branch_product_id=? AND ready_quantity=0 AND status<>'READY'",Integer.class,bp(p))).isEqualTo(3);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_centre_jobs WHERE staff_id=?",Integer.class,actor)).isEqualTo(1);
  assertThatThrownBy(()->enqueue(o,entry(p,from,"10","8"))).hasMessageContaining("Future stock");
 }
 @Test void failureOnLaterDateRollsBackAllDatesForThatItem(){
  long p=product();var from=today.plusDays(1);var o=options(from,from.plusDays(2),"DAILY_PRODUCTION",true,false);
  enqueue(o,entry(p,from,"10",null));worker.runBatch();
  jdbc.update("UPDATE inventory_daily_allocations SET status='CLOSED' WHERE branch_product_id=? AND service_date=?",bp(p),from.plusDays(1));
  UUID failed=enqueue(o,entry(p,from,"20",null));worker.runBatch();
  assertThat(jobs.summary(branch,failed).get("failed")).isEqualTo(1);
  assertThat(jdbc.queryForList("SELECT approved_quantity FROM inventory_daily_allocations WHERE branch_product_id=?",BigDecimal.class,bp(p))).allSatisfy(q->assertThat(q).isEqualByComparingTo("10"));
  assertThat(jobs.results(branch,failed,0).getFirst().get("error")).asString().contains("paused or closed");
 }
 @Test void expiredLeaseRecoversAndConcurrentWorkersDoNotDuplicateCommittedWork()throws Exception{
  long p=product();UUID id=enqueue(options(today,today,"READY_STOCK",true,false),entry(p,today,"10","8"));
  jdbc.update("UPDATE inventory_centre_tasks SET status='PROCESSING',attempts=1,lease_until=now()-interval '1 minute' WHERE job_id=?",id);
  try(var pool=Executors.newFixedThreadPool(2)){var one=pool.submit(worker::runBatch);var two=pool.submit(worker::runBatch);one.get(20,TimeUnit.SECONDS);two.get(20,TimeUnit.SECONDS);}
  assertThat(jobs.summary(branch,id).get("succeeded")).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_daily_allocations WHERE branch_product_id=?",Integer.class,bp(p))).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_workspace_audit WHERE branch_id=? AND action='INVENTORY_ADJUSTMENT'",Integer.class,branch)).isEqualTo(1);
 }
 @Test void crossingIstMidnightRollsBackInsteadOfReusingTodayStockForTomorrow(){
  long p=product();var o=options(today,today.plusDays(1),"READY_STOCK",true,false);
  var clock=mock(Clock.class);when(clock.getZone()).thenReturn(ZoneId.of("Asia/Kolkata"));
  when(clock.instant()).thenReturn(today.atTime(23,59,59).atZone(clock.getZone()).toInstant(),today.plusDays(1).atStartOfDay(clock.getZone()).toInstant());
  var timed=new InventoryCentreProcessor(jdbc,jobs,workspace,policies,allocations,clock);
  var work=new Work(o,entry(p,today,"10","8"),List.of(new DateVersion(today,null),new DateVersion(today.plusDays(1),null)),0,null);
  var tx=new org.springframework.transaction.support.TransactionTemplate(manager);
  assertThatThrownBy(()->tx.executeWithoutResult(status->timed.apply(branch,work))).hasMessageContaining("crossed midnight");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_daily_allocations WHERE branch_product_id=?",Integer.class,bp(p))).isZero();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM branch_inventory_policies WHERE branch_product_id=?",Integer.class,bp(p))).isZero();
 }
 @Test void changedSellingUnitsCannotReinterpretQueuedQuantities(){
  long p=product();UUID id=enqueue(options(today,today,"READY_STOCK",true,false),entry(p,today,"10","8"));
  jdbc.update("UPDATE products SET sale_mode='WEIGHT',minimum_weight_grams=250,weight_step_grams=250 WHERE id=?",p);worker.runBatch();
  assertThat(jobs.summary(branch,id).get("failed")).isEqualTo(1);
  assertThat(jobs.results(branch,id,0).getFirst().get("error")).asString().contains("selling unit changed");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_daily_allocations WHERE branch_product_id=?",Integer.class,bp(p))).isZero();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM branch_inventory_policies WHERE branch_product_id=?",Integer.class,bp(p))).isZero();
 }
 @Test void currentPermissionRevocationStopsQueuedStockWrites(){
  long p=product();UUID id=enqueue(options(today,today,"READY_STOCK",true,false),entry(p,today,"10","8"));
  when(users.loadUserByUsername(username)).thenReturn(User.withUsername(username).password("disabled").authorities("MENU_MANAGE").build());worker.runBatch();
  assertThat(jobs.summary(branch,id).get("failed")).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_daily_allocations WHERE branch_product_id=?",Integer.class,bp(p))).isZero();
 }
 @Test void boundedQueueCleanupRemovesOnlyUnusedWorkAndKeepsInventoryAndAuditHistory(){
  long p=product();UUID id=enqueue(options(today,today,"READY_STOCK",true,false),entry(p,today,"10","8"));worker.runBatch();
  jdbc.update("UPDATE inventory_centre_tasks SET finished_at=now()-interval '31 days' WHERE job_id=?",id);worker.cleanup();
  assertThat(jobs.results(branch,id,0)).isEmpty();assertThat(jobs.summary(branch,id).get("succeeded")).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_daily_allocations WHERE branch_product_id=?",Integer.class,bp(p))).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_workspace_audit WHERE branch_id=? AND action='INVENTORY_ADJUSTMENT'",Integer.class,branch)).isEqualTo(1);
 }
 @Test void tenThousandDatePlansAreBoundedAndWorkerProcessesOnlyFiveProductsPerBatch(){
  var from=today.plusDays(1);var o=options(from,from.plusDays(49),"DAILY_PRODUCTION",true,false);
  var rows=new ArrayList<Entry>();for(int i=0;i<200;i++){long p=product();rows.add(entry(p,from,"10",null));}
  UUID id=UUID.randomUUID();jobs.submit(branch,new Submit(id,o,rows));
  assertThat(jdbc.queryForObject("SELECT sum(jsonb_array_length(payload::jsonb->'dates')) FROM inventory_centre_tasks WHERE job_id=?",Long.class,id)).isEqualTo(10000L);
  worker.runBatch();assertThat(jobs.summary(branch,id).get("succeeded")).isEqualTo(5);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_daily_allocations a JOIN branch_products bp ON bp.id=a.branch_product_id WHERE bp.branch_id=?",Integer.class,branch)).isEqualTo(250);
  rows.add(rows.getFirst());assertThatThrownBy(()->jobs.submit(branch,new Submit(UUID.randomUUID(),o,rows))).hasMessageContaining("10,000");
 }
 @Test void admissionLimitsRejectOversizePlansAndActiveDuplicateBranchJobs(){
  long p=product();var o=options(today,today,"DAILY_PRODUCTION",true,false);enqueue(o,entry(p,today,"10",null));
  assertThatThrownBy(()->enqueue(o,entry(p,today,"10",null))).hasMessageContaining("active inventory job");
  assertThatThrownBy(()->enqueue(options(today,today.plusDays(61),"DAILY_PRODUCTION",true,false),entry(p,today,"10",null))).hasMessageContaining("10,000");
 }
}
