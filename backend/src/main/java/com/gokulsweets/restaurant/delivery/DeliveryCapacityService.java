package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.sql.Time;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryCapacityService {
    private final EnhancementProperties flags;
    private final DeliveryZoneService zones;
    private final JdbcTemplate jdbc;
    private final Clock inventoryClock;
    private final DeliveryStockCheck stock;
    private final DeliveryBoundaryService boundaries;

    public boolean enabled() { return flags.isDeliveryCapacity() && zones.enabled(); }

    @Transactional
    public Window configure(long branchId, long zoneId, WindowConfiguration input) {
        requireEnabled();
        if (input.riderCapacity() < 1 || input.riderCapacity() > 1000)
            throw new IllegalArgumentException("Rider capacity must be between 1 and 1000.");
        LocalDate today = LocalDate.now(inventoryClock);
        if (input.serviceDate().isBefore(today) || input.serviceDate().isAfter(today.plusDays(30)))
            throw new IllegalArgumentException("Select a date within the next 30 IST business days.");
        var zone = jdbc.query("""
                SELECT opens_at, closes_at FROM delivery_zones WHERE id = ? AND branch_id = ? FOR UPDATE
                """, (rs, row) -> new LocalTime[]{rs.getTime(1).toLocalTime(), rs.getTime(2).toLocalTime()},
                zoneId, branchId).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!input.endsAt().isAfter(input.startsAt()) || input.startsAt().isBefore(zone[0])
                || input.endsAt().isAfter(zone[1])) throw new IllegalArgumentException("Window must fit inside zone operating hours.");
        var result = jdbc.query("""
                INSERT INTO delivery_capacity_windows (zone_id, service_date, starts_at, ends_at, rider_capacity, paused)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (zone_id, service_date, starts_at, ends_at) DO UPDATE SET
                    rider_capacity = EXCLUDED.rider_capacity, paused = EXCLUDED.paused,
                    updated_at = CURRENT_TIMESTAMP
                WHERE delivery_capacity_windows.reserved_count <= EXCLUDED.rider_capacity
                RETURNING id, reserved_count
                """, (rs, row) -> new long[]{rs.getLong("id"), rs.getLong("reserved_count")},
                zoneId, Date.valueOf(input.serviceDate()), Time.valueOf(input.startsAt()),
                Time.valueOf(input.endsAt()), input.riderCapacity(), input.paused());
        if (result.isEmpty()) throw new IllegalArgumentException("Capacity cannot be lower than existing reservations.");
        return new Window(result.getFirst()[0], zoneId, input.serviceDate(), input.startsAt(), input.endsAt(),
                input.riderCapacity(), Math.toIntExact(result.getFirst()[1]), input.paused());
    }

    @Transactional(readOnly = true)
    public List<Window> list(long branchId, long zoneId) {
        requireEnabled();
        return jdbc.query("""
                SELECT w.id, w.zone_id, w.service_date, w.starts_at, w.ends_at,
                       w.rider_capacity, w.reserved_count, w.paused
                FROM delivery_capacity_windows w JOIN delivery_zones z ON z.id = w.zone_id
                WHERE z.id = ? AND z.branch_id = ? AND w.service_date >= ?
                ORDER BY w.service_date, w.starts_at
                """, (rs, row) -> new Window(rs.getLong("id"), rs.getLong("zone_id"),
                rs.getDate("service_date").toLocalDate(), rs.getTime("starts_at").toLocalTime(),
                rs.getTime("ends_at").toLocalTime(), rs.getInt("rider_capacity"),
                rs.getInt("reserved_count"), rs.getBoolean("paused")),
                zoneId, branchId, Date.valueOf(LocalDate.now(inventoryClock)));
    }

    @Transactional(readOnly = true)
    public Quote quote(QuoteRequest request) {
        requireEnabled();
        if (request.items() == null || request.items().isEmpty() || request.items().size() > 50)
            return unavailable("Add products before checking delivery capability.");
        if (request.items().stream().anyMatch(item -> item == null || item.productId() == null
                || item.productId() <= 0)) return unavailable("Review the items in your cart.");
        LocalDate today = LocalDate.now(inventoryClock);
        if (request.serviceDate().isBefore(today) || request.serviceDate().isAfter(today.plusDays(30)))
            return unavailable("Choose a date within the next 30 IST business days.");
        List<Long> productIds = request.items().stream().map(CreateOrderItemRequest::productId).distinct().toList();
        if (productIds.size() != request.items().size()) return unavailable("Choose each item only once.");
        // Only exact zone, branch and active catalog matches qualify. No inferred radius or pickup slot reuse.
        var zoneIds = jdbc.queryForList("""
                SELECT z.id FROM delivery_zones z JOIN branches b ON b.id = z.branch_id
                WHERE z.branch_id = ? AND z.postal_code = ? AND z.locality_key = ?
                  AND z.active AND NOT z.rider_paused AND b.active
                """, Long.class, request.branchId(), request.postalCode(), DeliveryZoneService.canonical(request.locality()));
        if (zoneIds.isEmpty()) return unavailable("Delivery coverage has not been confirmed for this area.");
        Long zoneId = zoneIds.getFirst();
        if (flags.isDeliveryAddressBoundaries() && !boundaries.contains(zoneId, request.latitude(), request.longitude()))
            return unavailable("A pin inside a reviewed delivery boundary is required. Confirm your pin or choose pickup.");
        Long eligibleProducts = jdbc.queryForObject("""
                SELECT count(*) FROM delivery_zone_products zp
                JOIN branch_products bp ON bp.branch_id = ? AND bp.product_id = zp.product_id
                JOIN products p ON p.id = zp.product_id JOIN categories c ON c.id = p.category_id
                WHERE zp.zone_id = ? AND zp.product_id IN (%s)
                    AND bp.available AND p.active AND c.active
                """.formatted(String.join(",", Collections.nCopies(productIds.size(), "?"))),
                Long.class, params(request.branchId(), zoneId, productIds));
        if (eligibleProducts == null || eligibleProducts != productIds.size())
            return unavailable("One or more products are not offered for delivery in this area.");
        var dailyStock = stock.check(request.branchId(), request.serviceDate(), request.items());
        if (!dailyStock.available()) return unavailable(dailyStock.reason());
        var windows = jdbc.query("""
                SELECT w.id, w.zone_id, w.service_date, w.starts_at, w.ends_at,
                       w.rider_capacity, w.reserved_count, w.paused
                FROM delivery_capacity_windows w JOIN delivery_zones z ON z.id = w.zone_id
                WHERE w.zone_id = ? AND w.service_date = ? AND NOT w.paused
                  AND w.reserved_count < w.rider_capacity
                  AND w.starts_at >= z.opens_at AND w.ends_at <= z.closes_at
                ORDER BY w.starts_at
                """, (rs, row) -> new Window(rs.getLong("id"), rs.getLong("zone_id"),
                rs.getDate("service_date").toLocalDate(), rs.getTime("starts_at").toLocalTime(),
                rs.getTime("ends_at").toLocalTime(), rs.getInt("rider_capacity"),
                rs.getInt("reserved_count"), rs.getBoolean("paused")),
                zoneId, Date.valueOf(request.serviceDate())).stream()
                .filter(window -> request.serviceDate().isAfter(today)
                        || window.startsAt().isAfter(LocalTime.now(inventoryClock)))
                .toList();
        return new Quote(windows, false, windows.isEmpty()
                ? "No delivery windows are currently configured with rider capacity."
                : "Windows are provisional; product inventory and rider capacity are not reserved. Delivery checkout is not open yet.");
    }

    private void requireEnabled() {
        if (!enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery capacity is disabled.");
    }

    private static Quote unavailable(String notice) { return new Quote(List.of(), false, notice); }

    private static Object[] params(long branchId, long zoneId, List<Long> productIds) {
        Object[] values = new Object[productIds.size() + 2];
        values[0] = branchId; values[1] = zoneId;
        for (int i = 0; i < productIds.size(); i++) values[i + 2] = productIds.get(i);
        return values;
    }

    public record WindowConfiguration(@NotNull LocalDate serviceDate, @NotNull LocalTime startsAt,
                                      @NotNull LocalTime endsAt, @Min(1) @Max(1000) int riderCapacity,
                                      boolean paused) {}
    public record Window(long id, long zoneId, LocalDate serviceDate, LocalTime startsAt,
                         LocalTime endsAt, int riderCapacity, int reservedCount, boolean paused) {}
    public record QuoteRequest(@Positive long branchId, @NotBlank @Size(min = 2, max = 120) String locality,
                               @NotBlank @Pattern(regexp = "[0-9]{6}") String postalCode,
                               @NotNull LocalDate serviceDate,
                               @NotEmpty @Size(max = 50) List<@NotNull @Valid CreateOrderItemRequest> items,
                               Double latitude, Double longitude) {
        public QuoteRequest(long branchId, String locality, String postalCode, LocalDate serviceDate,
                            List<CreateOrderItemRequest> items) {
            this(branchId, locality, postalCode, serviceDate, items, null, null);
        }
    }
    public record Quote(List<Window> provisionalWindows, boolean orderable, String notice) {}
}
