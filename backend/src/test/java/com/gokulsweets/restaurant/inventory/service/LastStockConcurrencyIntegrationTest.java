package com.gokulsweets.restaurant.inventory.service;

import static org.assertj.core.api.Assertions.*;

import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.model.CreateInventoryHoldCommand;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest
class LastStockConcurrencyIntegrationTest {
    @Autowired InventoryReservationService reservations;
    @Autowired JdbcTemplate jdbc;

    @Test
    void onlyOneOfTenCustomersCanHoldLast500GramsAndReleaseRestoresStock() throws Exception {
        String code = "STOCK-" + UUID.randomUUID();
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        long branch =
                jdbc.queryForObject(
                        "INSERT INTO branches(code,name) VALUES(?,'Concurrency') RETURNING id",
                        Long.class,
                        code);
        long category =
                jdbc.queryForObject(
                        "INSERT INTO categories(code,name) VALUES(?,'Sweets') RETURNING id",
                        Long.class,
                        code);
        long product =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " products(code,name,category_id,base_price,sale_mode,minimum_weight_grams,weight_step_grams)"
                            + " VALUES(?,'Last sweet',?,400,'WEIGHT',250,250) RETURNING id",
                        Long.class,
                        code,
                        category);
        long bp =
                jdbc.queryForObject(
                        "INSERT INTO branch_products(branch_id,product_id) VALUES(?,?) RETURNING"
                                + " id",
                        Long.class,
                        branch,
                        product);
        jdbc.update(
                "INSERT INTO"
                    + " branch_inventory_policies(branch_product_id,control_mode,inventory_unit,online_enabled,booking_horizon_days,created_at,updated_at)"
                    + " VALUES(?,'DAILY_PRODUCTION','GRAM',true,30,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                bp);
        jdbc.update(
                "INSERT INTO"
                    + " inventory_daily_allocations(branch_product_id,service_date,status,inventory_unit,approved_quantity,created_at,updated_at)"
                    + " VALUES(?,?,'APPROVED','GRAM',500,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                bp,
                date);
        var start = new CountDownLatch(1);
        var results = new ArrayList<Future<String>>();
        try (var executor = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                String key = code + "-" + i;
                results.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    try {
                                        reservations.createHold(
                                                new CreateInventoryHoldCommand(
                                                        key,
                                                        null,
                                                        bp,
                                                        date,
                                                        new BigDecimal("500")));
                                        return key;
                                    } catch (InventoryConflictException expected) {
                                        return null;
                                    }
                                }));
            }
            start.countDown();
            var winners = new ArrayList<String>();
            for (var result : results) {
                var winner = result.get(20, TimeUnit.SECONDS);
                if (winner != null) winners.add(winner);
            }
            assertThat(winners).hasSize(1);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT held_quantity FROM inventory_daily_allocations WHERE"
                                            + " branch_product_id=? AND service_date=?",
                                    BigDecimal.class,
                                    bp,
                                    date))
                    .isEqualByComparingTo("500");
            var retry =
                    reservations.createHold(
                            new CreateInventoryHoldCommand(
                                    winners.getFirst(), null, bp, date, new BigDecimal("500")));
            assertThat(retry.getQuantity()).isEqualByComparingTo("500");
            reservations.releaseHold(winners.getFirst(), "Customer cancelled");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT held_quantity FROM inventory_daily_allocations WHERE"
                                            + " branch_product_id=? AND service_date=?",
                                    BigDecimal.class,
                                    bp,
                                    date))
                    .isEqualByComparingTo("0");
            reservations.createHold(
                    new CreateInventoryHoldCommand(
                            code + "-replacement", null, bp, date, new BigDecimal("500")));
        }
    }
}
