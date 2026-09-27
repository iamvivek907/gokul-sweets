package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class DeliveryZoneServiceIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BranchRepository branches;

    @Test
    void exactAreaRequiresActiveBranchRiderAndEligibleProductAndNeverPromisesCheckout() {
        var flags = new EnhancementProperties();
        var service = new DeliveryZoneService(flags, jdbc, branches);
        var unique = UUID.randomUUID().toString().substring(0, 8);
        Long branch = jdbc.queryForObject("INSERT INTO branches (code, name) VALUES (?, ?) RETURNING id",
                Long.class, "DEL-" + unique, "Delivery Test " + unique);
        Long category = jdbc.queryForObject("INSERT INTO categories (name) VALUES (?) RETURNING id", Long.class, "Test " + unique);
        Long product = jdbc.queryForObject("INSERT INTO products (category_id, name, base_price) VALUES (?, ?, 100) RETURNING id",
                Long.class, category, "Test product " + unique);
        jdbc.update("INSERT INTO branch_products (branch_id, product_id) VALUES (?, ?)", branch, product);
        var request = new DeliveryZoneService.Configuration(" Hazratganj ", "226001",
                LocalTime.of(10, 0), LocalTime.of(20, 0), true, false, List.of(product));

        assertThatThrownBy(() -> service.configure(branch, request)).isInstanceOf(IllegalStateException.class);
        enable(flags);
        var saved = service.configure(branch, request);
        assertThat(saved.locality()).isEqualTo("hazratganj");
        assertThat(service.list(branch)).singleElement().extracting(DeliveryZoneService.Zone::id).isEqualTo(saved.id());
        assertThat(service.coverage(" HAZRATGANJ ", "226001").configuredAreas()).hasSize(1);
        assertThat(service.coverage("HAZRATGANJ", "226001").orderable()).isFalse();
        assertThat(service.coverage("other", "226001").configuredAreas()).isEmpty();
        assertThat(service.coverage("hazratganj", "226002").configuredAreas()).isEmpty();

        service.configure(branch, new DeliveryZoneService.Configuration("hazratganj", "226001",
                LocalTime.of(10, 0), LocalTime.of(20, 0), true, true, List.of(product)));
        assertThat(service.coverage("hazratganj", "226001").configuredAreas()).isEmpty();
        service.configure(branch, request);
        jdbc.update("UPDATE branch_products SET available = false WHERE branch_id = ?", branch);
        assertThat(service.coverage("hazratganj", "226001").configuredAreas()).isEmpty();
        jdbc.update("UPDATE branch_products SET available = true WHERE branch_id = ?", branch);
        jdbc.update("UPDATE branches SET active = false WHERE id = ?", branch);
        assertThat(service.coverage("hazratganj", "226001").configuredAreas()).isEmpty();
    }

    @Test
    void rejectsInvertedHoursAndProductsNotAvailableAtBranch() {
        var flags = new EnhancementProperties();
        enable(flags);
        var service = new DeliveryZoneService(flags, jdbc, branches);
        Long branch = jdbc.queryForObject("INSERT INTO branches (code, name) VALUES (?, 'Zone test') RETURNING id",
                Long.class, "ZONE-" + UUID.randomUUID().toString().substring(0, 8));
        assertThatThrownBy(() -> service.configure(branch, new DeliveryZoneService.Configuration("Area", "226001",
                LocalTime.NOON, LocalTime.NOON, true, false, List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.configure(branch, new DeliveryZoneService.Configuration("Area", "226001",
                LocalTime.of(10, 0), LocalTime.of(18, 0), true, false, List.of(123456789L))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void enable(EnhancementProperties flags) {
        flags.setDeliveryZones(true);
        flags.setDeliveryLocalityCheck(true);
        flags.setCustomerConsentControls(true);
        flags.setCustomerOtpIdentity(true);
    }
}
