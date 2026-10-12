package com.gokulsweets.restaurant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@SpringBootTest
@Transactional
class OrderFulfillmentShapeIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void existingPickupInsertGetsPickupFulfillmentDefault() {
        String key = UUID.randomUUID().toString().substring(0, 8);
        Long branchId =
                jdbc.queryForObject(
                        "INSERT INTO branches(code, name) VALUES (?, ?) RETURNING id",
                        Long.class,
                        "FUL-" + key,
                        "Fulfillment " + key);
        Long slotId =
                jdbc.queryForObject(
                        """
INSERT INTO pickup_slots(branch_id, slot_date, start_time, end_time, capacity)
VALUES (?, ?, ?, ?, 1) RETURNING id
""",
                        Long.class,
                        branchId,
                        LocalDate.of(2026, 10, 1),
                        LocalTime.of(10, 0),
                        LocalTime.of(11, 0));
        Long orderId =
                jdbc.queryForObject(
                        """
INSERT INTO orders(order_number, branch_id, pickup_slot_id, customer_name, customer_phone,
                   pickup_type, order_status, reservation_expires_at)
VALUES (?, ?, ?, 'Customer', '9999999999', 'NORMAL', 'PENDING_PAYMENT', CURRENT_TIMESTAMP)
RETURNING id
""",
                        Long.class,
                        "FUL-" + key,
                        branchId,
                        slotId);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT fulfillment_type FROM orders WHERE id = ?",
                                String.class,
                                orderId))
                .isEqualTo("PICKUP");
    }

    @Test
    void pickupCannotLoseItsSlotDespiteNullableDeliveryShape() {
        String key = UUID.randomUUID().toString().substring(0, 8);
        Long branchId =
                jdbc.queryForObject(
                        "INSERT INTO branches(code, name) VALUES (?, ?) RETURNING id",
                        Long.class,
                        "FUL-" + key,
                        "Fulfillment " + key);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        """
INSERT INTO orders(order_number, branch_id, customer_name, customer_phone,
                   pickup_type, order_status, reservation_expires_at)
VALUES (?, ?, 'Customer', '9999999999', 'NORMAL', 'PENDING_PAYMENT', CURRENT_TIMESTAMP)
""",
                                        "FUL-" + key,
                                        branchId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
