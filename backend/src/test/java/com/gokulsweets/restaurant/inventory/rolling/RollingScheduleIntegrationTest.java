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
    @Autowired RollingScheduleService service;
    @Autowired Clock clock;
    @Autowired EnhancementProperties features;

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
}
