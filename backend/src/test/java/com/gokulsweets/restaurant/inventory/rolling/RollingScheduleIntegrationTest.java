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
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired EnhancementProperties features;
    @Autowired tools.jackson.databind.ObjectMapper mapper;
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
        assertThat(service.workspace(f.branch()).message())
                .contains("No dishes selected in the saved plan", "0 allocations created");
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
    void explainsZeroAllocationsWhenExistingDatesHaveNoActiveRegularSlots() {
        var f = fixture();
        service.save(f.branch(), plan(f, -1, true), "manager");
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        for (int day = 0; day < 2; day++) {
            jdbc.update(
                    "INSERT INTO"
                        + " pickup_slots(branch_id,slot_date,start_time,end_time,capacity,active)"
                        + " VALUES(?,?,'10:00','11:00',10,FALSE)",
                    f.branch(),
                    today.plusDays(day));
        }
        service.reconcile(f.branch());
        assertThat(service.workspace(f.branch()).message())
                .contains(
                        "0 allocations created",
                        "1 selected policies",
                        "No upcoming active regular pickup slots");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=?",
                                Integer.class,
                                f.product()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pickup_slots WHERE branch_id=? AND active",
                                Integer.class,
                                f.branch()))
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
    void preservesManualSlotsAndFillsOnlyMissingConfiguredIntervals() {
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
                .isEqualTo(3);
    }

    @Test
    void repairsDeletedIntervalsOnClaimedDatesWithoutChangingDisabledOrBookedSlots() {
        var f = fixture();
        LocalDate tomorrow = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1);
        service.save(f.branch(), maximumPlan(-1, 2), "manager");
        service.reconcile(f.branch());
        entityManager.flush();
        entityManager.clear();
        jdbc.update(
                "UPDATE pickup_slots SET active=FALSE,booked_count=1 WHERE branch_id=? AND"
                        + " slot_date=? AND start_time='10:00'",
                f.branch(),
                tomorrow);
        var original =
                jdbc.queryForMap(
                        "SELECT * FROM pickup_slots WHERE branch_id=? AND slot_date=? AND"
                                + " start_time='10:00'",
                        f.branch(),
                        tomorrow);
        var allocation =
                jdbc.queryForMap(
                        "SELECT * FROM inventory_daily_allocations WHERE branch_product_id=? AND"
                                + " service_date=?",
                        f.product(),
                        tomorrow);
        jdbc.update(
                "DELETE FROM pickup_slots WHERE branch_id=? AND slot_date=? AND start_time='10:30'",
                f.branch(),
                tomorrow);
        for (int i = 0; i < 3; i++) service.reconcile(f.branch());
        assertThat(
                        jdbc.queryForMap(
                                "SELECT * FROM pickup_slots WHERE branch_id=? AND slot_date=? AND"
                                        + " start_time='10:00'",
                                f.branch(),
                                tomorrow))
                .isEqualTo(original);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pickup_slots WHERE branch_id=? AND"
                                        + " slot_date=? AND active AND start_time='10:30'",
                                Integer.class,
                                f.branch(),
                                tomorrow))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForMap(
                                "SELECT * FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=? AND service_date=?",
                                f.product(),
                                tomorrow))
                .isEqualTo(allocation);
        // The date remains claimed, but losing every interval must still be repairable.
        jdbc.update(
                "DELETE FROM pickup_slots WHERE branch_id=? AND slot_date=?", f.branch(), tomorrow);
        service.reconcile(f.branch());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pickup_slots WHERE branch_id=? AND"
                                        + " slot_date=? AND active",
                                Integer.class,
                                f.branch(),
                                tomorrow))
                .isEqualTo(2);
    }

    @Test
    void recordedReadyStockIncludingZeroWinsWhileMissingAllocationsUseMaximum() {
        var f = fixture();
        var zero = fixture();
        var missing = fixture();
        jdbc.update(
                "UPDATE branch_products SET branch_id=? WHERE id IN (?,?)",
                f.branch(),
                zero.product(),
                missing.product());
        jdbc.update(
                "UPDATE branch_inventory_policies SET ready_stock_required=TRUE WHERE id IN"
                        + " (?,?,?)",
                f.policy(),
                zero.policy(),
                missing.policy());
        LocalDate tomorrow = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1);
        for (var item : List.of(f, zero)) {
            jdbc.update(
                    "INSERT INTO"
                        + " inventory_daily_allocations(branch_product_id,service_date,status,inventory_unit,approved_quantity,ready_quantity,safety_buffer_quantity,actual_ready_at,created_at,updated_at)"
                        + " VALUES(?,?,'READY','PIECE',100,?,2,now(),now(),now())",
                    item.product(),
                    tomorrow,
                    item == f ? 30 : 0);
        }
        var readyBefore =
                jdbc.queryForMap(
                        "SELECT * FROM inventory_daily_allocations WHERE branch_product_id=? AND"
                                + " service_date=?",
                        f.product(),
                        tomorrow);
        var zeroBefore =
                jdbc.queryForMap(
                        "SELECT * FROM inventory_daily_allocations WHERE branch_product_id=? AND"
                                + " service_date=?",
                        zero.product(),
                        tomorrow);
        service.save(f.branch(), maximumPlan(-1, 2), "manager");
        for (int i = 0; i < 3; i++) service.reconcile(f.branch());
        entityManager.flush();
        entityManager.clear();
        assertThat(
                        jdbc.queryForMap(
                                "SELECT * FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=? AND service_date=?",
                                f.product(),
                                tomorrow))
                .isEqualTo(readyBefore);
        assertThat(
                        jdbc.queryForMap(
                                "SELECT * FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=? AND service_date=?",
                                zero.product(),
                                tomorrow))
                .isEqualTo(zeroBefore);
        var generated =
                jdbc.queryForMap(
                        "SELECT approved_quantity,ready_quantity,status FROM"
                                + " inventory_daily_allocations WHERE branch_product_id=? AND"
                                + " service_date=?",
                        missing.product(),
                        tomorrow);
        assertThat((BigDecimal) generated.get("approved_quantity")).isEqualByComparingTo("100");
        assertThat((BigDecimal) generated.get("ready_quantity")).isZero();
        assertThat(generated.get("status")).isEqualTo("APPROVED");
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

    @Test
    void bulkOnlySavedSelectionRemainsRemovableAndDoesNotPreventPause() {
        var f = fixture();
        service.save(f.branch(), plan(f, -1, true), "manager");
        jdbc.update("UPDATE branch_products SET occasion_only=TRUE WHERE id=?", f.product());
        var policy = service.workspace(f.branch()).policies().getFirst();
        assertThat(policy.selectable()).isFalse();
        assertThat(policy.eligible()).isFalse();
        assertThat(policy.unavailableReason()).contains("Bulk-only");
        service.reconcile(f.branch());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=?",
                                Integer.class,
                                f.product()))
                .isZero();
        service.save(f.branch(), plan(f, 0, false), "manager");
        assertThat(service.ownsInventory(f.branch())).isFalse();
        assertThat(service.workspace(f.branch()).plan().templates()).hasSize(1);
        var saved = plan(f, 1, true);
        service.save(
                f.branch(),
                new RollingScheduleService.Plan(
                        true,
                        saved.openingTime(),
                        saved.closingTime(),
                        30,
                        5,
                        2,
                        127,
                        1,
                        List.of()),
                "manager");
        assertThat(service.workspace(f.branch()).plan().templates()).isEmpty();
        assertThat(service.workspace(f.branch()).policies()).isEmpty();
    }

    @Test
    void capacityAndManualPoliciesCannotGeneratePhysicalAllocations() {
        var f = fixture();
        service.save(f.branch(), plan(f, -1, true), "manager");
        jdbc.update(
                "UPDATE branch_inventory_policies SET"
                    + " control_mode='SLOT_CAPACITY',inventory_unit='CAPACITY_POINT' WHERE id=?",
                f.policy());
        var capacity = service.workspace(f.branch()).policies().getFirst();
        assertThat(capacity.selectable()).isFalse();
        assertThat(capacity.eligible()).isFalse();
        assertThat(capacity.unavailableReason()).contains("Capacity-only");
        service.reconcile(f.branch());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=?",
                                Integer.class,
                                f.product()))
                .isZero();
        // Existing selections remain safe to retain or remove after a control-mode change.
        service.save(f.branch(), plan(f, 0, true), "manager");
        jdbc.update(
                "UPDATE branch_inventory_policies SET control_mode='MANUAL',inventory_unit='PIECE'"
                        + " WHERE id=?",
                f.policy());
        assertThat(service.workspace(f.branch()).policies().getFirst().selectable()).isFalse();
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
    void rejectsNewCapacitySelectionAndAllowsPauseAfterLimitsChange() {
        var f = fixture();
        service.save(f.branch(), plan(f, -1, true), "manager");
        jdbc.update(
                "UPDATE branch_inventory_policies SET maximum_daily_allocation=5 WHERE id=?",
                f.policy());
        service.save(f.branch(), plan(f, 0, false), "manager");
        assertThat(service.ownsInventory(f.branch())).isFalse();
        var other = fixture();
        jdbc.update(
                "UPDATE branch_inventory_policies SET"
                    + " control_mode='SLOT_CAPACITY',inventory_unit='CAPACITY_POINT' WHERE id=?",
                other.policy());
        assertThatThrownBy(() -> service.save(other.branch(), plan(other, -1, true), "manager"))
                .hasMessageContaining("supported physical inventory policy");
    }

    private RollingScheduleService.Plan maximumPlan(long version, int days) {
        return new RollingScheduleService.Plan(
                true,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                30,
                5,
                days,
                127,
                version,
                List.of(),
                true);
    }

    @Test
    void maximumModeUsesGrossCapOnceAndSkipsPoliciesWithoutUsableMaximums() {
        var f = fixture();
        var missing = fixture();
        var blocked = fixture();
        var offline = fixture();
        for (var extra : List.of(missing, blocked, offline))
            jdbc.update(
                    "UPDATE branch_products SET branch_id=? WHERE id=?",
                    f.branch(),
                    extra.product());
        jdbc.update(
                "UPDATE branch_inventory_policies SET maximum_daily_allocation=NULL WHERE id=?",
                missing.policy());
        jdbc.update(
                "UPDATE branch_inventory_policies SET maximum_daily_allocation=2 WHERE id=?",
                blocked.policy());
        jdbc.update(
                "UPDATE branch_inventory_policies SET online_enabled=FALSE WHERE id=?",
                offline.policy());
        service.save(f.branch(), maximumPlan(-1, 2), "manager");
        assertThat(service.workspace(f.branch()).plan().usePolicyMaximum()).isTrue();
        assertThat(service.workspace(f.branch()).plan().templates()).isEmpty();
        service.reconcile(f.branch());
        entityManager.flush();
        entityManager.clear();
        LocalDate tomorrow = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1);
        var row =
                jdbc.queryForMap(
                        "SELECT approved_quantity,safety_buffer_quantity,status FROM"
                                + " inventory_daily_allocations WHERE branch_product_id=? AND"
                                + " service_date=?",
                        f.product(),
                        tomorrow);
        assertThat((BigDecimal) row.get("approved_quantity")).isEqualByComparingTo("100");
        assertThat((BigDecimal) row.get("safety_buffer_quantity")).isEqualByComparingTo("2");
        assertThat(
                        ((BigDecimal) row.get("approved_quantity"))
                                .subtract((BigDecimal) row.get("safety_buffer_quantity")))
                .isEqualByComparingTo("98");
        assertThat(row.get("status")).isEqualTo("APPROVED");
        for (var extra : List.of(missing, blocked, offline))
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM inventory_daily_allocations WHERE"
                                            + " branch_product_id=?",
                                    Integer.class,
                                    extra.product()))
                    .isZero();
        assertThat(service.workspace(f.branch()).message())
                .contains("Policy maximum mode: 3 policies skipped");
    }

    @Test
    void maximumModeIsRepeatSafePreservesOverridesAndExtendsSlotBackedCoverage() {
        var f = fixture();
        service.save(f.branch(), maximumPlan(-1, 2), "manager");
        service.reconcile(f.branch());
        entityManager.flush();
        entityManager.clear();
        LocalDate tomorrow = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1);
        jdbc.update(
                "UPDATE inventory_daily_allocations SET"
                    + " approved_quantity=7,held_quantity=1,committed_quantity=2,ready_quantity=5,note='Manual"
                    + " override' WHERE branch_product_id=? AND service_date=?",
                f.product(),
                tomorrow);
        var preserved =
                jdbc.queryForMap(
                        "SELECT * FROM inventory_daily_allocations WHERE branch_product_id=? AND"
                                + " service_date=?",
                        f.product(),
                        tomorrow);
        int ledgers =
                jdbc.queryForObject(
                        "SELECT count(*) FROM inventory_stock_transactions WHERE"
                                + " branch_product_id=?",
                        Integer.class,
                        f.product());
        jdbc.update(
                "UPDATE branch_inventory_policies SET maximum_daily_allocation=50 WHERE id=?",
                f.policy());
        for (int run = 0; run < 3; run++) service.reconcile(f.branch());
        assertThat(
                        jdbc.queryForMap(
                                "SELECT * FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=? AND service_date=?",
                                f.product(),
                                tomorrow))
                .isEqualTo(preserved);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM inventory_stock_transactions WHERE"
                                        + " branch_product_id=?",
                                Integer.class,
                                f.product()))
                .isEqualTo(ledgers);
        service.save(f.branch(), maximumPlan(0, 3), "manager");
        service.reconcile(f.branch());
        entityManager.flush();
        entityManager.clear();
        LocalDate future = tomorrow.plusDays(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pickup_slots WHERE branch_id=? AND"
                                        + " slot_date=? AND active",
                                Integer.class,
                                f.branch(),
                                future))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT approved_quantity FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=? AND service_date=?",
                                BigDecimal.class,
                                f.product(),
                                future))
                .isEqualByComparingTo("50");
        assertThat(
                        jdbc.queryForMap(
                                "SELECT * FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=? AND service_date=?",
                                f.product(),
                                tomorrow))
                .isEqualTo(preserved);
        var added = fixture();
        jdbc.update(
                "UPDATE branch_products SET branch_id=? WHERE id=?", f.branch(), added.product());
        service.reconcile(f.branch());
        entityManager.flush();
        entityManager.clear();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT approved_quantity FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=? AND service_date=?",
                                BigDecimal.class,
                                added.product(),
                                future))
                .isEqualByComparingTo("100");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=? AND service_date=?",
                                Integer.class,
                                f.product(),
                                future))
                .isEqualTo(1);
    }

    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentMaximumJobsCreateOneAllocationAndOneApprovalPerItemDate() throws Exception {
        var tx =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var f =
                tx.execute(
                        status -> {
                            var created = fixture();
                            service.save(created.branch(), maximumPlan(-1, 2), "manager");
                            return created;
                        });
        try {
            try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var start = new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.Callable<Void> job =
                        () -> {
                            start.await();
                            service.reconcile(f.branch());
                            return null;
                        };
                var first = workers.submit(job);
                var second = workers.submit(job);
                start.countDown();
                first.get(30, java.util.concurrent.TimeUnit.SECONDS);
                second.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
            LocalDate tomorrow =
                    LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM inventory_daily_allocations WHERE"
                                            + " branch_product_id=? AND service_date=?",
                                    Integer.class,
                                    f.product(),
                                    tomorrow))
                    .isEqualTo(1);
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
                                    "SELECT count(*) FROM inventory_stock_transactions WHERE"
                                            + " branch_product_id=?",
                                    Integer.class,
                                    f.product()))
                    .isEqualTo(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM inventory_daily_allocations WHERE"
                                            + " branch_product_id=?",
                                    Integer.class,
                                    f.product()));
        } finally {
            tx.executeWithoutResult(
                    status -> {
                        jdbc.queryForObject(
                                "SELECT id FROM branches WHERE id=? FOR UPDATE",
                                Long.class,
                                f.branch());
                        var details =
                                jdbc.queryForMap(
                                        "SELECT p.id,p.category_id,p.tax_category_id FROM products"
                                                + " p JOIN branch_products bp ON bp.product_id=p.id"
                                                + " WHERE bp.id=?",
                                        f.product());
                        jdbc.update(
                                "DELETE FROM inventory_stock_transactions WHERE"
                                        + " branch_product_id=?",
                                f.product());
                        jdbc.update(
                                "DELETE FROM inventory_allocation_plan_audit WHERE"
                                        + " branch_product_id=?",
                                f.product());
                        jdbc.update(
                                "DELETE FROM inventory_daily_allocations WHERE branch_product_id=?",
                                f.product());
                        jdbc.update(
                                "DELETE FROM rolling_inventory_templates WHERE branch_id=?",
                                f.branch());
                        jdbc.update(
                                "DELETE FROM rolling_schedule_dates WHERE branch_id=?", f.branch());
                        jdbc.update(
                                "DELETE FROM rolling_branch_schedules WHERE branch_id=?",
                                f.branch());
                        jdbc.update("DELETE FROM pickup_slots WHERE branch_id=?", f.branch());
                        jdbc.update("DELETE FROM branch_inventory_policies WHERE id=?", f.policy());
                        jdbc.update("DELETE FROM branch_products WHERE id=?", f.product());
                        jdbc.update("DELETE FROM products WHERE id=?", details.get("id"));
                        jdbc.update(
                                "DELETE FROM categories WHERE id=?", details.get("category_id"));
                        jdbc.update(
                                "DELETE FROM tax_categories WHERE id=?",
                                details.get("tax_category_id"));
                        jdbc.update("DELETE FROM branches WHERE id=?", f.branch());
                    });
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void skipsInactiveProductsAndCategoriesAndResumesWhenReactivated(boolean maximum) {
        var active = fixture();
        var inactiveProduct = fixture();
        var inactiveCategory = fixture();
        for (var item : List.of(inactiveProduct, inactiveCategory)) {
            jdbc.update(
                    "UPDATE branch_products SET branch_id=? WHERE id=?",
                    active.branch(),
                    item.product());
        }
        jdbc.update(
                "UPDATE products SET active=FALSE WHERE id=(SELECT product_id FROM branch_products"
                        + " WHERE id=?)",
                inactiveProduct.product());
        jdbc.update(
                "UPDATE categories SET active=FALSE WHERE id=(SELECT p.category_id FROM products p"
                        + " JOIN branch_products bp ON bp.product_id=p.id WHERE bp.id=?)",
                inactiveCategory.product());
        var templates =
                List.of(active, inactiveProduct, inactiveCategory).stream()
                        .map(
                                item ->
                                        new RollingScheduleService.Template(
                                                item.policy(), BigDecimal.TEN, true))
                        .toList();
        service.save(
                active.branch(),
                new RollingScheduleService.Plan(
                        true,
                        LocalTime.of(10, 0),
                        LocalTime.of(11, 0),
                        30,
                        5,
                        2,
                        127,
                        -1,
                        templates,
                        maximum),
                "manager");
        var policies = service.workspace(active.branch()).policies();
        assertThat(
                        policies.stream()
                                .filter(p -> p.id() == inactiveProduct.policy())
                                .findFirst()
                                .orElseThrow())
                .satisfies(
                        p -> {
                            assertThat(p.eligible()).isFalse();
                            assertThat(p.unavailableReason()).contains("dish is inactive");
                        });
        assertThat(
                        policies.stream()
                                .filter(p -> p.id() == inactiveCategory.policy())
                                .findFirst()
                                .orElseThrow())
                .satisfies(
                        p -> {
                            assertThat(p.eligible()).isFalse();
                            assertThat(p.unavailableReason()).contains("category is inactive");
                        });
        service.reconcile(active.branch());
        entityManager.flush();
        entityManager.clear();
        LocalDate tomorrow = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1);
        var preserved =
                jdbc.queryForMap(
                        "SELECT * FROM inventory_daily_allocations WHERE branch_product_id=? AND"
                                + " service_date=?",
                        active.product(),
                        tomorrow);
        for (var item : List.of(inactiveProduct, inactiveCategory)) {
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM inventory_daily_allocations WHERE"
                                            + " branch_product_id=?",
                                    Integer.class,
                                    item.product()))
                    .isZero();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM inventory_stock_transactions WHERE"
                                            + " branch_product_id=?",
                                    Integer.class,
                                    item.product()))
                    .isZero();
        }
        jdbc.update(
                "UPDATE products SET active=TRUE WHERE id=(SELECT product_id FROM branch_products"
                        + " WHERE id=?)",
                inactiveProduct.product());
        jdbc.update(
                "UPDATE categories SET active=TRUE WHERE id=(SELECT p.category_id FROM products p"
                        + " JOIN branch_products bp ON bp.product_id=p.id WHERE bp.id=?)",
                inactiveCategory.product());
        service.reconcile(active.branch());
        entityManager.flush();
        entityManager.clear();
        for (var item : List.of(inactiveProduct, inactiveCategory)) {
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT approved_quantity FROM inventory_daily_allocations"
                                            + " WHERE branch_product_id=? AND service_date=?",
                                    BigDecimal.class,
                                    item.product(),
                                    tomorrow))
                    .isEqualByComparingTo(maximum ? "100" : "10");
        }
        assertThat(
                        jdbc.queryForMap(
                                "SELECT * FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=? AND service_date=?",
                                active.product(),
                                tomorrow))
                .isEqualTo(preserved);
    }

    @Test
    void coverageHealthDistinguishesMissingRecordsFromDisabledDatesAndBookingLimits() {
        var f = fixture();
        service.save(f.branch(), maximumPlan(-1, 2), "manager");
        var today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        var audit =
                new RollingAutomationDiagnostics(
                        jdbc,
                        Clock.fixed(
                                today.atTime(12, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant(),
                                ZoneId.of("Asia/Kolkata")),
                        features);
        var before = audit.health(f.branch(), service.workspace(f.branch()), List.of());
        assertThat(before.status()).isEqualTo("NEEDS_ATTENTION");
        assertThat(before.missingSlots()).isEqualTo(2);
        assertThat(before.missingAllocations()).isEqualTo(1);
        service.reconcile(f.branch(), "MANUAL", "test-manager");
        entityManager.flush();
        entityManager.clear();
        var after = audit.health(f.branch(), service.workspace(f.branch()), List.of());
        assertThat(after.status()).isEqualTo("COMPLETE");
        assertThat(after.missingSlots()).isZero();
        assertThat(after.missingAllocations()).isZero();
        jdbc.update("UPDATE pickup_slots SET active=FALSE WHERE branch_id=?", f.branch());
        var disabled = audit.health(f.branch(), service.workspace(f.branch()), List.of());
        assertThat(disabled.status()).isEqualTo("COMPLETE");
        assertThat(disabled.expectedAllocations()).isZero();
        jdbc.update("UPDATE pickup_slots SET active=TRUE WHERE branch_id=?", f.branch());
        jdbc.update(
                "UPDATE branch_inventory_policies SET booking_horizon_days=0 WHERE id=?",
                f.policy());
        assertThat(
                        audit.health(f.branch(), service.workspace(f.branch()), List.of())
                                .expectedAllocations())
                .isZero();
        var run = service.inspect(f.branch()).runs().getFirst();
        assertThat(run.trigger()).isEqualTo("MANUAL");
        assertThat(run.actor()).isEqualTo("test-manager");
        assertThat(run.status()).isEqualTo("SUCCESS");
        assertThat(run.durationMs()).isGreaterThanOrEqualTo(run.lockWaitMs());
        assertThat(run.lockHeldMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void overdueAlertsRespectMidnightGraceNewPlansAndSuccessfulManualRecovery() {
        var f = fixture();
        service.save(f.branch(), maximumPlan(-1, 2), "manager");
        var tomorrow = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1);
        var zone = ZoneId.of("Asia/Kolkata");
        var grace =
                new RollingAutomationDiagnostics(
                        jdbc,
                        Clock.fixed(tomorrow.atTime(0, 10).atZone(zone).toInstant(), zone),
                        features);
        var overdue =
                new RollingAutomationDiagnostics(
                        jdbc,
                        Clock.fixed(tomorrow.atTime(0, 16).atZone(zone).toInstant(), zone),
                        features);
        assertThat(grace.health(f.branch(), service.workspace(f.branch()), List.of()).overdue())
                .isFalse();
        assertThat(overdue.health(f.branch(), service.workspace(f.branch()), List.of()).overdue())
                .isTrue();
        jdbc.update(
                "UPDATE rolling_branch_schedules SET last_success_at=? WHERE branch_id=?",
                java.sql.Timestamp.from(tomorrow.atTime(0, 5).atZone(zone).toInstant()),
                f.branch());
        assertThat(overdue.health(f.branch(), service.workspace(f.branch()), List.of()).overdue())
                .isFalse();
        jdbc.update(
                "UPDATE rolling_branch_schedules SET last_success_at=NULL,enabled_since_at=? WHERE"
                        + " branch_id=?",
                java.sql.Timestamp.from(tomorrow.atTime(0, 15).atZone(zone).toInstant()),
                f.branch());
        assertThat(overdue.health(f.branch(), service.workspace(f.branch()), List.of()).overdue())
                .isFalse();
    }

    @Test
    void healthExamplesAndExecutionHistoryRemainBounded() {
        var f = fixture();
        service.save(f.branch(), maximumPlan(-1, 60), "manager");
        var audit = new RollingAutomationDiagnostics(jdbc, clock, features);
        var health = audit.health(f.branch(), service.workspace(f.branch()), List.of());
        assertThat(health.issues()).hasSizeLessThanOrEqualTo(30);
        assertThat(health.missingSlots()).isGreaterThan(30);
        assertThat(health.missingAllocations())
                .isEqualTo(
                        Math.min(14, features.getFutureOrderingDays())
                                + (LocalTime.now(clock.withZone(ZoneId.of("Asia/Kolkata")))
                                                .isBefore(LocalTime.of(10, 30))
                                        ? 1
                                        : 0));
        for (int i = 0; i < 35; i++) {
            long run = audit.start(f.branch(), "MANUAL", "manager-" + i);
            audit.finish(run, f.branch(), "SUCCESS", 12, 3, 8, 0, 0, "Coverage checked");
        }
        assertThat(audit.runs(f.branch())).hasSize(30);
        assertThat(audit.runs(f.branch()).getFirst().actor()).isEqualTo("manager-34");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM rolling_automation_runs WHERE branch_id=?",
                                Integer.class,
                                f.branch()))
                .isEqualTo(30);
        assertThat(audit.runs(f.branch() + 999999)).isEmpty();
    }

    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void failedExecutionHistorySurvivesBranchTransactionRollback() {
        long branch =
                jdbc.queryForObject(
                        "INSERT INTO branches(code,name) VALUES(?,'Failure history test') RETURNING"
                                + " id",
                        Long.class,
                        "FAIL-HISTORY-" + UUID.randomUUID());
        try {
            service.save(branch, maximumPlan(-1, 2), "manager");
            jdbc.update("UPDATE branches SET operational=FALSE WHERE id=?", branch);
            assertThatThrownBy(() -> service.reconcile(branch, "MANUAL", "test-manager"))
                    .hasMessageContaining("active and operational");
            var run = service.inspect(branch).runs().getFirst();
            assertThat(run.status()).isEqualTo("FAILED");
            assertThat(run.actor()).isEqualTo("test-manager");
            assertThat(run.slotsCreated()).isZero();
            assertThat(run.allocationsCreated()).isZero();
            assertThat(service.inspect(branch).health().lastRunFailed()).isTrue();
        } finally {
            jdbc.update("DELETE FROM rolling_branch_schedules WHERE branch_id=?", branch);
            jdbc.update("DELETE FROM branches WHERE id=?", branch);
        }
    }

    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void durableRunStartDoesNotWaitForAnUncommittedSlotWriter() throws Exception {
        long branch =
                jdbc.queryForObject(
                        "INSERT INTO branches(code,name) VALUES(?,'Run lock test') RETURNING id",
                        Long.class,
                        "RUN-LOCK-" + UUID.randomUUID());
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var audit = new RollingAutomationDiagnostics(jdbc, clock, features);
        try {
            var transaction =
                    new org.springframework.transaction.support.TransactionTemplate(
                            transactionManager);
            transaction.executeWithoutResult(
                    status -> {
                        jdbc.update(
                                "INSERT INTO"
                                    + " pickup_slots(branch_id,slot_date,start_time,end_time,capacity,active)"
                                    + " VALUES(?,?,'10:00','11:00',5,TRUE)",
                                branch,
                                LocalDate.now(clock).plusDays(1));
                        var future =
                                executor.submit(
                                        () -> audit.start(branch, "MANUAL", "concurrent-manager"));
                        try {
                            assertThat(future.get(5, java.util.concurrent.TimeUnit.SECONDS))
                                    .isPositive();
                        } catch (Exception failure) {
                            throw new IllegalStateException(
                                    "Run audit must not block on the schedule mutex", failure);
                        }
                        status.setRollbackOnly();
                    });
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
            jdbc.update("DELETE FROM pickup_slots WHERE branch_id=?", branch);
            jdbc.update("DELETE FROM branches WHERE id=?", branch);
        }
    }

    @Test
    void editingExistingPlanPreservesActivationAndMissedRunAlert() {
        var f = fixture();
        service.save(f.branch(), maximumPlan(-1, 2), "manager");
        var zone = ZoneId.of("Asia/Kolkata");
        var today = LocalDate.now(clock.withZone(zone));
        var original = java.sql.Timestamp.from(today.minusDays(1).atStartOfDay(zone).toInstant());
        jdbc.update(
                "UPDATE rolling_branch_schedules SET"
                        + " enabled_since_at=?,updated_at=?,last_success_at=NULL WHERE branch_id=?",
                original,
                original,
                f.branch());
        var audit =
                new RollingAutomationDiagnostics(
                        jdbc,
                        Clock.fixed(today.atTime(23, 59).atZone(zone).toInstant(), zone),
                        features);
        assertThat(audit.health(f.branch(), service.workspace(f.branch()), List.of()).overdue())
                .isTrue();
        service.save(f.branch(), maximumPlan(0, 2), "manager");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT enabled_since_at FROM rolling_branch_schedules WHERE"
                                        + " branch_id=?",
                                java.sql.Timestamp.class,
                                f.branch()))
                .isEqualTo(original);
        assertThat(audit.health(f.branch(), service.workspace(f.branch()), List.of()).overdue())
                .isTrue();
        service.save(f.branch(), plan(f, 1, false), "manager");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT enabled_since_at FROM rolling_branch_schedules WHERE"
                                        + " branch_id=?",
                                java.sql.Timestamp.class,
                                f.branch()))
                .isEqualTo(original);
        assertThat(audit.health(f.branch(), service.workspace(f.branch()), List.of()).overdue())
                .isFalse();
        service.save(f.branch(), maximumPlan(2, 2), "manager");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT enabled_since_at FROM rolling_branch_schedules WHERE"
                                        + " branch_id=?",
                                java.sql.Timestamp.class,
                                f.branch()))
                .isAfter(original);
        assertThat(audit.health(f.branch(), service.workspace(f.branch()), List.of()).overdue())
                .isFalse();
    }

    @Test
    void completionAfterMidnightSatisfiesDueRunEvenWithLegacyTransactionTimestamp() {
        var f = fixture();
        service.save(f.branch(), maximumPlan(-1, 2), "manager");
        var zone = ZoneId.of("Asia/Kolkata");
        var today = LocalDate.now(clock.withZone(zone));
        var midnight = today.atStartOfDay(zone).toInstant();
        jdbc.update(
                "UPDATE rolling_branch_schedules SET enabled_since_at=?,last_success_at=? WHERE"
                        + " branch_id=?",
                java.sql.Timestamp.from(midnight.minusSeconds(3600)),
                java.sql.Timestamp.from(midnight.minusSeconds(5)),
                f.branch());
        var began =
                new RollingAutomationDiagnostics(
                        jdbc, Clock.fixed(midnight.minusSeconds(5), zone), features);
        long id = began.start(f.branch(), "MANUAL", "manager");
        var finished =
                new RollingAutomationDiagnostics(
                        jdbc, Clock.fixed(midnight.plusSeconds(60), zone), features);
        finished.finish(
                id,
                f.branch(),
                "SUCCESS",
                65000,
                60000,
                5000,
                2,
                1,
                "Coverage completed after midnight");
        var audit =
                new RollingAutomationDiagnostics(
                        jdbc, Clock.fixed(midnight.plusSeconds(960), zone), features);
        assertThat(
                        audit.health(
                                        f.branch(),
                                        service.workspace(f.branch()),
                                        finished.runs(f.branch()))
                                .overdue())
                .isFalse();
        assertThat(
                        jdbc.queryForObject(
                                        "SELECT last_success_at FROM rolling_branch_schedules WHERE"
                                                + " branch_id=?",
                                        java.sql.Timestamp.class,
                                        f.branch())
                                .toInstant())
                .isBefore(midnight);
    }

    @Test
    void successfulRunRecordsWallClockCompletionInsteadOfTransactionStart() {
        var f = fixture();
        service.save(f.branch(), maximumPlan(-1, 2), "manager");
        var began =
                jdbc.queryForObject("SELECT current_timestamp", java.time.OffsetDateTime.class)
                        .toInstant();
        service.reconcile(f.branch(), "MANUAL", "manager");
        entityManager.flush();
        var completed =
                jdbc.queryForObject(
                                "SELECT last_success_at FROM rolling_branch_schedules WHERE"
                                        + " branch_id=?",
                                java.time.OffsetDateTime.class,
                                f.branch())
                        .toInstant();
        assertThat(completed).isAfter(began);
    }

    @Test
    void reviewedSavedVersionIsRequiredForCombinedGeneration() {
        var f = fixture();
        service.save(f.branch(), maximumPlan(-1, 2), "manager");
        service.save(f.branch(), maximumPlan(0, 3), "other-manager");
        assertThatThrownBy(() -> service.reconcile(f.branch(), "MANUAL", "manager", 0L))
                .hasMessageContaining("saved setup changed");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pickup_slots WHERE branch_id=?",
                                Integer.class,
                                f.branch()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=?",
                                Integer.class,
                                f.product()))
                .isZero();
    }

    @Test
    void matchingSavedVersionGeneratesStockAndReturnsActualStorefrontLimit() {
        var f = fixture();
        service.save(f.branch(), maximumPlan(-1, 2), "manager");
        service.reconcile(f.branch(), "MANUAL", "manager", 0L);
        entityManager.flush();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM inventory_daily_allocations WHERE"
                                        + " branch_product_id=?",
                                Integer.class,
                                f.product()))
                .isPositive();
        assertThat(service.inspect(f.branch()).storefrontBookingDays())
                .isEqualTo(features.getFutureOrderingDays());
    }

    @Autowired com.gokulsweets.restaurant.inventory.service.AdminInventoryService inventory;

    private RollingScheduleService.Plan readinessPlan(long version, boolean autoReady) {
        var base = maximumPlan(version, 2);
        return new RollingScheduleService.Plan(
                base.enabled(),
                base.openingTime(),
                base.closingTime(),
                base.slotMinutes(),
                base.capacity(),
                base.maintainDays(),
                base.weekdays(),
                base.version(),
                base.templates(),
                base.usePolicyMaximum(),
                autoReady);
    }

    private java.util.Map<String, Object> allocation(Fixture f, LocalDate date) {
        entityManager.flush();
        return jdbc.queryForMap(
                "SELECT * FROM inventory_daily_allocations WHERE branch_product_id=? AND"
                        + " service_date=?",
                f.product(),
                date);
    }

    @Test
    void automaticReadinessMakesReadyStockOrderableAndRepeatedRunsNeverRefillIt() {
        var f = fixture();
        jdbc.update(
                "UPDATE branch_inventory_policies SET ready_stock_required=TRUE WHERE id=?",
                f.policy());
        service.save(f.branch(), readinessPlan(-1, true), "manager");
        service.reconcile(f.branch());
        var date = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1);
        var after = allocation(f, date);
        assertThat(after.get("status")).isEqualTo("READY");
        assertThat((BigDecimal) after.get("ready_quantity")).isEqualByComparingTo("100");
        assertThat((BigDecimal) after.get("approved_quantity")).isEqualByComparingTo("100");
        assertThat((BigDecimal) after.get("safety_buffer_quantity")).isEqualByComparingTo("2");
        entityManager.clear();
        boolean enforcement = inventoryProperties.isEnforcementEnabled();
        boolean smart = features.isSmartAvailability();
        try {
            inventoryProperties.setEnforcementEnabled(true);
            features.setSmartAvailability(true);
            long product =
                    jdbc.queryForObject(
                            "SELECT product_id FROM branch_products WHERE id=?",
                            Long.class,
                            f.product());
            var preview =
                    cart.check(
                            f.branch(),
                            date,
                            1,
                            List.of(
                                    new com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest(
                                            product, 1, null)));
            assertThat(preview.dates().getFirst().available()).isTrue();
        } finally {
            inventoryProperties.setEnforcementEnabled(enforcement);
            features.setSmartAvailability(smart);
        }
        jdbc.update(
                "UPDATE inventory_daily_allocations SET committed_quantity=10,wasted_quantity=3"
                        + " WHERE id=?",
                after.get("id"));
        var protectedStock = allocation(f, date);
        for (int i = 0; i < 3; i++) service.reconcile(f.branch());
        assertThat(allocation(f, date)).isEqualTo(protectedStock);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM inventory_stock_transactions WHERE"
                                        + " allocation_id=? AND"
                                        + " transaction_type='READY_STOCK_RECORDED'",
                                Integer.class,
                                after.get("id")))
                .isEqualTo(1);
    }

    @Test
    void enablingReadinessUpgradesOnlyUntouchedLegacyAutomationUsingOriginalApprovedQuantity() {
        var f = fixture();
        jdbc.update(
                "UPDATE branch_inventory_policies SET ready_stock_required=TRUE WHERE id=?",
                f.policy());
        service.save(f.branch(), readinessPlan(-1, false), "manager");
        service.reconcile(f.branch());
        var date = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1);
        assertThat(allocation(f, date).get("status")).isEqualTo("APPROVED");
        entityManager.clear();
        jdbc.update(
                "UPDATE branch_inventory_policies SET maximum_daily_allocation=200 WHERE id=?",
                f.policy());
        service.save(f.branch(), readinessPlan(0, true), "manager");
        service.reconcile(f.branch());
        var after = allocation(f, date);
        assertThat(after.get("status")).isEqualTo("READY");
        assertThat((BigDecimal) after.get("ready_quantity")).isEqualByComparingTo("100");
        assertThat((BigDecimal) after.get("approved_quantity")).isEqualByComparingTo("100");
        service.reconcile(f.branch());
        assertThat(allocation(f, date)).isEqualTo(after);
    }

    @Test
    void readinessAutomationPreservesStaffCountsZeroReadingsAndManualApprovals() {
        var f = fixture();
        var zero = fixture();
        var manual = fixture();
        for (var item : List.of(zero, manual))
            jdbc.update(
                    "UPDATE branch_products SET branch_id=? WHERE id=?",
                    f.branch(),
                    item.product());
        for (var item : List.of(f, zero, manual))
            jdbc.update(
                    "UPDATE branch_inventory_policies SET ready_stock_required=TRUE WHERE id=?",
                    item.policy());
        service.save(f.branch(), readinessPlan(-1, false), "manager");
        service.reconcile(f.branch());
        var date = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1);
        entityManager.flush();
        inventory.updateReadiness(
                f.product(),
                date,
                new com.gokulsweets.restaurant.inventory.dto.AdminReadinessUpdateRequest(
                        com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus.READY,
                        new BigDecimal("30"),
                        null,
                        "Counted by staff"),
                "staff");
        inventory.updateReadiness(
                zero.product(),
                date,
                new com.gokulsweets.restaurant.inventory.dto.AdminReadinessUpdateRequest(
                        com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus
                                .DELAYED,
                        BigDecimal.ZERO,
                        null,
                        "Explicit zero"),
                "staff");
        for (var item : List.of(zero, manual))
            inventory.approveAllocation(
                    item.product(),
                    date,
                    new com.gokulsweets.restaurant.inventory.dto.AdminAllocationApprovalRequest(
                            new BigDecimal("100"), null, null, null, null, "Staff approval"),
                    "staff");
        var before = List.of(allocation(f, date), allocation(zero, date), allocation(manual, date));
        entityManager.clear();
        service.save(f.branch(), readinessPlan(0, true), "manager");
        for (int i = 0; i < 2; i++) service.reconcile(f.branch());
        assertThat(List.of(allocation(f, date), allocation(zero, date), allocation(manual, date)))
                .isEqualTo(before);
    }

    @Test
    void automaticReadinessDoesNotApproveManualReviewDrafts() {
        var f = fixture();
        jdbc.update(
                "UPDATE branch_inventory_policies SET ready_stock_required=TRUE WHERE id=?",
                f.policy());
        var base = readinessPlan(-1, true);
        service.save(
                f.branch(),
                new RollingScheduleService.Plan(
                        true,
                        base.openingTime(),
                        base.closingTime(),
                        30,
                        5,
                        2,
                        127,
                        -1,
                        List.of(
                                new RollingScheduleService.Template(
                                        f.policy(), new BigDecimal("80"), false)),
                        false,
                        true),
                "manager");
        service.reconcile(f.branch());
        var date = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1);
        assertThat(allocation(f, date).get("status")).isEqualTo("DRAFT");
        assertThat((BigDecimal) allocation(f, date).get("ready_quantity")).isZero();
    }

    @Test
    void omittedReadinessSettingDefaultsOnForTheAdminApi() throws Exception {
        var plan =
                mapper.readValue(
                        "{\"enabled\":false,\"openingTime\":\"10:00\",\"closingTime\":\"11:00\",\"slotMinutes\":30,\"capacity\":5,\"maintainDays\":14,\"weekdays\":127,\"version\":-1,\"templates\":[],\"usePolicyMaximum\":true}",
                        RollingScheduleService.Plan.class);
        assertThat(plan.autoReady()).isTrue();
        var f = fixture();
        service.save(f.branch(), plan, "manager");
        assertThat(service.workspace(f.branch()).plan().autoReady()).isTrue();
    }
}
