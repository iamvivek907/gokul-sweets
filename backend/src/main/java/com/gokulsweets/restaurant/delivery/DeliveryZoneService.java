package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DeliveryZoneService {
    private final EnhancementProperties flags;
    private final JdbcTemplate jdbc;
    private final BranchRepository branches;

    public boolean enabled() {
        return flags.isDeliveryZones() && flags.isDeliveryLocalityCheck()
                && flags.isCustomerConsentControls() && flags.isCustomerOtpIdentity();
    }

    @Transactional
    public Zone configure(long branchId, Configuration input) {
        if (!enabled()) throw new IllegalStateException("Delivery configuration is disabled.");
        if (branches.findById(branchId).isEmpty()) throw new IllegalArgumentException("Unknown branch.");
        if (!input.closesAt().isAfter(input.opensAt())) throw new IllegalArgumentException("Closing time must follow opening time.");
        var productIds = input.productIds().stream().distinct().toList();
        if (productIds.size() != input.productIds().size()) throw new IllegalArgumentException("Duplicate product IDs.");
        if (!productIds.isEmpty()) {
            Long count = jdbc.queryForObject("""
                    SELECT count(*) FROM branch_products bp JOIN products p ON p.id = bp.product_id
                    JOIN categories c ON c.id = p.category_id
                    WHERE bp.branch_id = ? AND bp.product_id IN (%s) AND bp.available AND p.active AND c.active
                    """.formatted(String.join(",", Collections.nCopies(productIds.size(), "?"))), Long.class,
                    concat(branchId, productIds));
            if (count == null || count != productIds.size()) throw new IllegalArgumentException("Products must be active on this branch menu.");
        }
        String locality = canonical(input.locality());
        Long zoneId = jdbc.queryForObject("""
                INSERT INTO delivery_zones (branch_id, locality_key, postal_code, opens_at, closes_at, active, rider_paused)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (branch_id, locality_key, postal_code) DO UPDATE SET
                    opens_at = EXCLUDED.opens_at, closes_at = EXCLUDED.closes_at,
                    active = EXCLUDED.active, rider_paused = EXCLUDED.rider_paused,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING id
                """, Long.class, branchId, locality, input.postalCode(),
                Time.valueOf(input.opensAt()), Time.valueOf(input.closesAt()), input.active(), input.riderPaused());
        jdbc.update("DELETE FROM delivery_zone_products WHERE zone_id = ?", zoneId);
        for (Long productId : productIds) jdbc.update("INSERT INTO delivery_zone_products (zone_id, product_id) VALUES (?, ?)", zoneId, productId);
        return new Zone(zoneId, branchId, locality, input.postalCode(), input.opensAt(), input.closesAt(),
                input.active(), input.riderPaused(), productIds);
    }

    @Transactional(readOnly = true)
    public List<Zone> list(long branchId) {
        if (!enabled()) throw new IllegalStateException("Delivery configuration is disabled.");
        if (branches.findById(branchId).isEmpty()) throw new IllegalArgumentException("Unknown branch.");
        return jdbc.query("""
                SELECT id, locality_key, postal_code, opens_at, closes_at, active, rider_paused
                FROM delivery_zones WHERE branch_id = ? ORDER BY locality_key, postal_code
                """, (rs, row) -> new Zone(rs.getLong("id"), branchId, rs.getString("locality_key"),
                rs.getString("postal_code"), rs.getTime("opens_at").toLocalTime(),
                rs.getTime("closes_at").toLocalTime(), rs.getBoolean("active"),
                rs.getBoolean("rider_paused"), jdbc.queryForList(
                "SELECT product_id FROM delivery_zone_products WHERE zone_id = ? ORDER BY product_id",
                Long.class, rs.getLong("id"))), branchId);
    }

    @Transactional(readOnly = true)
    public Coverage coverage(String locality, String postalCode) {
        if (!enabled()) throw new IllegalStateException("Delivery coverage is disabled.");
        var matches = jdbc.query("""
                SELECT z.branch_id, b.name, z.opens_at, z.closes_at FROM delivery_zones z
                JOIN branches b ON b.id = z.branch_id
                WHERE z.locality_key = ? AND z.postal_code = ? AND z.active
                  AND NOT z.rider_paused AND b.active
                  AND EXISTS (SELECT 1 FROM delivery_zone_products zp JOIN branch_products bp
                      ON bp.product_id = zp.product_id AND bp.branch_id = z.branch_id
                      JOIN products p ON p.id = zp.product_id
                      JOIN categories c ON c.id = p.category_id
                      WHERE zp.zone_id = z.id AND bp.available AND p.active AND c.active)
                ORDER BY b.name
                """, (rs, row) -> new Area(rs.getLong("branch_id"), rs.getString("name"),
                rs.getTime("opens_at").toLocalTime(), rs.getTime("closes_at").toLocalTime()),
                canonical(locality), postalCode);
        // Until a delivery capacity/reservation path exists, coverage cannot promise a slot.
        return new Coverage(matches, false, "Coverage is provisional. Delivery ordering is not open yet.");
    }

    static String canonical(String locality) {
        return locality.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static Object[] concat(long branchId, List<Long> ids) {
        Object[] params = new Object[ids.size() + 1];
        params[0] = branchId;
        for (int i = 0; i < ids.size(); i++) params[i + 1] = ids.get(i);
        return params;
    }

    public record Configuration(@NotBlank @Size(min = 2, max = 120) String locality,
                                @NotBlank @Pattern(regexp = "[0-9]{6}") String postalCode,
                                @NotNull LocalTime opensAt, @NotNull LocalTime closesAt,
                                boolean active, boolean riderPaused,
                                @NotNull @Size(max = 100) List<@NotNull @Positive Long> productIds) {}
    public record Zone(Long id, long branchId, String locality, String postalCode, LocalTime opensAt,
                       LocalTime closesAt, boolean active, boolean riderPaused, List<Long> productIds) {}
    public record Area(long branchId, String branchName, LocalTime opensAt, LocalTime closesAt) {}
    public record Coverage(List<Area> configuredAreas, boolean orderable, String notice) {}
}
