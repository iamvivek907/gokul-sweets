package com.gokulsweets.restaurant.order;

import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.service.OrderQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DeliveryCustomerOrderViewIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderQueryService orders;

    @Test
    void deliveryOrderShowsWindowAndAddressWithoutPickupFields() {
        String key = UUID.randomUUID().toString().substring(0, 8);
        Long branch = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, ?) RETURNING id",
                Long.class, "DCV-" + key, "Delivery " + key);
        Long zone = jdbc.queryForObject("""
                INSERT INTO delivery_zones(branch_id, locality_key, postal_code, opens_at, closes_at, active, rider_paused)
                VALUES (?, 'hazratganj', '226001', '10:00', '20:00', true, false) RETURNING id
                """, Long.class, branch);
        Long window = jdbc.queryForObject("""
                INSERT INTO delivery_capacity_windows(zone_id, service_date, starts_at, ends_at, rider_capacity, reserved_count)
                VALUES (?, ?, ?, ?, 1, 1) RETURNING id
                """, Long.class, zone, LocalDate.of(2026, 10, 1), LocalTime.of(11, 0), LocalTime.of(12, 0));
        String hold = "view-" + key;
        jdbc.update("""
                INSERT INTO delivery_rider_holds(hold_key, window_id, request_fingerprint, state, expires_at)
                VALUES (?, ?, ?, 'COMMITTED', CURRENT_TIMESTAMP + INTERVAL '1 hour')
                """, hold, window, "a".repeat(64));
        String orderNumber = ("DCV-" + key).toUpperCase(java.util.Locale.ROOT);
        jdbc.update("""
                INSERT INTO orders(order_number, branch_id, customer_name, customer_phone, order_status,
                                   reservation_expires_at, fulfillment_type, delivery_window_id, delivery_hold_key,
                                   delivery_address_line, delivery_locality, delivery_postal_code)
                VALUES (?, ?, 'Customer', '9999999999', 'PENDING_PAYMENT', CURRENT_TIMESTAMP + INTERVAL '15 minutes',
                        'DELIVERY', ?, ?, '12 Main Road', 'Hazratganj', '226001')
                """, orderNumber, branch, window, hold);

        jdbc.update("UPDATE orders SET subtotal=100, delivery_fee=30, total_amount=110 WHERE order_number=?", orderNumber);
        var detail = orders.getCustomerOrder(orderNumber);
        assertThat(detail.deliveryFee()).isEqualByComparingTo("30");
        assertThat(detail.totalAmount()).isEqualByComparingTo("110");
        assertThat(detail.fulfillmentType()).isEqualTo(FulfillmentType.DELIVERY);
        assertThat(detail.pickupDate()).isNull();
        assertThat(detail.pickupType()).isNull();
        assertThat(detail.deliveryDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(detail.deliveryStartTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(detail.deliveryAddressLine()).isEqualTo("12 Main Road");
        var summary = orders.getCustomerOrderHistory(List.of(orderNumber)).getFirst();
        assertThat(summary.branchId()).isEqualTo(branch);
        assertThat(summary.fulfillmentType()).isEqualTo(FulfillmentType.DELIVERY);
        assertThat(summary.deliveryEndTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(summary.pickupDate()).isNull();
    }
}
