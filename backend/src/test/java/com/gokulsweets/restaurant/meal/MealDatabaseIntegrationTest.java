package com.gokulsweets.restaurant.meal;

import static com.gokulsweets.restaurant.meal.MealPolicyTest.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import java.nio.file.*;
import java.util.*;

@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql:.*")
class MealDatabaseIntegrationTest {
    JdbcTemplate jdbc, root;
    String schema;
    TransactionTemplate tx;
    MealService meals;
    StaffAuthorizationService authorization;

    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("DATABASE_URL"),
                user = System.getenv("DATABASE_USERNAME"),
                password = System.getenv("DATABASE_PASSWORD");
        root = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "meal_test_" + UUID.randomUUID().toString().replace("-", "");
        root.execute("CREATE SCHEMA " + schema);
        var source =
                new DriverManagerDataSource(
                        url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                        user,
                        password);
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.execute("CREATE TABLE branches(id bigint PRIMARY KEY,active boolean)");
        jdbc.execute(
                "CREATE TABLE products(id bigint PRIMARY KEY,name text,sale_mode text,base_price"
                        + " numeric,minimum_weight_grams integer,weight_step_grams integer,active"
                        + " boolean)");
        jdbc.execute(
                "CREATE TABLE branch_products(branch_id bigint,product_id bigint,price_override"
                        + " numeric,available boolean)");
        jdbc.execute("CREATE TABLE order_items(id bigint,product_name varchar(200))");
        jdbc.execute("INSERT INTO branches VALUES(1,true),(2,true)");
        jdbc.execute(
                "INSERT INTO products"
                    + " VALUES(1,'Main','UNIT',100,250,50,true),(2,'Side','UNIT',50,250,50,true),(3,'Sweet','WEIGHT',200,250,50,true)");
        jdbc.execute(
                "INSERT INTO branch_products"
                        + " VALUES(1,1,null,true),(1,2,null,true),(1,3,null,true)");
        jdbc.execute(
                Files.readString(
                        Path.of("src/main/resources/db/migration/V144__app_exclusive_meals.sql")));
        authorization = mock(StaffAuthorizationService.class);
        meals = new MealService(jdbc, JsonMapper.builder().build(), authorization);
    }

    @AfterEach
    void cleanup() {
        if (root != null && schema != null) root.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    MealModels.Edit edit(long version, boolean active) {
        var m = meal();
        return new MealModels.Edit(
                version,
                m.title(),
                m.titleHi(),
                m.description(),
                m.imageUrl(),
                m.basePrice(),
                m.discountType(),
                m.discountValue(),
                m.estimatedCost(),
                active,
                m.groups());
    }

    @Test
    void adminDiscountsAreVersionedAndCostsNeverLeakToThePublic() {
        var saved = tx.execute(s -> meals.save(1, 0, edit(0, true)));
        assertThat(saved.estimatedCost()).isEqualByComparingTo("75.00");
        assertThat(meals.catalog(1, false).getFirst().estimatedCost()).isNull();
        assertThat(meals.catalog(2, false)).isEmpty();
        var q =
                meals.quote(
                        1,
                        new MealModels.Claim(
                                "one", saved.id(), saved.version(), 1, List.of("main1", "side1")));
        assertThat(q.total()).isEqualByComparingTo("120.00");
        var updated = tx.execute(s -> meals.save(1, saved.id(), edit(saved.version(), false)));
        assertThat(updated.version()).isEqualTo(saved.version() + 1);
        assertThat(meals.catalog(1, false)).isEmpty();
        assertThatThrownBy(
                        () ->
                                tx.execute(
                                        s ->
                                                meals.save(
                                                        1,
                                                        saved.id(),
                                                        edit(saved.version(), true))))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(authorization, atLeastOnce()).requirePermission(PermissionName.MENU_MANAGE);
        verify(authorization, atLeastOnce()).requireBranchAccess(1L);
    }

    @Test
    void forbiddenOrCrossBranchWritesAndInvalidRecipesDoNotPublish() {
        doThrow(new SecurityException("forbidden")).when(authorization).requireBranchAccess(2L);
        assertThatThrownBy(() -> tx.execute(s -> meals.save(2, 0, edit(0, true))))
                .isInstanceOf(SecurityException.class);
        jdbc.update("UPDATE branch_products SET available=false WHERE product_id=1");
        assertThatThrownBy(() -> tx.execute(s -> meals.save(1, 0, edit(0, true))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM branch_meals", Long.class)).isZero();
    }

    @Test
    void transactionRollbackDoesNotLeaveAnOfferAndForeignQuotesAreRejected() {
        tx.executeWithoutResult(
                s -> {
                    meals.save(1, 0, edit(0, true));
                    s.setRollbackOnly();
                });
        assertThat(meals.catalog(1, false)).isEmpty();
        var saved = tx.execute(s -> meals.save(1, 0, edit(0, true)));
        assertThatThrownBy(
                        () ->
                                meals.quote(
                                        2,
                                        new MealModels.Claim(
                                                "one",
                                                saved.id(),
                                                saved.version(),
                                                1,
                                                List.of("main1", "side1"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
