package com.gokulsweets.restaurant.delivery;

import tools.jackson.databind.ObjectMapper;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryBoundaryService {
    private final EnhancementProperties flags;
    private final DeliveryZoneService zones;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public boolean enabled() {
        return flags.isDeliveryAddressBoundaries() && flags.isDeliveryCapacity() && zones.enabled();
    }

    @Transactional
    public Boundary configure(long branchId, long zoneId, Boundary input) {
        requireEnabled();
        validate(input.vertices());
        jdbc.query("SELECT 1 FROM delivery_zones WHERE branch_id = ? AND id = ? FOR UPDATE",
                (rs, row) -> rs.getInt(1), branchId, zoneId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zone not found."));
        String json;
        try { json = mapper.writeValueAsString(input.vertices()); }
        catch (Exception exception) {
            throw new IllegalArgumentException("Invalid delivery boundary.", exception);
        }
        jdbc.update("""
                INSERT INTO delivery_zone_boundaries (zone_id, vertices, reviewed) VALUES (?, ?::jsonb, ?)
                ON CONFLICT (zone_id) DO UPDATE SET vertices = EXCLUDED.vertices,
                    reviewed = EXCLUDED.reviewed, updated_at = CURRENT_TIMESTAMP
                """, zoneId, json, input.reviewed());
        return input;
    }

    @Transactional(readOnly = true)
    public Boundary get(long branchId, long zoneId) {
        requireEnabled();
        var rows = jdbc.query("""
                SELECT b.vertices::text, b.reviewed FROM delivery_zone_boundaries b
                JOIN delivery_zones z ON z.id = b.zone_id WHERE z.id = ? AND z.branch_id = ?
                """, (rs, row) -> decode(rs.getString(1), rs.getBoolean(2)), zoneId, branchId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Boundary not configured.");
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public boolean contains(long zoneId, Double latitude, Double longitude) {
        if (!enabled() || latitude == null || longitude == null || !valid(latitude, longitude)) return false;
        var rows = jdbc.query("""
                SELECT vertices::text FROM delivery_zone_boundaries WHERE zone_id = ? AND reviewed
                """, (rs, row) -> rs.getString(1), zoneId);
        return !rows.isEmpty() && inside(decode(rows.getFirst(), true).vertices(), new Point(latitude, longitude));
    }

    private Boundary decode(String json, boolean reviewed) {
        try { return new Boundary(List.of(mapper.readValue(json, Point[].class)), reviewed); }
        catch (Exception exception) { throw new IllegalStateException("Invalid stored delivery boundary.", exception); }
    }

    private void requireEnabled() {
        if (!enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address boundaries are disabled.");
    }

    private static boolean valid(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    static void validate(List<Point> points) {
        if (points == null || points.size() < 3 || points.size() > 100)
            throw new IllegalArgumentException("A boundary needs 3 to 100 vertices.");
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            if (point == null || !valid(point.latitude(), point.longitude()))
                throw new IllegalArgumentException("Invalid boundary coordinate.");
            Point next = points.get((i + 1) % points.size());
            if (point.equals(next)) throw new IllegalArgumentException("Adjacent boundary vertices must differ.");
        }
        double area = 0;
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i), b = points.get((i + 1) % points.size());
            area += a.longitude() * b.latitude() - b.longitude() * a.latitude();
        }
        if (Math.abs(area) < 1e-12) throw new IllegalArgumentException("Boundary area is too small or self-crossing.");
        for (int i = 0; i < points.size(); i++) for (int j = i + 2; j < points.size(); j++) {
            if (i == 0 && j == points.size() - 1) continue;
            Point a = points.get(i), b = points.get((i + 1) % points.size());
            Point c = points.get(j), d = points.get((j + 1) % points.size());
            if (intersects(a, b, c, d)) throw new IllegalArgumentException("Boundary edges cannot cross.");
        }
    }

    private static double cross(Point a, Point b, Point c) {
        return (b.longitude() - a.longitude()) * (c.latitude() - a.latitude())
                - (b.latitude() - a.latitude()) * (c.longitude() - a.longitude());
    }

    private static boolean onEdge(Point a, Point b, Point p) {
        return Math.abs(cross(a, b, p)) < 1e-10
                && p.longitude() >= Math.min(a.longitude(), b.longitude()) - 1e-10
                && p.longitude() <= Math.max(a.longitude(), b.longitude()) + 1e-10
                && p.latitude() >= Math.min(a.latitude(), b.latitude()) - 1e-10
                && p.latitude() <= Math.max(a.latitude(), b.latitude()) + 1e-10;
    }

    private static boolean intersects(Point a, Point b, Point c, Point d) {
        double x = cross(a, b, c), y = cross(a, b, d), z = cross(c, d, a), w = cross(c, d, b);
        return (x * y < 0 && z * w < 0) || onEdge(a, b, c) || onEdge(a, b, d)
                || onEdge(c, d, a) || onEdge(c, d, b);
    }

    static boolean inside(List<Point> polygon, Point point) {
        boolean found = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            Point a = polygon.get(j), b = polygon.get(i);
            if (onEdge(a, b, point)) return true;
            if ((a.latitude() > point.latitude()) != (b.latitude() > point.latitude())
                    && point.longitude() < a.longitude() + (b.longitude() - a.longitude())
                    * (point.latitude() - a.latitude()) / (b.latitude() - a.latitude())) found = !found;
        }
        return found;
    }

    public record Point(double latitude, double longitude) {}
    public record Boundary(@NotNull @Size(min = 3, max = 100) List<@NotNull @Valid Point> vertices,
                           boolean reviewed) {}
}
