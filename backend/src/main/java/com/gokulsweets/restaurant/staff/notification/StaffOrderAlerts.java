package com.gokulsweets.restaurant.staff.notification;

import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.PreparationEligibility;
import com.gokulsweets.restaurant.order.service.PreparationEligibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffOrderAlerts {
    private final JdbcTemplate jdbc;
    private final EnhancementProperties flags;
    private final Environment environment;
    private final StaffAlertProperties properties;
    private final OrderRepository orders;
    private final PreparationEligibilityService preparation;
    private final Clock inventoryClock;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", java.util.Locale.ENGLISH);

    // Same rule is used for inbox, registration recipients and the pre-send permission recheck.
    public static final String ELIGIBLE = """
        u.active AND (EXISTS (SELECT 1 FROM roles r WHERE r.id = u.role_id AND r.name = 'OWNER_ADMIN')
          OR EXISTS (SELECT 1 FROM staff_branch_access b WHERE b.staff_user_id = u.id AND b.branch_id = e.branch_id))
        AND EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
          WHERE rp.role_id = u.role_id AND p.name = 'ORDER_VIEW')
        AND EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
          WHERE rp.role_id = u.role_id AND p.name = e.required_permission)
        """;
    public record Event(long id, long orderId, String orderNumber, long branchId, String kind, String title,
                        String message, LocalDateTime scheduledAt, java.time.Instant createdAt) {}
    public record Message(Event event, java.time.Instant readAt, boolean actionRequired, String pushState, String emailState) {}
    public record Page(List<Message> messages, long unreadCount, Long nextBefore) {}
    public boolean enabled() {
        return flags.isStaffOrderAlerts() && flags.isSecureStaffSessions()
                && environment.getProperty("gokul.environment-isolation.enabled", Boolean.class, false)
                && environment.getProperty("gokul.web.environment-cors-enabled", Boolean.class, false)
                && List.of("DEV", "PROD").contains(scope());
    }
    public String scope() {return environment.getProperty("gokul.environment-isolation.environment", "");}
    public LocalDateTime now() {return LocalDateTime.ofInstant(inventoryClock.instant(), ApplicationClock.BUSINESS_ZONE);}
    public int reminderMinutes() {return Math.max(1, Math.min(60, properties.getReminderMinutes()));}
    public int escalationMinutes() {return Math.max(1, Math.min(60, properties.getEscalationMinutes()));}

    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentConfirmed(long paymentId) {
        if (!enabled()) return;
        jdbc.update("""
            INSERT INTO staff_order_alerts(environment, event_key, order_id, branch_id, kind, required_permission, title, message)
            SELECT ?, 'paid:' || o.id, o.id, o.branch_id, 'NEW_ORDER', 'ORDER_VIEW', LEFT('New paid order · ' || b.name, 160),
              'Order ' || o.order_number || ' is confirmed. ' || CASE WHEN s.id IS NULL THEN 'Open the delivery order for its service window.'
              ELSE 'Pickup ' || to_char(s.slot_date, 'DD Mon YYYY') || ', ' || to_char(s.start_time, 'HH12:MI AM') || ' IST. Check the preparation queue.' END
            FROM payments p JOIN orders o ON o.id = p.order_id JOIN branches b ON b.id = o.branch_id
            LEFT JOIN pickup_slots s ON s.id = o.pickup_slot_id
            WHERE p.id = ? AND p.payment_status = 'PAID' AND o.order_status = 'CONFIRMED'
            ON CONFLICT(environment, event_key) DO NOTHING
            """, scope(), paymentId);
    }

    @Transactional
    public void generateReminders() {
        if (!enabled()) return;
        // Process all live waiting/preparing orders in bounded keyset pages; no dependency on portal traffic.
        long after = 0;
        while (true) {
            var ids = jdbc.query("SELECT id FROM orders WHERE id > ? AND order_status IN ('CONFIRMED','PREPARING') ORDER BY id LIMIT 100",
                    (rs, row) -> rs.getLong(1), after);
            if (ids.isEmpty()) return;
            for (long id : ids) {
                Order order = orders.findById(id).orElse(null);
                if (order == null) continue;
                try {
                    var schedule = schedule(order);
                    LocalDateTime now = now(), due = schedule.eligibleAt(), pickup = schedule.pickupAt();
                    if (order.getOrderStatus() == OrderStatus.CONFIRMED) {
                        if (!now.isBefore(due.minusMinutes(reminderMinutes())) && now.isBefore(due))
                            record(order, "PREPARATION_SOON", "ORDER_START_PREPARATION", due,
                                    "Preparation opens soon", "Preparation opens at " + TIME.format(due) + " IST. Check ingredients and the queue; start only when eligible.");
                        else if (!now.isBefore(due) && now.isBefore(pickup))
                            record(order, "PREPARATION_DUE", "ORDER_START_PREPARATION", due,
                                    "Time to start preparation", "The preparation window is open. Start preparation for pickup/service at " + TIME.format(pickup) + " IST.");
                    }
                    if (!now.isBefore(pickup) && now.isBefore(pickup.plusDays(1))) {
                        boolean waiting = order.getOrderStatus() == OrderStatus.CONFIRMED;
                        record(order, waiting ? "PREPARATION_OVERDUE" : "READY_OVERDUE",
                                waiting ? "ORDER_START_PREPARATION" : "ORDER_MARK_READY", pickup,
                                waiting ? "Preparation is overdue" : "Order is not ready on time",
                                "Booked pickup/service time " + TIME.format(pickup) + " IST has passed. "
                                  + (waiting ? "Preparation has not started." : "The order is still preparing.")
                                  + " Take action and communicate any revised estimate to the customer.");
                    }
                } catch (IllegalStateException incompleteSchedule) {
                    // A missing legacy window must not prevent other branches' reminders. The order stays in its queue.
                }
            }
            after = ids.getLast();
        }
    }
    private void record(Order order, String kind, String permission, LocalDateTime scheduled, String title, String message) {
        jdbc.update("""
                INSERT INTO staff_order_alerts(environment, event_key, order_id, branch_id, kind, required_permission,
                  title, message, scheduled_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(environment, event_key) DO NOTHING
                """, scope(), order.getId() + ":" + kind + ":" + scheduled, order.getId(), order.getBranch().getId(), kind,
                permission, title, "Order " + order.getOrderNumber() + " · " + order.getBranch().getName() + ". " + message, Timestamp.valueOf(scheduled));
    }
    private PreparationEligibility schedule(Order order) {
        // Evaluate a snapshot so a PREPARING entity is never mutated back to CONFIRMED.
        Order view = new Order(); view.setOrderStatus(OrderStatus.CONFIRMED);
        view.setBranch(order.getBranch()); view.setPickupSlot(order.getPickupSlot()); view.setPickupType(order.getPickupType());
        view.setFulfillmentType(order.getFulfillmentType()); view.setDeliveryWindowId(order.getDeliveryWindowId());
        return preparation.evaluate(view, now());
    }
    @Transactional(readOnly = true)
    public boolean actionable(Event event) {
        Order order = orders.findById(event.orderId()).orElse(null);
        if (order == null) return false;
        if (event.kind().equals("NEW_ORDER")) return order.getOrderStatus() == OrderStatus.CONFIRMED;
        if (order.getOrderStatus() != OrderStatus.CONFIRMED && order.getOrderStatus() != OrderStatus.PREPARING) return false;
        try {
            var plan = schedule(order); var now = now();
            return switch (event.kind()) {
                case "PREPARATION_SOON" -> order.getOrderStatus() == OrderStatus.CONFIRMED && plan.eligibleAt().equals(event.scheduledAt())
                    && !now.isBefore(plan.eligibleAt().minusMinutes(reminderMinutes())) && now.isBefore(plan.eligibleAt());
                case "PREPARATION_DUE" -> order.getOrderStatus() == OrderStatus.CONFIRMED && plan.eligibleAt().equals(event.scheduledAt())
                    && !now.isBefore(plan.eligibleAt()) && now.isBefore(plan.pickupAt());
                case "PREPARATION_OVERDUE" -> order.getOrderStatus() == OrderStatus.CONFIRMED && plan.pickupAt().equals(event.scheduledAt())
                    && !now.isBefore(plan.pickupAt()) && now.isBefore(plan.pickupAt().plusDays(1));
                case "READY_OVERDUE" -> order.getOrderStatus() == OrderStatus.PREPARING && plan.pickupAt().equals(event.scheduledAt())
                    && !now.isBefore(plan.pickupAt()) && now.isBefore(plan.pickupAt().plusDays(1));
                default -> false;
            };
        } catch (IllegalStateException incomplete) {return false;}
    }
    public static Event event(java.sql.ResultSet rs) throws java.sql.SQLException {
        var scheduled = rs.getTimestamp("scheduled_at");
        return new Event(rs.getLong("id"), rs.getLong("order_id"), rs.getString("order_number"), rs.getLong("branch_id"),
                rs.getString("kind"), rs.getString("title"), rs.getString("message"), scheduled == null ? null : scheduled.toLocalDateTime(),
                rs.getTimestamp("created_at").toInstant());
    }
    @Transactional(readOnly = true)
    public Page page(long staffId, Long before) {return page(staffId, before, false, "");}
    @Transactional(readOnly = true)
    public Page page(long staffId, Long before, boolean unreadOnly, String search) {
        String query = search == null ? "" : search.trim();
        if (query.length() > 100) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        if (before != null && before <= 0) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        var messages = jdbc.query("""
            SELECT e.*, o.order_number, r.read_at,
              (SELECT state FROM staff_alert_deliveries d WHERE d.event_id = e.id AND d.staff_id = u.id AND d.channel = 'PUSH' ORDER BY d.id DESC LIMIT 1) push_state,
              (SELECT state FROM staff_alert_deliveries d WHERE d.event_id = e.id AND d.staff_id = u.id AND d.channel = 'EMAIL') email_state
            FROM staff_order_alerts e JOIN orders o ON o.id = e.order_id JOIN staff_users u ON u.id = ?
            LEFT JOIN staff_order_alert_reads r ON r.event_id = e.id AND r.staff_id = u.id
            WHERE e.environment = ? AND e.id < ? AND (NOT ? OR r.read_at IS NULL)
              AND (? = '' OR strpos(lower(o.order_number || ' ' || e.title || ' ' || e.message), lower(?)) > 0) AND
            """ + ELIGIBLE + " ORDER BY e.id DESC LIMIT 31", (rs, row) -> {
                var event = event(rs); var read = rs.getTimestamp("read_at");
                return new Message(event, read == null ? null : read.toInstant(), actionable(event), rs.getString("push_state"), rs.getString("email_state"));
            }, staffId, scope(), before == null ? Long.MAX_VALUE : before, unreadOnly, query, query);
        Long unread = jdbc.queryForObject("SELECT COUNT(*) FROM staff_order_alerts e JOIN staff_users u ON u.id = ? WHERE e.environment = ? AND "
                + ELIGIBLE + " AND NOT EXISTS (SELECT 1 FROM staff_order_alert_reads r WHERE r.event_id = e.id AND r.staff_id = u.id)", Long.class, staffId, scope());
        return new Page(messages.stream().limit(30).toList(), unread == null ? 0 : unread, messages.size() > 30 ? messages.get(29).event().id() : null);
    }
    @Transactional
    public void markRead(long staffId, long eventId) {
        Boolean allowed = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM staff_order_alerts e JOIN staff_users u ON u.id = ? WHERE e.id = ? AND e.environment = ? AND " + ELIGIBLE + ")", Boolean.class, staffId, eventId, scope());
        if (!Boolean.TRUE.equals(allowed)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        jdbc.update("INSERT INTO staff_order_alert_reads(event_id, staff_id) VALUES (?, ?) ON CONFLICT DO NOTHING", eventId, staffId);
    }
    public boolean eligible(long eventId, long staffId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM staff_order_alerts e JOIN staff_users u ON u.id = ? WHERE e.id = ? AND e.environment = ? AND " + ELIGIBLE + ")", Boolean.class, staffId, eventId, scope()));
    }
}
