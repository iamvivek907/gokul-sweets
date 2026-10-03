package com.gokulsweets.restaurant.order;

import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.OrderQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CustomerOrderNumberIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderRepository repository;
    @Autowired OrderQueryService customerOrders;
    @Autowired PlatformTransactionManager transactions;

    private long branch() {
        return jdbc.queryForObject("INSERT INTO branches(code,name) VALUES (?,'Number test') RETURNING id",
                Long.class, "NUM-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private long pending(long branch) {
        return jdbc.queryForObject("""
                INSERT INTO orders(order_number,branch_id,customer_name,customer_phone,order_status,reservation_expires_at)
                VALUES (?,?,'Number test','9876543210','PENDING_PAYMENT',CURRENT_TIMESTAMP+INTERVAL '15 minutes') RETURNING id
                """, Long.class, "NUM-" + UUID.randomUUID(), branch);
    }

    private Long number(long id) {
        return jdbc.queryForObject("SELECT customer_order_number FROM orders WHERE id=?", Long.class, id);
    }

    @Test
    void generatedNumberIsRetrievedByHibernateAndExposedAlongsideOpaqueReference() {
        long id = pending(branch());
        assertThat(number(id)).isNull();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var order = repository.findById(id).orElseThrow();
            order.setOrderStatus(OrderStatus.CONFIRMED);
            repository.saveAndFlush(order);
            assertThat(order.getCustomerOrderNumber()).isPositive();
            var detail = customerOrders.getCustomerOrder(order.getOrderNumber());
            assertThat(detail.orderNumber()).isEqualTo(order.getOrderNumber());
            assertThat(detail.customerOrderNumber()).isEqualTo(order.getCustomerOrderNumber());
            assertThat(customerOrders.getCustomerOrderHistory(List.of(order.getOrderNumber())).getFirst().customerOrderNumber())
                    .isEqualTo(order.getCustomerOrderNumber());
        });
    }

    @Test
    void simultaneousConfirmationsAcrossBranchesGetDistinctNumbers() throws Exception {
        long first = branch(), second = branch();
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 100; i++) ids.add(pending(i % 2 == 0 ? first : second));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(12)) {
            var results = ids.stream().map(id -> executor.submit(() -> {
                start.await();
                jdbc.update("UPDATE orders SET order_status='CONFIRMED' WHERE id=?", id);
                return number(id);
            })).toList();
            start.countDown();
            var numbers = new HashSet<Long>();
            for (var result : results) assertThat(numbers.add(result.get(30, TimeUnit.SECONDS))).isTrue();
            assertThat(numbers).hasSize(100).allMatch(value -> value != null && value > 0);
        }
    }

    @Test
    void duplicateConfirmationsNeverReassignAndCancellationKeepsTheNumber() throws Exception {
        long id = pending(branch());
        jdbc.update("UPDATE orders SET order_status='CONFIRMED' WHERE id=?", id);
        Long assigned = number(id);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 50; i++) futures.add(executor.submit(() ->
                    jdbc.update("UPDATE orders SET order_status='CONFIRMED' WHERE id=?", id)));
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
        }
        jdbc.update("UPDATE orders SET order_status='CANCELLED',customer_order_number=999999999 WHERE id=?", id);
        assertThat(number(id)).isEqualTo(assigned);
        jdbc.update("UPDATE orders SET customer_order_number=NULL WHERE id=?", id);
        assertThat(number(id)).isEqualTo(assigned);
    }

    @Test
    void failedPaymentsHaveNoPublicNumberAndRolledBackConfirmationsDoNotReuseNumbers() {
        long id = pending(branch());
        jdbc.update("UPDATE orders SET order_status='PAYMENT_FAILED',customer_order_number=1 WHERE id=?", id);
        assertThat(number(id)).isNull();
        var reserved = new long[1];
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("UPDATE orders SET order_status='CONFIRMED' WHERE id=?", id);
            reserved[0] = number(id);
            tx.setRollbackOnly();
        });
        assertThat(number(id)).isNull();
        long another = pending(branch());
        jdbc.update("UPDATE orders SET order_status='CONFIRMED' WHERE id=?", another);
        assertThat(number(another)).isGreaterThan(reserved[0]);
    }
}
