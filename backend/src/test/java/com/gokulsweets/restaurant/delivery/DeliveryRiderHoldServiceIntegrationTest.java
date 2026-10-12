package com.gokulsweets.restaurant.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@Transactional
class DeliveryRiderHoldServiceIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BranchRepository branches;
    private static final Instant NOW = Instant.parse("2026-09-27T18:35:00Z");
    private static final Clock IST = Clock.fixed(NOW, ZoneId.of("Asia/Kolkata"));

    @Test
    void retriesNeverOversellAndReleaseIsIdempotent() {
        var flags = flags();
        var zones = new DeliveryZoneService(flags, jdbc, branches);
        var stock = mock(DeliveryStockCheck.class);
        when(stock.check(anyLong(), any(), anyList()))
                .thenReturn(new DeliveryStockCheck.Check(true, null));
        var boundaries = mock(DeliveryBoundaryService.class);
        var capacity = new DeliveryCapacityService(flags, zones, jdbc, IST, stock, boundaries);
        var holds = new DeliveryRiderHoldService(flags, capacity, jdbc, IST);
        String random = UUID.randomUUID().toString().substring(0, 8);
        Long branch =
                jdbc.queryForObject(
                        "INSERT INTO branches (code, name) VALUES (?, ?) RETURNING id",
                        Long.class,
                        "DWH-" + random,
                        "Delivery " + random);
        Long category =
                jdbc.queryForObject(
                        "INSERT INTO categories (code, name) VALUES (?, ?) RETURNING id",
                        Long.class,
                        "DWH-C-" + random,
                        "Category " + random);
        Long product =
                jdbc.queryForObject(
                        "INSERT INTO products (code, category_id, name, base_price) VALUES (?, ?,"
                                + " ?, 100) RETURNING id",
                        Long.class,
                        "DWH-P-" + random,
                        category,
                        "Product " + random);
        jdbc.update(
                "INSERT INTO branch_products (branch_id, product_id) VALUES (?, ?)",
                branch,
                product);
        var zone =
                zones.configure(
                        branch,
                        new DeliveryZoneService.Configuration(
                                "Hazratganj",
                                "226001",
                                LocalTime.of(10, 0),
                                LocalTime.of(20, 0),
                                true,
                                false,
                                List.of(product)));
        var window =
                capacity.configure(
                        branch,
                        zone.id(),
                        new DeliveryCapacityService.WindowConfiguration(
                                LocalDate.of(2026, 9, 28),
                                LocalTime.of(10, 0),
                                LocalTime.of(11, 0),
                                1,
                                false));
        when(boundaries.contains(zone.id(), 26.85, 80.94)).thenReturn(true);
        var request =
                new DeliveryCapacityService.QuoteRequest(
                        branch,
                        "Hazratganj",
                        "226001",
                        LocalDate.of(2026, 9, 28),
                        List.of(new CreateOrderItemRequest(product, 1, null)),
                        26.85,
                        80.94);
        String fingerprint = "a".repeat(64);

        assertThat(holds.hold("one-" + random, fingerprint, request, window.id())).isTrue();
        assertThat(holds.hold("one-" + random, fingerprint, request, window.id())).isTrue();
        assertThat(holds.hold("one-" + random, "b".repeat(64), request, window.id())).isFalse();
        assertThat(holds.hold("two-" + random, fingerprint, request, window.id())).isFalse();
        assertThat(reserved(window.id())).isEqualTo(1);
        assertThat(holds.commit("one-" + random)).isTrue();
        assertThat(holds.expire("one-" + random)).isFalse();
        assertThat(holds.release("one-" + random)).isTrue();
        assertThat(holds.release("one-" + random)).isTrue();
        assertThat(reserved(window.id())).isZero();
        assertThat(holds.commit("one-" + random)).isFalse();
        assertThat(holds.hold("one-" + random, fingerprint, request, window.id())).isFalse();
        assertThat(holds.hold("two-" + random, fingerprint, request, window.id())).isTrue();
        flags.setDeliveryRiderHolds(false);
        assertThat(holds.release("two-" + random)).isTrue();
        assertThat(reserved(window.id())).isZero();
    }

    private int reserved(long id) {
        return jdbc.queryForObject(
                "SELECT reserved_count FROM delivery_capacity_windows WHERE id = ?",
                Integer.class,
                id);
    }

    private static EnhancementProperties flags() {
        var flags = new EnhancementProperties();
        flags.setCustomerOtpIdentity(true);
        flags.setCustomerConsentControls(true);
        flags.setDeliveryLocalityCheck(true);
        flags.setDeliveryZones(true);
        flags.setDeliveryCapacity(true);
        flags.setDeliveryAddressBoundaries(true);
        flags.setDeliveryRiderHolds(true);
        return flags;
    }
}
