package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class MenuCatalogIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean MenuCatalogService catalog;
    @Autowired MenuAvailabilityService live;
    @Autowired MenuService menu;
    @Autowired MenuServiceWindows windows;
    @Autowired MenuPickupDiscoveryService discovery;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean(name="inventoryClock") java.time.Clock clock;
    @MockitoBean StaffAuthorizationService authorization;
    long branch,category,product,bp;
    @BeforeEach void setup(){
        String code="CAT-"+UUID.randomUUID();
        branch=jdbc.queryForObject("INSERT INTO branches(code,name) VALUES(?,'Catalog test') RETURNING id",Long.class,code);
        category=jdbc.queryForObject("INSERT INTO categories(code,name) VALUES(?,'Sweets') RETURNING id",Long.class,code);
        product=jdbc.queryForObject("INSERT INTO products(code,name,category_id,base_price,sale_mode,minimum_weight_grams,weight_step_grams) VALUES(?,'Sweet',?,400,'WEIGHT',250,250) RETURNING id",Long.class,code,category);
        bp=jdbc.queryForObject("INSERT INTO branch_products(branch_id,product_id) VALUES(?,?) RETURNING id",Long.class,branch,product);
    }
    @Test void dietaryChangesInvalidateCatalogAndReachCombinedMenu(){
        var before=catalog.get(branch);assertThat(before.categories().getFirst().products().getFirst().vegetarian()).isTrue();
        jdbc.update("UPDATE products SET vegetarian=false WHERE id=?",product);
        var after=catalog.get(branch);assertThat(after.revision()).isNotEqualTo(before.revision());
        assertThat(after.categories().getFirst().products().getFirst().vegetarian()).isFalse();
        assertThat(before.categories().getFirst().products().getFirst().vegetarian()).isTrue();
        windows.save(branch,new MenuServiceWindows.Settings(true,0,List.of()));
        assertThat(menu.getMenu(branch).getFirst().products().getFirst().vegetarian()).isFalse();
    }
    @Test void immutableSnapshotIsReusedAndDirectSqlEditsInvalidateItAcrossBranches(){
        var first=catalog.get(branch);assertThat(catalog.get(branch)).isSameAs(first);
        jdbc.update("UPDATE products SET base_price=500 WHERE id=?",product);
        var next=catalog.get(branch);assertThat(next.revision()).isNotEqualTo(first.revision());
        assertThat(next.categories().getFirst().products().getFirst().price()).isEqualByComparingTo("500");
        assertThat(first.categories().getFirst().products().getFirst().price()).isEqualByComparingTo("400");
        jdbc.update("UPDATE categories SET active=false WHERE id=?",category);assertThat(catalog.get(branch).categories()).isEmpty();
    }
    @Test void editCommittedBetweenBuildAndRevisionCheckIsRebuiltWithFreshValues() throws Exception {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        try(var executor=Executors.newSingleThreadExecutor()) {
            org.mockito.Mockito.doAnswer(invocation->{
                if(calls.incrementAndGet()==2)executor.submit(()->{
                    jdbc.update("UPDATE products SET base_price=550,name='Edited sweet' WHERE id=?",product);
                    jdbc.update("UPDATE branch_products SET available=false WHERE id=?",bp);
                }).get(5,TimeUnit.SECONDS);
                return invocation.callRealMethod();
            }).when(catalog).revision();
            var result=catalog.get(branch);
            var item=result.categories().getFirst().products().getFirst();
            assertThat(item.price()).isEqualByComparingTo("550");
            assertThat(item.name()).isEqualTo("Edited sweet");
            assertThat(item.available()).isFalse();
            assertThat(result.revision()).isEqualTo(catalog.revision());
            assertThat(catalog.get(branch)).isSameAs(result);
        } finally { org.mockito.Mockito.doCallRealMethod().when(catalog).revision(); }
    }
    @Test void soldOutAndClosureNeverReuseAnOldAvailableDecision(){
        catalog.get(branch);jdbc.update("UPDATE branch_products SET available=false WHERE id=?",bp);
        assertThat(live.get(branch).items().getFirst().available()).isFalse();assertThat(menu.getMenu(branch)).isEmpty();
        windows.save(branch,new MenuServiceWindows.Settings(true,0,List.of()));
        assertThat(menu.getMenu(branch).getFirst().products().getFirst().available()).isFalse();
        jdbc.update("UPDATE branches SET operational=false WHERE id=?",branch);
        assertThatThrownBy(()->catalog.get(branch)).hasMessageContaining("not operational");
        assertThatThrownBy(()->live.get(branch)).hasMessageContaining("not operational");
    }
    @Test void rolledBackChangesNeverEscapeIntoTheSharedCache(){
        var before=catalog.get(branch);var transaction=new TransactionTemplate(manager);
        transaction.executeWithoutResult(status->{jdbc.update("UPDATE products SET base_price=999 WHERE id=?",product);assertThat(catalog.get(branch).categories().getFirst().products().getFirst().price()).isEqualByComparingTo("999");status.setRollbackOnly();});
        assertThat(catalog.get(branch)).isSameAs(before);
    }
    @Test void concurrentColdReadersShareOnePublishedSnapshot()throws Exception {
        try(var executor=Executors.newFixedThreadPool(10)) {
            var tasks=new ArrayList<Callable<MenuCatalogService.Catalog>>();for(int i=0;i<20;i++)tasks.add(()->catalog.get(branch));
            var responses=executor.invokeAll(tasks);var first=responses.getFirst().get();for(var result:responses)assertThat(result.get()).isSameAs(first);
        }
    }
    @Test void browseAvailabilityUsesShortTtlAndRejectsClockReversalWithoutBlockingAdvanceOrders(){
        windows.save(branch,new MenuServiceWindows.Settings(true,0,List.of(new MenuServiceWindows.Item(bp,java.time.LocalTime.of(10,0),java.time.LocalTime.of(17,0),127,false,null))));
        org.mockito.Mockito.doReturn(java.time.Instant.parse("2026-10-06T04:29:59.500Z")).when(clock).instant();
        var before=live.get(branch);assertThat(before.items().getFirst().available()).isTrue();
        org.mockito.Mockito.doReturn(java.time.Instant.parse("2026-10-06T04:29:59.750Z")).when(clock).instant();var reused=live.get(branch);assertThat(reused.items()).isSameAs(before.items());assertThat(reused.observedAt()).isEqualTo(clock.instant());
        org.mockito.Mockito.doReturn(java.time.Instant.parse("2026-10-06T04:30:00.600Z")).when(clock).instant();
        var expired=live.get(branch);assertThat(expired.items()).isNotSameAs(before.items());assertThat(expired.items().getFirst().available()).isTrue();
        org.mockito.Mockito.doReturn(java.time.Instant.parse("2026-10-06T04:29:59.900Z")).when(clock).instant();
        var reversed=live.get(branch);assertThat(reversed.items()).isNotSameAs(expired.items());assertThat(reversed.items().getFirst().available()).isTrue();
    }
    @Test void dateDiscoveryDoesNotNeedAnyProductInventory(){
        var tomorrow=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(1);
        jdbc.update("INSERT INTO pickup_slots(branch_id,slot_date,start_time,end_time,capacity) VALUES(?,?,'15:00','16:00',10)",branch,tomorrow);
        var result=discovery.discover(branch,tomorrow,1);
        assertThat(result.dates().getFirst().available()).isTrue();assertThat(result.dates().getFirst().items()).isEmpty();
        assertThat(result.dates().getFirst().slots().getFirst().issues()).isEmpty();
    }
}
