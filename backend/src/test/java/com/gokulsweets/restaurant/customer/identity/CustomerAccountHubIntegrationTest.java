package com.gokulsweets.restaurant.customer.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CustomerAccountHubIntegrationTest {
    @Autowired CustomerAccountHub hub;
    @Autowired JdbcTemplate jdbc;

    @Test
    void savedChoicesRemainScopedToSubjectAndEnvironmentAndDeletedAddressCannotBeRead() {
        var subject = UUID.randomUUID();
        var other = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO verified_customer_subjects(id, environment, verified_phone)
                VALUES (?, 'DEV', '+919876543210')
                """, subject);
        hub.savePreferences("DEV", subject, new CustomerAccountHub.Preferences("No nuts", null));
        var address = hub.addAddress("DEV", subject,
                new CustomerAccountHub.AddressInput("Home", "Main Road", "Tamkuhi", "274407"));
        assertThat(hub.snapshot("DEV", subject).preferences().dietaryNotes()).isEqualTo("No nuts");
        assertThat(hub.snapshot("DEV", subject).addresses()).hasSize(1);
        assertThat(hub.snapshot("DEV", other).addresses()).isEmpty();
        assertThat(hub.snapshot("PROD", subject).preferences().dietaryNotes()).isNull();
        assertThatThrownBy(() -> hub.deleteAddress("DEV", other, address.id()))
                .isInstanceOf(ResponseStatusException.class);
        hub.deleteAddress("DEV", subject, address.id());
        assertThat(hub.snapshot("DEV", subject).addresses()).isEmpty();
    }

    @Test
    void tickCountIncludesPaidOwnedOrdersButNotGuestUnpaidOrCancelled() {
        var subject = UUID.randomUUID();
        var branch = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, 'Test branch') RETURNING id",
                Long.class, "HUB-" + UUID.randomUUID().toString().substring(0, 8));
        var slot = jdbc.queryForObject("""
                INSERT INTO pickup_slots(branch_id, slot_date, start_time, end_time, capacity)
                VALUES (?, CURRENT_DATE, '10:00', '10:30', 2) RETURNING id
                """, Long.class, branch);
        var paid = order(branch, slot, "CONFIRMED");
        var unpaid = order(branch, slot, "PENDING_PAYMENT");
        var cancelled = order(branch, slot, "CANCELLED");
        var guest = order(branch, slot, "CONFIRMED");
        for (var id : new Long[]{paid, unpaid, cancelled}) jdbc.update("""
                INSERT INTO verified_order_ownership(order_id, environment, verified_subject_id)
                VALUES (?, 'DEV', ?)
                """, id, subject);
        for (var id : new Long[]{paid, cancelled, guest}) jdbc.update("""
                INSERT INTO payments(order_id, provider, amount, payment_status)
                VALUES (?, 'PHONEPE', 100, 'PAID')
                """, id);
        assertThat(hub.snapshot("DEV", subject).paidOrders()).isEqualTo(1);
        assertThat(hub.snapshot("DEV", UUID.randomUUID()).paidOrders()).isZero();
        assertThat(hub.snapshot("PROD", subject).paidOrders()).isZero();
        jdbc.update("UPDATE orders SET order_status = 'CANCELLED' WHERE id = ?", paid);
        assertThat(hub.snapshot("DEV", subject).paidOrders()).isZero();
    }

    private Long order(Long branch, Long slot, String status) {
        return jdbc.queryForObject("""
                INSERT INTO orders(order_number, branch_id, pickup_slot_id, customer_name,
                    customer_phone, pickup_type, order_status, reservation_expires_at)
                VALUES (?, ?, ?, 'Customer', '9876543210', 'NORMAL', ?, CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, "GKS-HUB-" + UUID.randomUUID(), branch, slot, status);
    }
}
