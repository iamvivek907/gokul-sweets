package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.config.EnhancementProperties;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class DeliveryCapacityServiceIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BranchRepository branches;
    private static final Clock IST = Clock.fixed(Instant.parse("2026-09-27T18:35:00Z"), ZoneId.of("Asia/Kolkata"));
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @Test
    void exactAreaProductPauseAndCapacityDetermineProvisionalWindowsOnly() {
        var flags = flags();
        var zones = new DeliveryZoneService(flags, jdbc, branches);
        var service = new DeliveryCapacityService(flags, zones, jdbc, IST);
        var key = UUID.randomUUID().toString().substring(0, 8);
        Long branch = jdbc.queryForObject("INSERT INTO branches (code, name) VALUES (?, ?) RETURNING id",
                Long.class, "DWC-" + key, "Delivery " + key);
        Long category = jdbc.queryForObject("INSERT INTO categories (code, name) VALUES (?, ?) RETURNING id",
                Long.class, "DWC-C-" + key, "Category " + key);
        Long product = jdbc.queryForObject("INSERT INTO products (code, category_id, name, base_price) VALUES (?, ?, ?, 100) RETURNING id",
                Long.class, "DWC-P-" + key, category, "Product " + key);
        jdbc.update("INSERT INTO branch_products (branch_id, product_id) VALUES (?, ?)", branch, product);
        var zoneRequest = new DeliveryZoneService.Configuration("Hazratganj", "226001", LocalTime.of(10, 0),
                LocalTime.of(20, 0), true, false, List.of(product));
        var zone = zones.configure(branch, zoneRequest);
        var window = service.configure(branch, zone.id(), new DeliveryCapacityService.WindowConfiguration(
                TODAY, LocalTime.of(10, 0), LocalTime.of(11, 0), 1, false));
        var quote = new DeliveryCapacityService.QuoteRequest(branch, " HAZRATGANJ ", "226001", TODAY, List.of(product));

        assertThat(service.quote(quote).provisionalWindows()).singleElement()
                .extracting(DeliveryCapacityService.Window::id).isEqualTo(window.id());
        assertThat(service.quote(quote).orderable()).isFalse();
        assertThat(service.quote(new DeliveryCapacityService.QuoteRequest(branch, "Hazratganj", "226002", TODAY,
                List.of(product))).provisionalWindows()).isEmpty();
        assertThat(service.quote(new DeliveryCapacityService.QuoteRequest(branch, "Nearby hamlet", "226001", TODAY,
                List.of(product))).provisionalWindows()).isEmpty();

        jdbc.update("UPDATE delivery_capacity_windows SET reserved_count = rider_capacity WHERE id = ?", window.id());
        assertThat(service.quote(quote).provisionalWindows()).isEmpty();
        assertThatThrownBy(() -> service.configure(branch, zone.id(), new DeliveryCapacityService.WindowConfiguration(
                TODAY, LocalTime.of(10, 0), LocalTime.of(11, 0), 0, false))).isInstanceOf(IllegalArgumentException.class);
        jdbc.update("UPDATE delivery_capacity_windows SET reserved_count = 0, paused = true WHERE id = ?", window.id());
        assertThat(service.quote(quote).provisionalWindows()).isEmpty();
        jdbc.update("UPDATE delivery_capacity_windows SET paused = false WHERE id = ?", window.id());
        zones.configure(branch, new DeliveryZoneService.Configuration("Hazratganj", "226001", LocalTime.of(10, 0),
                LocalTime.of(20, 0), true, true, List.of(product)));
        assertThat(service.quote(quote).provisionalWindows()).isEmpty();
    }

    @Test
    void missingFlagsAndIstBoundaryFailClosed() {
        var flags = flags();
        var zones = new DeliveryZoneService(flags, jdbc, branches);
        var service = new DeliveryCapacityService(flags, zones, jdbc, IST);
        var request = new DeliveryCapacityService.QuoteRequest(1, "Hazratganj", "226001", TODAY.minusDays(1), List.of(1L));
        assertThat(service.quote(request).provisionalWindows()).isEmpty();
        flags.setDeliveryCapacity(false);
        assertThatThrownBy(() -> service.quote(request)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    private static EnhancementProperties flags() {
        var flags = new EnhancementProperties();
        flags.setCustomerOtpIdentity(true);
        flags.setCustomerConsentControls(true);
        flags.setDeliveryLocalityCheck(true);
        flags.setDeliveryZones(true);
        flags.setDeliveryCapacity(true);
        return flags;
    }
}
