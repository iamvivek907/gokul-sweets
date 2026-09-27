package com.gokulsweets.restaurant.delivery;

import tools.jackson.databind.ObjectMapper;
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

@SpringBootTest
@Transactional
class DeliveryBoundaryIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BranchRepository branches;
    @Autowired ObjectMapper mapper;

    @Test
    void onlyReviewedBoundaryOfMatchingBranchAndZoneAllowsCustomerPin() {
        var flags = new EnhancementProperties();
        flags.setCustomerOtpIdentity(true); flags.setCustomerConsentControls(true);
        flags.setDeliveryLocalityCheck(true); flags.setDeliveryZones(true);
        flags.setDeliveryCapacity(true); flags.setDeliveryAddressBoundaries(true);
        var zones = new DeliveryZoneService(flags, jdbc, branches);
        var boundaries = new DeliveryBoundaryService(flags, zones, jdbc, mapper);
        Long branch = jdbc.queryForObject("INSERT INTO branches (code, name) VALUES (?, 'Boundary Test') RETURNING id",
                Long.class, "BOUND-" + UUID.randomUUID().toString().substring(0, 8));
        var zone = zones.configure(branch, new DeliveryZoneService.Configuration("Hazratganj", "226001",
                LocalTime.of(10, 0), LocalTime.of(20, 0), true, false, List.of()));
        var vertices = List.of(new DeliveryBoundaryService.Point(26.84, 80.93),
                new DeliveryBoundaryService.Point(26.84, 80.95),
                new DeliveryBoundaryService.Point(26.86, 80.95),
                new DeliveryBoundaryService.Point(26.86, 80.93));
        boundaries.configure(branch, zone.id(), new DeliveryBoundaryService.Boundary(vertices, false));
        assertThat(boundaries.contains(zone.id(), 26.85, 80.94)).isFalse();
        boundaries.configure(branch, zone.id(), new DeliveryBoundaryService.Boundary(vertices, true));
        assertThat(boundaries.contains(zone.id(), 26.85, 80.94)).isTrue();
        assertThat(boundaries.contains(zone.id(), 26.85, 80.951)).isFalse();
        assertThat(boundaries.contains(zone.id(), Double.NaN, 80.94)).isFalse();
        assertThat(boundaries.get(branch, zone.id()).vertices()).hasSize(4);
        flags.setDeliveryAddressBoundaries(false);
        assertThat(boundaries.contains(zone.id(), 26.85, 80.94)).isFalse();
    }
}
