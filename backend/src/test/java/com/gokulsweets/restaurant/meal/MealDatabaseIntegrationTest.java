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
        jdbc.execute(
                Files.readString(
                        Path.of(
                                "src/main/resources/db/migration/V145__selected_item_meal_pricing.sql")));
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
    void automaticMealConfigurationAndCapRoundTripWithoutChangingLegacyPrices() {
        var m = new SelectedMealPolicyTest().automatic("PERCENT", "2.00", "2.00");
        var edit =
                new MealModels.Edit(
                        0,
                        m.title(),
                        m.titleHi(),
                        m.description(),
                        m.imageUrl(),
                        m.basePrice(),
                        m.discountType(),
                        m.discountValue(),
                        null,
                        true,
                        m.groups(),
                        m.pricingMode(),
                        m.maximumDiscount());
        var saved = tx.execute(s -> meals.save(1, 0, edit));
        assertThat(saved.pricingMode()).isEqualTo("SELECTED_ITEMS");
        assertThat(saved.basePrice()).isEqualByComparingTo("0.00");
        assertThat(saved.maximumDiscount()).isEqualByComparingTo("2.00");
        var quote =
                meals.quote(
                        1,
                        new MealModels.Claim(
                                "auto", saved.id(), saved.version(), 2, List.of("main1", "side1")));
        assertThat(quote.regularTotal()).isEqualByComparingTo("300.00");
        assertThat(quote.total()).isEqualByComparingTo("296.00");
        var fixed = tx.execute(s -> meals.save(1, 0, edit(0, true)));
        assertThat(fixed.pricingMode()).isEqualTo("FIXED");
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
        jdbc.update("DELETE FROM branch_products WHERE product_id=1");
        assertThatThrownBy(() -> tx.execute(s -> meals.save(1, 0, edit(0, true))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM branch_meals", Long.class)).isZero();
    }

    @Test
    void adminPickerIncludesDisabledDishesWithBoundedCursorPagesAndBranchAuthorization() {
        jdbc.update(
                "UPDATE branch_products SET available=false,price_override=90 WHERE product_id=1");
        jdbc.execute(
                "INSERT INTO products SELECT id,'Dish '||id,'UNIT',100,250,50,true FROM"
                    + " generate_series(10,60) id");
        jdbc.execute(
                "INSERT INTO branch_products SELECT 1,id,null,true FROM generate_series(10,60) id");
        var first = meals.adminProducts(1, 0);
        assertThat(first.products()).hasSize(50);
        assertThat(first.products().getFirst().available()).isFalse();
        assertThat(first.products().getFirst().price()).isEqualByComparingTo("90");
        var second = meals.adminProducts(1, first.nextAfter());
        assertThat(second.products()).hasSize(4);
        assertThat(second.nextAfter()).isNull();
        assertThat(second.products().getFirst().id()).isGreaterThan(first.nextAfter());
        assertThat(meals.adminProducts(2, 0).products()).isEmpty();
        assertThatThrownBy(() -> meals.adminProducts(1, -1))
                .isInstanceOf(IllegalArgumentException.class);
        doThrow(new SecurityException("forbidden")).when(authorization).requireBranchAccess(2L);
        assertThatThrownBy(() -> meals.adminProducts(2, 0)).isInstanceOf(SecurityException.class);
        verify(authorization, atLeastOnce()).requirePermission(PermissionName.MENU_MANAGE);
    }

    @Test
    void disabledDishCanBePublishedWithTrustedNameButCannotBeQuoted() {
        jdbc.update("UPDATE branch_products SET available=false WHERE product_id=1");
        var saved = tx.execute(s -> meals.save(1, 0, edit(0, true)));
        assertThat(saved.active()).isTrue();
        assertThat(saved.groups().getFirst().options().getFirst().productName()).isEqualTo("Main");
        assertThat(meals.catalog(1, false)).hasSize(1);
        assertThatThrownBy(
                        () ->
                                meals.quote(
                                        1,
                                        new MealModels.Claim(
                                                "disabled",
                                                saved.id(),
                                                saved.version(),
                                                1,
                                                List.of("main1", "side1"))))
                .isInstanceOf(IllegalArgumentException.class);
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
