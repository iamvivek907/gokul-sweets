package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

/** Coordinates delivery boundary operations. */
@Service
@RequiredArgsConstructor
public class DeliveryBoundaryService {

    private final EnhancementProperties flags;

    private final DeliveryZoneService zones;

    private final JdbcTemplate jdbc;

    private final ObjectMapper mapper;

    /**
     * Returns whether the configured prerequisites for this feature are enabled.
     *
     * @return the value of {@code flags.isDeliveryAddressBoundaries() && flags.isDeliveryCapacity()
     *     && zones.enabled()}
     */
    public boolean enabled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryBoundaryService.class, "enabled()");
        try {
            return flags.isDeliveryAddressBoundaries()
                    && flags.isDeliveryCapacity()
                    && zones.enabled();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DeliveryBoundaryService.class, "enabled()");
        }
    }

    /**
     * Configures delivery boundary data and returns the {@code Boundary} result.
     *
     * <p>Reads {@code delivery_zones}.
     *
     * <p>Writes {@code delivery_zone_boundaries}.
     *
     * @param branchId the branch id supplied to this method
     * @param zoneId the zone id supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code input}
     * @throws IllegalArgumentException when the method rejects the request with {@code Invalid
     *     delivery boundary.}
     */
    @Transactional
    public Boundary configure(long branchId, long zoneId, Boundary input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryBoundaryService.class, "configure(long,long,Boundary)");
        try {
            requireEnabled();
            validate(input.vertices());
            jdbc
                    .query(
                            "SELECT 1 FROM delivery_zones WHERE branch_id = ? AND id = ? FOR"
                                    + " UPDATE",
                            (rs, row) -> rs.getInt(1),
                            branchId,
                            zoneId)
                    .stream()
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND, "Zone not found."));
            String json;
            try {
                json = mapper.writeValueAsString(input.vertices());
            } catch (Exception exception) {
                throw new IllegalArgumentException("Invalid delivery boundary.", exception);
            }
            jdbc.update(
                    """
INSERT INTO delivery_zone_boundaries (zone_id, vertices, reviewed) VALUES (?, ?::jsonb, ?)
ON CONFLICT (zone_id) DO UPDATE SET vertices = EXCLUDED.vertices,
    reviewed = EXCLUDED.reviewed, updated_at = CURRENT_TIMESTAMP
""",
                    zoneId,
                    json,
                    input.reviewed());
            return input;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryBoundaryService.class,
                    "configure(long,long,Boundary)");
        }
    }

    /**
     * Returns get information for delivery boundary.
     *
     * <p>Reads {@code delivery_zone_boundaries}, {@code delivery_zones}.
     *
     * @param branchId the branch id supplied to this method
     * @param zoneId the zone id supplied to this method
     * @return the value of {@code rows.getFirst()}
     * @throws ResponseStatusException when the method rejects the request with {@code Boundary not
     *     configured.}
     */
    @Transactional(readOnly = true)
    public Boundary get(long branchId, long zoneId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryBoundaryService.class, "get(long,long)");
        try {
            requireEnabled();
            var rows =
                    jdbc.query(
                            """
SELECT b.vertices::text, b.reviewed FROM delivery_zone_boundaries b
JOIN delivery_zones z ON z.id = b.zone_id WHERE z.id = ? AND z.branch_id = ?
""",
                            (rs, row) -> decode(rs.getString(1), rs.getBoolean(2)),
                            zoneId,
                            branchId);
            if (rows.isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Boundary not configured.");
            return rows.getFirst();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DeliveryBoundaryService.class, "get(long,long)");
        }
    }

    /**
     * Returns contains information for delivery boundary.
     *
     * <p>Reads {@code delivery_zone_boundaries}.
     *
     * @param zoneId the zone id supplied to this method
     * @param latitude the latitude supplied to this method
     * @param longitude the longitude supplied to this method
     * @return the {@code boolean} result
     */
    @Transactional(readOnly = true)
    public boolean contains(long zoneId, Double latitude, Double longitude) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryBoundaryService.class, "contains(long,Double,Double)");
        try {
            if (!enabled() || latitude == null || longitude == null || !valid(latitude, longitude))
                return false;
            var rows =
                    jdbc.query(
                            """
SELECT vertices::text FROM delivery_zone_boundaries WHERE zone_id = ? AND reviewed
""",
                            (rs, row) -> rs.getString(1),
                            zoneId);
            return !rows.isEmpty()
                    && inside(
                            decode(rows.getFirst(), true).vertices(),
                            new Point(latitude, longitude));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryBoundaryService.class,
                    "contains(long,Double,Double)");
        }
    }

    /**
     * Decodes delivery boundary data and returns the {@code Boundary} result.
     *
     * @param json the json supplied to this method
     * @param reviewed the reviewed supplied to this method
     * @return the {@code Boundary} result
     * @throws IllegalStateException when the method rejects the request with {@code Invalid stored
     *     delivery boundary.}
     */
    private Boundary decode(String json, boolean reviewed) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryBoundaryService.class, "decode(String,boolean)");
        try {
            try {
                return new Boundary(List.of(mapper.readValue(json, Point[].class)), reviewed);
            } catch (Exception exception) {
                throw new IllegalStateException("Invalid stored delivery boundary.", exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryBoundaryService.class,
                    "decode(String,boolean)");
        }
    }

    /** Requires enabled. */
    private void requireEnabled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryBoundaryService.class, "requireEnabled()");
        try {
            if (!enabled())
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Address boundaries are disabled.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DeliveryBoundaryService.class, "requireEnabled()");
        }
    }

    /**
     * Returns valid information for delivery boundary.
     *
     * @param lat the lat supplied to this method
     * @param lon the lon supplied to this method
     * @return the value of {@code Double.isFinite(lat) && Double.isFinite(lon) && lat >= -90 && lat
     *     <= 90 && lon >= -180 && lon <= 180}
     */
    private static boolean valid(double lat, double lon) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryBoundaryService.class, "valid(double,double)");
        try {
            return Double.isFinite(lat)
                    && Double.isFinite(lon)
                    && lat >= -90
                    && lat <= 90
                    && lon >= -180
                    && lon <= 180;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryBoundaryService.class,
                    "valid(double,double)");
        }
    }

    /**
     * Validates delivery boundary data.
     *
     * @param points the points supplied to this method
     * @throws IllegalArgumentException when the method rejects the request with {@code A boundary
     *     needs 3 to 100 vertices.}; {@code Adjacent boundary vertices must differ.}; {@code
     *     Boundary area is too small or self-crossing.}; {@code Boundary edges cannot cross.};
     *     {@code Invalid boundary coordinate.}
     */
    static void validate(List<Point> points) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryBoundaryService.class, "validate(List<Point>)");
        try {
            if (points == null || points.size() < 3 || points.size() > 100)
                throw new IllegalArgumentException("A boundary needs 3 to 100 vertices.");
            for (int i = 0; i < points.size(); i++) {
                Point point = points.get(i);
                if (point == null || !valid(point.latitude(), point.longitude()))
                    throw new IllegalArgumentException("Invalid boundary coordinate.");
                Point next = points.get((i + 1) % points.size());
                if (point.equals(next))
                    throw new IllegalArgumentException("Adjacent boundary vertices must differ.");
            }
            double area = 0;
            for (int i = 0; i < points.size(); i++) {
                Point a = points.get(i), b = points.get((i + 1) % points.size());
                area += a.longitude() * b.latitude() - b.longitude() * a.latitude();
            }
            if (Math.abs(area) < 1e-12)
                throw new IllegalArgumentException("Boundary area is too small or self-crossing.");
            for (int i = 0; i < points.size(); i++)
                for (int j = i + 2; j < points.size(); j++) {
                    if (i == 0 && j == points.size() - 1) continue;
                    Point a = points.get(i), b = points.get((i + 1) % points.size());
                    Point c = points.get(j), d = points.get((j + 1) % points.size());
                    if (intersects(a, b, c, d))
                        throw new IllegalArgumentException("Boundary edges cannot cross.");
                }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryBoundaryService.class,
                    "validate(List<Point>)");
        }
    }

    /**
     * Returns cross information for delivery boundary.
     *
     * @param a the a supplied to this method
     * @param b the b supplied to this method
     * @param c the c supplied to this method
     * @return the value of {@code (b.longitude() - a.longitude()) * (c.latitude() - a.latitude()) -
     *     (b.latitude() - a.latitude()) * (c.longitude() - a.longitude())}
     */
    private static double cross(Point a, Point b, Point c) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryBoundaryService.class, "cross(Point,Point,Point)");
        try {
            return (b.longitude() - a.longitude()) * (c.latitude() - a.latitude())
                    - (b.latitude() - a.latitude()) * (c.longitude() - a.longitude());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryBoundaryService.class,
                    "cross(Point,Point,Point)");
        }
    }

    /**
     * Ons edge.
     *
     * @param a the a
     * @param b the b
     * @param p the p
     * @return the on edge result
     */
    private static boolean onEdge(Point a, Point b, Point p) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryBoundaryService.class, "onEdge(Point,Point,Point)");
        try {
            return Math.abs(cross(a, b, p)) < 1e-10
                    && p.longitude() >= Math.min(a.longitude(), b.longitude()) - 1e-10
                    && p.longitude() <= Math.max(a.longitude(), b.longitude()) + 1e-10
                    && p.latitude() >= Math.min(a.latitude(), b.latitude()) - 1e-10
                    && p.latitude() <= Math.max(a.latitude(), b.latitude()) + 1e-10;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryBoundaryService.class,
                    "onEdge(Point,Point,Point)");
        }
    }

    /**
     * Returns intersects information for delivery boundary.
     *
     * @param a the a supplied to this method
     * @param b the b supplied to this method
     * @param c the c supplied to this method
     * @param d the d supplied to this method
     * @return the value of {@code (x * y < 0 && z * w < 0) || onEdge(a, b, c) || onEdge(a, b, d) ||
     *     onEdge(c, d, a) || onEdge(c, d, b)}
     */
    private static boolean intersects(Point a, Point b, Point c, Point d) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryBoundaryService.class, "intersects(Point,Point,Point,Point)");
        try {
            double x = cross(a, b, c), y = cross(a, b, d), z = cross(c, d, a), w = cross(c, d, b);
            return (x * y < 0 && z * w < 0)
                    || onEdge(a, b, c)
                    || onEdge(a, b, d)
                    || onEdge(c, d, a)
                    || onEdge(c, d, b);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryBoundaryService.class,
                    "intersects(Point,Point,Point,Point)");
        }
    }

    /**
     * Returns inside information for delivery boundary.
     *
     * @param polygon the polygon supplied to this method
     * @param point the point supplied to this method
     * @return the {@code boolean} result
     */
    static boolean inside(List<Point> polygon, Point point) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryBoundaryService.class, "inside(List<Point>,Point)");
        try {
            boolean found = false;
            for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
                Point a = polygon.get(j), b = polygon.get(i);
                if (onEdge(a, b, point)) return true;
                if ((a.latitude() > point.latitude()) != (b.latitude() > point.latitude())
                        && point.longitude()
                                < a.longitude()
                                        + (b.longitude() - a.longitude())
                                                * (point.latitude() - a.latitude())
                                                / (b.latitude() - a.latitude())) found = !found;
            }
            return found;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryBoundaryService.class,
                    "inside(List<Point>,Point)");
        }
    }

    /**
     * Immutable point data contract.
     *
     * @param latitude the latitude
     * @param longitude the longitude
     */
    public record Point(double latitude, double longitude) {}

    /**
     * Immutable boundary data contract.
     *
     * @param vertices the vertices
     * @param reviewed the reviewed
     */
    public record Boundary(
            @NotNull @Size(min = 3, max = 100) List<@NotNull @Valid Point> vertices,
            boolean reviewed) {}
}
