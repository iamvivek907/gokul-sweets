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
    @Autowired MenuCatalogService catalog;
    @Autowired MenuAvailabilityService live;
    @Autowired MenuService menu;
    @Autowired MenuServiceWindows windows;
    @Autowired MenuPickupDiscoveryService discovery;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean StaffAuthorizationService authorization;
    long branch,category,product,bp;
    @BeforeEach void setup(){
        String code="CAT-"+UUID.randomUUID();
        branch=jdbc.queryForObject("INSERT INTO branches(code,name) VALUES(?,'Catalog test') RETURNING id",Long.class,code);
        category=jdbc.queryForObject("INSERT INTO categories(code,name) VALUES(?,'Sweets') RETURNING id",Long.class,code);
        product=jdbc.queryForObject("INSERT INTO products(code,name,category_id,base_price,sale_mode,minimum_weight_grams,weight_step_grams) VALUES(?,'Sweet',?,400,'WEIGHT',250,250) RETURNING id",Long.class,code,category);
        bp=jdbc.queryForObject("INSERT INTO branch_products(branch_id,product_id) VALUES(?,?) RETURNING id",Long.class,branch,product);
    }
    @Test void immutableSnapshotIsReusedAndDirectSqlEditsInvalidateItAcrossBranches(){
        var first=catalog.get(branch);assertThat(catalog.get(branch)).isSameAs(first);
        jdbc.update("UPDATE products SET base_price=500 WHERE id=?",product);
        var next=catalog.get(branch);assertThat(next.revision()).isGreaterThan(first.revision());
        assertThat(next.categories().getFirst().products().getFirst().price()).isEqualByComparingTo("500");
        assertThat(first.categories().getFirst().products().getFirst().price()).isEqualByComparingTo("400");
        jdbc.update("UPDATE categories SET active=false WHERE id=?",category);assertThat(catalog.get(branch).categories()).isEmpty();
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
    @Test void dateDiscoveryDoesNotNeedAnyProductInventory(){
        var tomorrow=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(1);
        jdbc.update("INSERT INTO pickup_slots(branch_id,slot_date,start_time,end_time,capacity) VALUES(?,?,'15:00','16:00',10)",branch,tomorrow);
        var result=discovery.discover(branch,tomorrow,1);
        assertThat(result.dates().getFirst().available()).isTrue();assertThat(result.dates().getFirst().items()).isEmpty();
        assertThat(result.dates().getFirst().slots().getFirst().issues()).isEmpty();
    }
}
