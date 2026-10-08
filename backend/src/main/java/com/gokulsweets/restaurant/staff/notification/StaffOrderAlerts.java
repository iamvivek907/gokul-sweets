package com.gokulsweets.restaurant.staff.notification;

import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
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

/** Backend staff order alerts contract and implementation. */
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

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", java.util.Locale.ENGLISH);

    // Same rule is used for inbox, registration recipients and the pre-send permission recheck.
    public static final String ELIGIBLE = AppConstant.STAFF_ORDER_ALERTS_ELIGIBLE;

    /** Immutable event data contract. */
    public record Event(
            long id,
            long orderId,
            String orderNumber,
            long branchId,
            String kind,
            String title,
            String message,
            LocalDateTime scheduledAt,
            java.time.Instant createdAt,
            java.util.UUID enquiryId,
            String targetUrl,
            Long customerOrderNumber) {

        public Event(
                long id,
                long orderId,
                String orderNumber,
                long branchId,
                String kind,
                String title,
                String message,
                LocalDateTime scheduledAt,
                java.time.Instant createdAt) {
            this(
                    id,
                    orderId,
                    orderNumber,
                    branchId,
                    kind,
                    title,
                    message,
                    scheduledAt,
                    createdAt,
                    null,
                    "/admin/orders/" + orderNumber,
                    null);
        }
    }

    /** Immutable message data contract. */
    public record Message(
            Event event,
            java.time.Instant readAt,
            boolean actionRequired,
            String pushState,
            String emailState) {}

    /** Immutable page data contract. */
    public record Page(
            List<Message> messages, long unreadCount, Long nextBefore, long readThrough) {

        public Page(List<Message> messages, long unreadCount, Long nextBefore) {
            this(messages, unreadCount, nextBefore, 0);
        }
    }

    /**
     * Enableds the operation.
     *
     * @return the enabled result
     */
    public boolean enabled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffOrderAlerts.class, "enabled()");
        try {
            return flags.isStaffOrderAlerts()
                    && flags.isSecureStaffSessions()
                    && environment.getProperty(
                            "gokul.environment-isolation.enabled", Boolean.class, false)
                    && environment.getProperty(
                            "gokul.web.environment-cors-enabled", Boolean.class, false)
                    && List.of("DEV", "PROD").contains(scope());
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, StaffOrderAlerts.class, "enabled()");
        }
    }

    /**
     * Scopes the operation.
     *
     * @return the scope result
     */
    public String scope() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffOrderAlerts.class, "scope()");
        try {
            return environment.getProperty("gokul.environment-isolation.environment", "");
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, StaffOrderAlerts.class, "scope()");
        }
    }

    /**
     * Nows the operation.
     *
     * @return the now result
     */
    public LocalDateTime now() {
        final long __gokulMethodStartedNanos = MethodTiming.start(StaffOrderAlerts.class, "now()");
        try {
            return LocalDateTime.ofInstant(
                    inventoryClock.instant(), ApplicationClock.BUSINESS_ZONE);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, StaffOrderAlerts.class, "now()");
        }
    }

    /**
     * Reminders minutes.
     *
     * @return the reminder minutes result
     */
    public int reminderMinutes() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffOrderAlerts.class, "reminderMinutes()");
        try {
            return Math.max(1, Math.min(60, properties.getReminderMinutes()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffOrderAlerts.class, "reminderMinutes()");
        }
    }

    /**
     * Escalations minutes.
     *
     * @return the escalation minutes result
     */
    public int escalationMinutes() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffOrderAlerts.class, "escalationMinutes()");
        try {
            return Math.max(1, Math.min(60, properties.getEscalationMinutes()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffOrderAlerts.class, "escalationMinutes()");
        }
    }

    /**
     * Payments confirmed.
     *
     * @param paymentId the payment id
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentConfirmed(long paymentId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffOrderAlerts.class, "paymentConfirmed(long)");
        try {
            if (!enabled()) return;
            jdbc.update(
                    """
INSERT INTO staff_order_alerts(environment, event_key, order_id, branch_id, kind, required_permission, title, message)
SELECT ?, 'paid:' || o.id, o.id, o.branch_id, 'NEW_ORDER', 'ORDER_VIEW', LEFT('New paid order · ' || b.name, 160),
  'Order ' || COALESCE('#' || o.customer_order_number::text,o.order_number) || ' is confirmed. ' || CASE WHEN s.id IS NULL THEN 'Open the delivery order for its service window.'
  ELSE 'Pickup ' || to_char(s.slot_date, 'DD Mon YYYY') || ', ' || to_char(s.start_time, 'HH12:MI AM') || ' IST. Check the preparation queue.' END
FROM payments p JOIN orders o ON o.id = p.order_id JOIN branches b ON b.id = o.branch_id
LEFT JOIN pickup_slots s ON s.id = o.pickup_slot_id
WHERE p.id = ? AND p.payment_status = 'PAID' AND o.order_status = 'CONFIRMED'
ON CONFLICT(environment, event_key) DO NOTHING
""",
                    scope(),
                    paymentId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffOrderAlerts.class, "paymentConfirmed(long)");
        }
    }

    /**
     * Pickups transferred.
     *
     * @param order the order
     * @param requestKey the request key
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void pickupTransferred(Order order, java.util.UUID requestKey) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffOrderAlerts.class, "pickupTransferred(Order,java.util.UUID)");
        try {
            if (!enabled()) return;
            jdbc.update(
                    """
INSERT INTO staff_order_alerts(environment,event_key,order_id,branch_id,kind,required_permission,title,message)
VALUES (?,?,?,?,'NEW_ORDER','ORDER_VIEW','Pickup order transferred to your branch',?)
ON CONFLICT(environment,event_key) DO NOTHING
""",
                    scope(),
                    "transfer:" + requestKey,
                    order.getId(),
                    order.getBranch().getId(),
                    "Order "
                            + (order.getCustomerOrderNumber() == null
                                    ? order.getOrderNumber()
                                    : "#" + order.getCustomerOrderNumber())
                            + " · "
                            + order.getBranch().getName()
                            + ". Check its updated pickup time and preparation queue.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffOrderAlerts.class,
                    "pickupTransferred(Order,java.util.UUID)");
        }
    }

    /**
     * Occasions changed.
     *
     * @param id the id
     * @param advance the advance
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void occasionChanged(java.util.UUID id, boolean advance) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffOrderAlerts.class, "occasionChanged(java.util.UUID,boolean)");
        try {
            if (!enabled()) return;
            jdbc.update(
                    """
INSERT INTO staff_order_alerts(environment,event_key,enquiry_id,branch_id,kind,required_permission,title,message)
SELECT environment,?||id,id,branch_id,?,'ORDER_VIEW',?,
 occasion_type||' · '||to_char(service_date,'DD Mon YYYY')||' IST. '||?
FROM occasion_enquiries WHERE id=? AND environment=?
ON CONFLICT(environment,event_key) DO NOTHING
""",
                    advance ? "occasion-advance:" : "occasion-request:",
                    advance ? "OCCASION_ADVANCE_PAID" : "NEW_OCCASION_REQUEST",
                    advance
                            ? "Bulk advance verified · plan production"
                            : "New occasion request · review quote",
                    advance
                            ? "Review the dedicated production totals and packing plan."
                            : "Review quantities, packing and readiness before quoting.",
                    id,
                    scope());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffOrderAlerts.class,
                    "occasionChanged(java.util.UUID,boolean)");
        }
    }

    /**
     * Occasions reviewed.
     *
     * @param id the id
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void occasionReviewed(java.util.UUID id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffOrderAlerts.class, "occasionReviewed(java.util.UUID)");
        try {
            if (!enabled()) return;
            jdbc.update(
                    "INSERT INTO staff_order_alert_reads(event_id,staff_id) SELECT e.id,u.id FROM"
                        + " staff_order_alerts e JOIN staff_users u ON TRUE WHERE e.environment=?"
                        + " AND e.enquiry_id=? AND e.kind='NEW_OCCASION_REQUEST' AND "
                            + ELIGIBLE
                            + " ON CONFLICT DO NOTHING",
                    scope(),
                    id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffOrderAlerts.class,
                    "occasionReviewed(java.util.UUID)");
        }
    }

    /** Generates reminders. */
    @Transactional
    public void generateReminders() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffOrderAlerts.class, "generateReminders()");
        try {
            if (!enabled()) return;
            // Process all live waiting/preparing orders in bounded keyset pages; no dependency on
            // portal traffic.
            long after = 0;
            while (true) {
                var ids =
                        jdbc.query(
                                "SELECT id FROM orders WHERE id > ? AND order_status IN"
                                        + " ('CONFIRMED','PREPARING') ORDER BY id LIMIT 100",
                                (rs, row) -> rs.getLong(1),
                                after);
                if (ids.isEmpty()) return;
                for (long id : ids) {
                    Order order = orders.findById(id).orElse(null);
                    if (order == null) continue;
                    try {
                        var schedule = schedule(order);
                        LocalDateTime now = now(),
                                due = schedule.eligibleAt(),
                                pickup = schedule.pickupAt();
                        if (order.getOrderStatus() == OrderStatus.CONFIRMED) {
                            if (!now.isBefore(due.minusMinutes(reminderMinutes()))
                                    && now.isBefore(due))
                                record(
                                        order,
                                        "PREPARATION_SOON",
                                        "ORDER_START_PREPARATION",
                                        due,
                                        "Preparation opens soon",
                                        "Preparation opens at "
                                                + TIME.format(due)
                                                + " IST. Check ingredients and the queue; start"
                                                + " only when eligible.");
                            else if (!now.isBefore(due) && now.isBefore(pickup))
                                record(
                                        order,
                                        "PREPARATION_DUE",
                                        "ORDER_START_PREPARATION",
                                        due,
                                        "Time to start preparation",
                                        "The preparation window is open. Start preparation for"
                                                + " pickup/service at "
                                                + TIME.format(pickup)
                                                + " IST.");
                        }
                        if (!now.isBefore(pickup)
                                && (now.isBefore(pickup.plusDays(1))
                                        || properties.isRecurringPreparationReminders())) {
                            boolean waiting = order.getOrderStatus() == OrderStatus.CONFIRMED;
                            record(
                                    order,
                                    waiting ? "PREPARATION_OVERDUE" : "READY_OVERDUE",
                                    waiting ? "ORDER_START_PREPARATION" : "ORDER_MARK_READY",
                                    pickup,
                                    waiting
                                            ? "Preparation is overdue"
                                            : "Order is not ready on time",
                                    "Booked pickup/service time "
                                            + TIME.format(pickup)
                                            + " IST has passed. "
                                            + (waiting
                                                    ? "Preparation has not started."
                                                    : "The order is still preparing.")
                                            + " Take action and communicate any revised estimate to"
                                            + " the customer.");
                        }
                    } catch (IllegalStateException incompleteSchedule) {
                        // A missing legacy window must not prevent other branches' reminders. The
                        // order stays in its queue.
                    }
                }
                after = ids.getLast();
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffOrderAlerts.class, "generateReminders()");
        }
    }

    /**
     * Records the operation.
     *
     * @param order the order
     * @param kind the kind
     * @param permission the permission
     * @param scheduled the scheduled
     * @param title the title
     * @param message the message
     */
    private void record(
            Order order,
            String kind,
            String permission,
            LocalDateTime scheduled,
            String title,
            String message) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffOrderAlerts.class,
                        "record(Order,String,String,LocalDateTime,String,String)");
        try {
            jdbc.update(
                    """
INSERT INTO staff_order_alerts(environment, event_key, order_id, branch_id, kind, required_permission,
  title, message, scheduled_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(environment, event_key) DO NOTHING
""",
                    scope(),
                    reminderKey(order.getId(), kind, scheduled)
                            + (Boolean.TRUE.equals(
                                            jdbc.queryForObject(
                                                    "SELECT EXISTS(SELECT 1 FROM order_corrections"
                                                        + " WHERE order_id=? AND kind='TRANSFER')",
                                                    Boolean.class,
                                                    order.getId()))
                                    ? ":branch:" + order.getBranch().getId()
                                    : ""),
                    order.getId(),
                    order.getBranch().getId(),
                    kind,
                    permission,
                    title,
                    "Order "
                            + (order.getCustomerOrderNumber() == null
                                    ? order.getOrderNumber()
                                    : "#" + order.getCustomerOrderNumber())
                            + " · "
                            + order.getBranch().getName()
                            + ". "
                            + message,
                    Timestamp.valueOf(scheduled));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffOrderAlerts.class,
                    "record(Order,String,String,LocalDateTime,String,String)");
        }
    }

    /**
     * Reminders key.
     *
     * @param orderId the order id
     * @param kind the kind
     * @param scheduled the scheduled
     * @return the reminder key result
     */
    String reminderKey(long orderId, String kind, LocalDateTime scheduled) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffOrderAlerts.class, "reminderKey(long,String,LocalDateTime)");
        try {
            String key = orderId + ":" + kind + ":" + scheduled;
            if (properties.isRecurringPreparationReminders()
                    && List.of("PREPARATION_DUE", "PREPARATION_OVERDUE", "READY_OVERDUE")
                            .contains(kind)) {
                int minutes = Math.max(2, Math.min(30, properties.getRepeatMinutes()));
                long elapsed =
                        Math.max(0, java.time.Duration.between(scheduled, now()).toMinutes());
                key += ":repeat:" + (elapsed / minutes);
            }
            return key;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffOrderAlerts.class,
                    "reminderKey(long,String,LocalDateTime)");
        }
    }

    /**
     * Schedules the operation.
     *
     * @param order the order
     * @return the schedule result
     */
    private PreparationEligibility schedule(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffOrderAlerts.class, "schedule(Order)");
        try {
            // Evaluate a snapshot so a PREPARING entity is never mutated back to CONFIRMED.
            Order view = new Order();
            view.setOrderStatus(OrderStatus.CONFIRMED);
            view.setBranch(order.getBranch());
            view.setPickupSlot(order.getPickupSlot());
            view.setPickupType(order.getPickupType());
            view.setFulfillmentType(order.getFulfillmentType());
            view.setDeliveryWindowId(order.getDeliveryWindowId());
            return preparation.evaluate(view, now());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffOrderAlerts.class, "schedule(Order)");
        }
    }

    /**
     * Actionables the operation.
     *
     * @param event the event
     * @return the actionable result
     */
    @Transactional(readOnly = true)
    public boolean actionable(Event event) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffOrderAlerts.class, "actionable(Event)");
        try {
            if (event.enquiryId() != null)
                return Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM occasion_enquiries WHERE id=? AND"
                                        + " environment=? AND status IN "
                                        + (event.kind().equals("NEW_OCCASION_REQUEST")
                                                ? "('REQUESTED')"
                                                : "('PAID','CONFIRMED') AND EXISTS(SELECT 1 FROM"
                                                        + " occasion_production_allocations a WHERE"
                                                        + " a.enquiry_id=occasion_enquiries.id AND"
                                                        + " a.state='COMMITTED' AND"
                                                        + " a.production_approved_at IS NULL AND"
                                                        + " a.ready_quantity=0)")
                                        + ")",
                                Boolean.class,
                                event.enquiryId(),
                                scope()));
            Order order = orders.findById(event.orderId()).orElse(null);
            if (order == null || order.getBranch().getId() != event.branchId()) return false;
            if (event.kind().equals("NEW_ORDER"))
                return order.getOrderStatus() == OrderStatus.CONFIRMED;
            if (order.getOrderStatus() != OrderStatus.CONFIRMED
                    && order.getOrderStatus() != OrderStatus.PREPARING) return false;
            try {
                var plan = schedule(order);
                var now = now();
                return switch (event.kind()) {
                    case "PREPARATION_SOON" ->
                            order.getOrderStatus() == OrderStatus.CONFIRMED
                                    && plan.eligibleAt().equals(event.scheduledAt())
                                    && !now.isBefore(
                                            plan.eligibleAt().minusMinutes(reminderMinutes()))
                                    && now.isBefore(plan.eligibleAt());
                    case "PREPARATION_DUE" ->
                            order.getOrderStatus() == OrderStatus.CONFIRMED
                                    && plan.eligibleAt().equals(event.scheduledAt())
                                    && !now.isBefore(plan.eligibleAt())
                                    && now.isBefore(plan.pickupAt());
                    case "PREPARATION_OVERDUE" ->
                            order.getOrderStatus() == OrderStatus.CONFIRMED
                                    && plan.pickupAt().equals(event.scheduledAt())
                                    && !now.isBefore(plan.pickupAt())
                                    && (properties.isRecurringPreparationReminders()
                                            || now.isBefore(plan.pickupAt().plusDays(1)));
                    case "READY_OVERDUE" ->
                            order.getOrderStatus() == OrderStatus.PREPARING
                                    && plan.pickupAt().equals(event.scheduledAt())
                                    && !now.isBefore(plan.pickupAt())
                                    && (properties.isRecurringPreparationReminders()
                                            || now.isBefore(plan.pickupAt().plusDays(1)));
                    default -> false;
                };
            } catch (IllegalStateException incomplete) {
                return false;
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffOrderAlerts.class, "actionable(Event)");
        }
    }

    /**
     * Events the operation.
     *
     * @param rs the rs
     * @return the event result
     * @throws java.sql.SQLException if the operation cannot complete
     */
    public static Event event(java.sql.ResultSet rs) throws java.sql.SQLException {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffOrderAlerts.class, "event(java.sql.ResultSet)");
        try {
            var scheduled = rs.getTimestamp("scheduled_at");
            return new Event(
                    rs.getLong("id"),
                    rs.getLong("order_id"),
                    rs.getString("order_number"),
                    rs.getLong("branch_id"),
                    rs.getString("kind"),
                    rs.getString("title"),
                    rs.getString("message"),
                    scheduled == null ? null : scheduled.toLocalDateTime(),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getObject("enquiry_id", java.util.UUID.class),
                    rs.getObject("enquiry_id") == null
                            ? "/admin/orders/"
                                    + java.net.URLEncoder.encode(
                                            rs.getString("order_number"),
                                            java.nio.charset.StandardCharsets.UTF_8)
                            : "/admin/occasion-enquiries?branch="
                                    + rs.getLong("branch_id")
                                    + "&enquiry="
                                    + rs.getObject("enquiry_id"),
                    rs.getObject("customer_order_number", Long.class));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffOrderAlerts.class, "event(java.sql.ResultSet)");
        }
    }

    /**
     * Pages the operation.
     *
     * @param staffId the staff id
     * @param before the before
     * @return the page result
     */
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page page(long staffId, Long before) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffOrderAlerts.class, "page(long,Long)");
        try {
            return page(staffId, before, false, "");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffOrderAlerts.class, "page(long,Long)");
        }
    }

    /**
     * Pages the operation.
     *
     * @param staffId the staff id
     * @param before the before
     * @param unreadOnly the unread only
     * @param search the search
     * @return the page result
     */
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page page(long staffId, Long before, boolean unreadOnly, String search) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffOrderAlerts.class, "page(long,Long,boolean,String)");
        try {
            String query = search == null ? "" : search.trim();
            if (query.length() > 100)
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST);
            if (before != null && before <= 0)
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST);
            var messages =
                    jdbc.query(
                            """
SELECT e.*, COALESCE(o.order_number,'Request '||LEFT(e.enquiry_id::text,8)) order_number, o.customer_order_number, r.read_at,
  (SELECT state FROM staff_alert_deliveries d WHERE d.event_id = e.id AND d.staff_id = u.id AND d.channel = 'PUSH' ORDER BY d.id DESC LIMIT 1) push_state,
  (SELECT state FROM staff_alert_deliveries d WHERE d.event_id = e.id AND d.staff_id = u.id AND d.channel = 'EMAIL') email_state
FROM staff_order_alerts e LEFT JOIN orders o ON o.id = e.order_id JOIN staff_users u ON u.id = ?
LEFT JOIN staff_order_alert_reads r ON r.event_id = e.id AND r.staff_id = u.id
WHERE e.environment = ? AND e.id < ? AND (NOT ? OR r.read_at IS NULL)
  AND (? = '' OR strpos(lower(COALESCE(o.order_number,e.enquiry_id::text) || ' ' || COALESCE(o.customer_order_number::text,'') || ' ' || e.title || ' ' || e.message), lower(?)) > 0) AND
"""
                                    + ELIGIBLE
                                    + " ORDER BY e.id DESC LIMIT 31",
                            (rs, row) -> {
                                var event = event(rs);
                                var read = rs.getTimestamp("read_at");
                                return new Message(
                                        event,
                                        read == null ? null : read.toInstant(),
                                        actionable(event),
                                        rs.getString("push_state"),
                                        rs.getString("email_state"));
                            },
                            staffId,
                            scope(),
                            before == null ? Long.MAX_VALUE : before,
                            unreadOnly,
                            query,
                            query);
            Long unread =
                    jdbc.queryForObject(
                            "SELECT COUNT(DISTINCT COALESCE(e.order_id::text,e.enquiry_id::text))"
                                + " FROM staff_order_alerts e JOIN staff_users u ON u.id = ? WHERE"
                                + " e.environment = ? AND "
                                    + ELIGIBLE
                                    + " AND NOT EXISTS (SELECT 1 FROM staff_order_alert_reads r"
                                    + " WHERE r.event_id = e.id AND r.staff_id = u.id)",
                            Long.class,
                            staffId,
                            scope());
            return new Page(
                    messages.stream().limit(30).toList(),
                    unread == null ? 0 : unread,
                    messages.size() > 30 ? messages.get(29).event().id() : null,
                    jdbc.queryForObject(
                            "SELECT COALESCE(MAX(e.id),0) FROM staff_order_alerts e JOIN"
                                    + " staff_users u ON u.id=? WHERE e.environment=? AND "
                                    + ELIGIBLE,
                            Long.class,
                            staffId,
                            scope()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffOrderAlerts.class,
                    "page(long,Long,boolean,String)");
        }
    }

    /**
     * Marks read.
     *
     * @param staffId the staff id
     * @param eventId the event id
     */
    @Transactional
    public void markRead(long staffId, long eventId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffOrderAlerts.class, "markRead(long,long)");
        try {
            // Keep the authorized parent alive until its read record commits. Cleanup skips this
            // lock.
            var allowed =
                    jdbc.queryForList(
                            "SELECT e.id FROM staff_order_alerts e JOIN staff_users u ON u.id = ?"
                                    + " WHERE e.id = ? AND e.environment = ? AND "
                                    + ELIGIBLE
                                    + " FOR KEY SHARE OF e",
                            Long.class,
                            staffId,
                            eventId,
                            scope());
            if (allowed.isEmpty())
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND);
            jdbc.update(
                    "INSERT INTO staff_order_alert_reads(event_id, staff_id) VALUES (?, ?) ON"
                            + " CONFLICT DO NOTHING",
                    eventId,
                    staffId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffOrderAlerts.class, "markRead(long,long)");
        }
    }

    /**
     * Marks all read.
     *
     * @param staffId the staff id
     * @param throughId the through id
     */
    @Transactional
    public void markAllRead(long staffId, long throughId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffOrderAlerts.class, "markAllRead(long,long)");
        try {
            if (throughId <= 0)
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST);
            // Skip alerts already being cleaned; retain parent locks for every inserted read
            // record.
            jdbc.update(
                    "INSERT INTO staff_order_alert_reads(event_id,staff_id) SELECT e.id,u.id FROM"
                        + " staff_order_alerts e JOIN staff_users u ON u.id=? WHERE e.environment=?"
                        + " AND e.id<=? AND "
                            + ELIGIBLE
                            + " FOR KEY SHARE OF e SKIP LOCKED ON CONFLICT DO NOTHING",
                    staffId,
                    scope(),
                    throughId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffOrderAlerts.class, "markAllRead(long,long)");
        }
    }

    /**
     * Eligibles the operation.
     *
     * @param eventId the event id
     * @param staffId the staff id
     * @return the eligible result
     */
    public boolean eligible(long eventId, long staffId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffOrderAlerts.class, "eligible(long,long)");
        try {
            return Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS (SELECT 1 FROM staff_order_alerts e JOIN staff_users u"
                                    + " ON u.id = ? WHERE e.id = ? AND e.environment = ? AND "
                                    + ELIGIBLE
                                    + ")",
                            Boolean.class,
                            staffId,
                            eventId,
                            scope()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffOrderAlerts.class, "eligible(long,long)");
        }
    }
}
