package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** Reads a compact committed fingerprint; never accepts an order or reserves stock. */
@Service
@RequiredArgsConstructor
public class MenuInventoryVersion {
    private final JdbcTemplate jdbc;
    private final EnhancementProperties features;
    private final InventoryProperties inventory;

    /** Reads branch/date stock and capacity tokens together with every validation dependency. */
    public String read(long branchId, LocalDate date) {
        long started = MethodTiming.start(MenuInventoryVersion.class, "read(long,LocalDate)");
        try {
            String token =
                    jdbc.queryForObject(
                            """
SELECT r.token::text || ':' ||
  COALESCE((SELECT md5(string_agg(a.availability_token::text, ',' ORDER BY a.id))
    FROM inventory_daily_allocations a
    JOIN branch_products bp ON bp.id=a.branch_product_id
    WHERE bp.branch_id=? AND a.service_date=?), 'empty') || ':' ||
  COALESCE((SELECT md5(string_agg(s.availability_token::text, ',' ORDER BY s.id))
    FROM pickup_slots s WHERE s.branch_id=? AND s.slot_date=?), 'empty')
FROM menu_catalog_revision r WHERE r.id=true
""",
                            String.class,
                            branchId,
                            date,
                            branchId,
                            date);
            return token
                    + ":"
                    + features.isSmartAvailability()
                    + ":"
                    + features.getFutureOrderingDays()
                    + ":"
                    + features.isPlannedPickupProduction()
                    + ":"
                    + inventory.isEnforcementEnabled();
        } finally {
            MethodTiming.finish(started, MenuInventoryVersion.class, "read(long,LocalDate)");
        }
    }
}
