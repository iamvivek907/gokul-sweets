package com.gokulsweets.restaurant.inventory.rolling;

import static org.assertj.core.api.Assertions.*;

import com.gokulsweets.restaurant.config.EnhancementProperties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Real database coverage for independent schedules and policy-only stock generation. */
@SpringBootTest
@Transactional
class RollingScheduleIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired RollingScheduleService service;
    @Autowired Clock clock;
    @Autowired EnhancementProperties features;
    @Autowired com.gokulsweets.restaurant.menu.MenuPickupDiscoveryService discovery;
    @Autowired com.gokulsweets.restaurant.order.service.CartAvailabilityService cart;
    @Autowired com.gokulsweets.restaurant.order.service.SmartOrderingRules ordering;
    @Autowired com.gokulsweets.restaurant.pickup.repository.PickupSlotRepository slots;
    @Autowired com.gokulsweets.restaurant.inventory.config.InventoryProperties inventoryProperties;

    private record Fixture(long branch, long policy, long product) {}

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Long branch =
                jdbc.queryForObject(
                        "INSERT INTO branches(code,name) VALUES(?, 'Rolling test') RETURNING id",
                        Long.class,
                        "ROLL-" + suffix);
        Long category =
                jdbc.queryForObject(
                        "INSERT INTO categories(code,name) VALUES(?, 'Rolling category') RETURNING"
                                + " id",
                        Long.class,
                        "ROLL-C-" + suffix);
        Long tax =
                jdbc.queryForObject(
                        "INSERT INTO tax_categories(code,name,cgst_rate,sgst_rate)"
                                + " VALUES(?,'Rolling food',0,0) RETURNING id",
                        Long.class,
                        "ROLL-T-" + suffix);
        Long product =
                jdbc.queryForObject(
                        "INSERT INTO products(code,name,category_id,base_price,tax_category_id)"
                                + " VALUES(?,'Rolling sweet',?,10,?) RETURNING id",
                        Long.class,
                        "ROLL-P-" + suffix,
                        category,
                        tax);
        Long bp =
                jdbc.queryForObject(
                        """
INSERT INTO branch_products(branch_id,product_id,available,display_order,created_at,updated_at)
VALUES(?,?,TRUE,0,now(),now()) RETURNING id
""",
                        Long.class,
                        branch,
                        product);
        Long policy =
                jdbc.queryForObject(
                        """
INSERT INTO branch_inventory_policies(branch_product_id,control_mode,inventory_unit,online_enabled,
ready_stock_required,default_safety_buffer,maximum_daily_allocation,booking_horizon_days,created_at,updated_at)
VALUES(?,'DAILY_PRODUCTION','PIECE',TRUE,FALSE,2,100,14,now(),now()) RETURNING id
""",
                        Long.class,
                        bp);
        return new Fixture(branch, policy, bp);
    }

    private RollingScheduleService.Plan plan(Fixture f, long version, boolean enabled) {
        return new RollingScheduleService.Plan(
                enabled,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                30,
                5,
                2,
                127,
                version,
                List.of(new RollingScheduleService.Template(f.policy(), BigDecimal.TEN, true)));
    }

    @Test
    void generatesOnceForSelectedPoliciesAndLeavesManualOverridesUntouched() {
        var f = fixture();
        service.save(f.branch(), plan(f, -1, true), "manager");
        assertThat(service.ownsInventory(f.branch())).isTrue();
        service.reconcile(f.branch());
        // Approval uses JPA inside this test's outer transaction; make it visible to JDBC,
        // then clear managed entities before simulating a separate manual update.
        entityManager.flush();
        entityManager.clear();
        LocalDate tomorrow = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pickup_slots WHERE branch_id=? AND"
                                        + " slot_date=?",
                                Integer.class,
                                f.branch(),
                                tomorrow))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT approved_quantity FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=? AND service_date=?",
                                BigDecimal.class,
                                f.product(),
                                tomorrow))
                .isEqualByComparingTo("10");
        jdbc.update(
                "UPDATE inventory_daily_allocations SET approved_quantity=7 WHERE"
                        + " branch_product_id=? AND service_date=?",
                f.product(),
                tomorrow);
        jdbc.update(
                "UPDATE pickup_slots SET active=FALSE WHERE branch_id=? AND slot_date=?",
                f.branch(),
                tomorrow);
        service.reconcile(f.branch());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT approved_quantity FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=? AND service_date=?",
                                BigDecimal.class,
                                f.product(),
                                tomorrow))
                .isEqualByComparingTo("7");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pickup_slots WHERE branch_id=? AND"
                                        + " slot_date=?",
                                Integer.class,
                                f.branch(),
                                tomorrow))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pickup_slots WHERE branch_id=? AND"
                                        + " slot_date=? AND active",
                                Integer.class,
                                f.branch(),
                                tomorrow))
                .isZero();
        service.save(f.branch(), plan(f, 0, false), "manager");
        assertThat(service.ownsInventory(f.branch())).isFalse();
    }

    @Test
    void slotsDoNotCreateInventoryForUnselectedOrOfflinePolicies() {
        var f = fixture();
        var noItems =
                new RollingScheduleService.Plan(
                        true,
                        LocalTime.of(10, 0),
                        LocalTime.of(11, 0),
                        30,
                        5,
                        2,
                        127,
                        -1,
                        List.of());
        service.save(f.branch(), noItems, "manager");
        service.reconcile(f.branch());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=?",
                                Integer.class,
                                f.product()))
                .isZero();
        service.save(f.branch(), plan(f, 0, true), "manager");
        jdbc.update(
                "UPDATE branch_inventory_policies SET online_enabled=FALSE WHERE id=?", f.policy());
        service.reconcile(f.branch());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=?",
                                Integer.class,
                                f.product()))
                .isZero();
    }

    @Test
    void cannotSelectAnotherBranchPolicyOrSaveAStaleVersion() {
        var first = fixture();
        var other = fixture();
        var invalid =
                new RollingScheduleService.Plan(
                        true,
                        LocalTime.of(10, 0),
                        LocalTime.of(11, 0),
                        30,
                        5,
                        2,
                        127,
                        -1,
                        List.of(
                                new RollingScheduleService.Template(
                                        other.policy(), BigDecimal.TEN, true)));
        assertThatThrownBy(() -> service.save(first.branch(), invalid, "manager"))
                .hasMessageContaining("configured inventory policy");
        service.save(first.branch(), plan(first, -1, true), "manager");
        assertThatThrownBy(() -> service.save(first.branch(), plan(first, -1, true), "manager"))
                .hasMessageContaining("schedule changed");
    }

    @Test
    void preservesManualDatesRatherThanAddingAnotherTimetable() {
        var f = fixture();
        LocalDate tomorrow = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1);
        jdbc.update(
                "INSERT INTO"
                    + " pickup_slots(branch_id,slot_date,start_time,end_time,capacity,booked_count)"
                    + " VALUES(?,?,'12:00','12:30',8,0)",
                f.branch(),
                tomorrow);
        service.save(f.branch(), plan(f, -1, true), "manager");
        service.reconcile(f.branch());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pickup_slots WHERE branch_id=? AND"
                                        + " slot_date=?",
                                Integer.class,
                                f.branch(),
                                tomorrow))
                .isEqualTo(1);
    }

    @Test
    void generatedWindowMatchesDiscoveryCartAndCheckoutWithoutEditingManualSettings() {
        var f = fixture();
        jdbc.update(
                "INSERT INTO"
                    + " branch_pickup_settings(branch_id,advance_booking_days,opening_time,closing_time)"
                    + " VALUES(?,1,'10:00','11:00')",
                f.branch());
        var configured =
                new RollingScheduleService.Plan(
                        true,
                        LocalTime.of(10, 0),
                        LocalTime.of(11, 0),
                        30,
                        5,
                        14,
                        127,
                        -1,
                        List.of(
                                new RollingScheduleService.Template(
                                        f.policy(), BigDecimal.TEN, true)));
        service.save(f.branch(), configured, "manager");
        service.reconcile(f.branch());
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        LocalDate last = today.plusDays(13);
        var discovered = discovery.discover(f.branch(), last, 1);
        assertThat(discovered.maximumDate()).isEqualTo(last);
        assertThat(discovered.dates().getFirst().available()).isTrue();
        long productId =
                jdbc.queryForObject(
                        "SELECT product_id FROM branch_products WHERE id=?",
                        Long.class,
                        f.product());
        boolean previousEnforcement = inventoryProperties.isEnforcementEnabled();
        boolean previousSmart = features.isSmartAvailability();
        try {
            inventoryProperties.setEnforcementEnabled(true);
            features.setSmartAvailability(true);
            var preview =
                    cart.check(
                            f.branch(),
                            last,
                            1,
                            List.of(
                                    new com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest(
                                            productId, 1, null)));
            assertThat(preview.maximumDate()).isEqualTo(last);
            assertThat(preview.dates().getFirst().available()).isTrue();
            var slot =
                    slots.findByBranchIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
                                    f.branch(), last, last)
                            .getFirst();
            assertThatCode(() -> ordering.validateWindow(slot)).doesNotThrowAnyException();
            assertThatThrownBy(() -> discovery.discover(f.branch(), last.plusDays(1), 1))
                    .hasMessageContaining("window");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT advance_booking_days FROM branch_pickup_settings WHERE"
                                            + " branch_id=?",
                                    Integer.class,
                                    f.branch()))
                    .isEqualTo(1);
            service.save(
                    f.branch(),
                    new RollingScheduleService.Plan(
                            false,
                            configured.openingTime(),
                            configured.closingTime(),
                            30,
                            5,
                            14,
                            127,
                            0,
                            configured.templates()),
                    "manager");
            assertThatThrownBy(() -> discovery.discover(f.branch(), last, 1))
                    .hasMessageContaining("window");
            assertThatThrownBy(() -> ordering.validateWindow(slot)).hasMessageContaining("window");
        } finally {
            inventoryProperties.setEnforcementEnabled(previousEnforcement);
            features.setSmartAvailability(previousSmart);
        }
    }
}
