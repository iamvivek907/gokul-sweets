package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.config.PreparationWindowProperties;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

/** Read-only branch plan; all actions still use the lifecycle coordinator's eligibility/locks/KOT. */
@Service @RequiredArgsConstructor
public class KitchenPlanningService {
    private final JdbcTemplate jdbc;
    private final StaffAuthorizationService staff;
    private final EnhancementProperties flags;
    private final PreparationWindowProperties windows;
    private final ApplicationClock clock;
    public enum Filter {ALL, OVERDUE, ELIGIBLE, SCHEDULED, PREPARING, READY, WAITING, IN_PROGRESS, HANDOVER}
    public record Row(String orderNumber, Long customerOrderNumber, String customerName, String fulfillmentType, String orderStatus,
                      String bucket, LocalDate date, LocalTime start, LocalTime end, LocalDateTime preparationAt, boolean earlyPreparation, List<Item> items) {}
    public record Item(long productId, String productName, String saleMode, int quantity, Integer weightGrams) {}
    public record Slot(LocalDate date, LocalTime start, LocalTime end, String fulfillmentType,
                       long waiting, long preparing, long ready, long total) {}
    public record Plan(List<Row> orders, List<Slot> slots, Map<String,Long> counts, int page, long total, LocalDateTime generatedAt) {}
    public record AlertCounts(long needsPreparation, long readyOverdue) {}
    private static final String BASE="""
        WITH timed AS (
          SELECT o.id,o.order_number,o.customer_order_number,o.customer_name,o.fulfillment_type,o.order_status,
           (o.fulfillment_type<>'DELIVERY' AND EXISTS(SELECT 1 FROM order_items i WHERE i.order_id=o.id)
             AND NOT EXISTS(SELECT 1 FROM order_items i LEFT JOIN branch_products bp ON bp.branch_id=o.branch_id AND bp.product_id=i.product_id
                WHERE i.order_id=o.id AND NOT COALESCE(bp.early_preparation_allowed,FALSE))) early_preparation,
           CASE WHEN o.fulfillment_type='DELIVERY' THEN w.service_date ELSE s.slot_date END service_date,
           CASE WHEN o.fulfillment_type='DELIVERY' THEN w.starts_at ELSE s.start_time END starts_at,
           CASE WHEN o.fulfillment_type='DELIVERY' THEN w.ends_at ELSE s.end_time END ends_at,
           CASE WHEN o.fulfillment_type='DELIVERY' THEN ? WHEN o.pickup_type='PRIORITY' THEN ?
                WHEN o.pickup_type='ADMIN_OVERRIDE' THEN ? ELSE ? END lead
          FROM orders o LEFT JOIN pickup_slots s ON s.id=o.pickup_slot_id
          LEFT JOIN delivery_capacity_windows w ON w.id=o.delivery_window_id
          LEFT JOIN delivery_zones z ON z.id=w.zone_id AND z.branch_id=o.branch_id
          WHERE o.branch_id=? AND o.order_status IN ('CONFIRMED','PREPARING','READY_FOR_PICKUP','READY_FOR_DELIVERY')
            AND (o.fulfillment_type<>'DELIVERY' OR z.id IS NOT NULL)
        ), planned AS (
          SELECT *, CASE WHEN early_preparation THEN LEAST(service_date::timestamp,service_date+starts_at-(lead*INTERVAL '1 minute'))
            ELSE service_date+starts_at-(lead*INTERVAL '1 minute') END preparation_at,
            CASE WHEN order_status IN ('READY_FOR_PICKUP','READY_FOR_DELIVERY') THEN 'READY'
            WHEN service_date+starts_at<=? THEN 'OVERDUE'
            WHEN order_status='PREPARING' THEN 'PREPARING'
            WHEN CASE WHEN early_preparation THEN LEAST(service_date::timestamp,service_date+starts_at-(lead*INTERVAL '1 minute')) ELSE service_date+starts_at-(lead*INTERVAL '1 minute') END<=? THEN 'ELIGIBLE' ELSE 'SCHEDULED' END bucket
          FROM timed WHERE service_date IS NOT NULL AND starts_at IS NOT NULL
        )
        """;
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Plan get(long branchId, Filter filter, LocalDate date, LocalTime start, int page) {
        if(!flags.isAdminPreparationBoard())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(branchId<=0||page<0||page>100000||start!=null&&date==null)throw new IllegalArgumentException("Choose a branch, date and valid page.");
        staff.requireBranchAccess(branchId);
        var now=clock.now();
        var params=new ArrayList<Object>(List.of(windows.getDeliveryLeadMinutes(),windows.getPriorityLeadMinutes(),windows.getAdminOverrideLeadMinutes(),windows.getNormalLeadMinutes(),branchId,Timestamp.valueOf(now),Timestamp.valueOf(now)));
        var slots=jdbc.query(BASE+"SELECT service_date,starts_at,ends_at,fulfillment_type,COUNT(*) FILTER(WHERE order_status='CONFIRMED'),COUNT(*) FILTER(WHERE order_status='PREPARING'),COUNT(*) FILTER(WHERE order_status IN ('READY_FOR_PICKUP','READY_FOR_DELIVERY')),COUNT(*) FROM planned GROUP BY service_date,starts_at,ends_at,fulfillment_type ORDER BY service_date,starts_at,fulfillment_type",
            (rs,n)->new Slot(rs.getObject(1,LocalDate.class),rs.getObject(2,LocalTime.class),rs.getObject(3,LocalTime.class),rs.getString(4),rs.getLong(5),rs.getLong(6),rs.getLong(7),rs.getLong(8)),params.toArray());
        String scope=" WHERE 1=1";
        if(date!=null){scope+=" AND service_date=?";params.add(date);}
        if(start!=null){scope+=" AND starts_at=?";params.add(start);}
        var counts=new LinkedHashMap<String,Long>();for(var value:Filter.values())counts.put(value.name(),0L);
        // Bucket and operational-lane totals share one classification scan. Lane counts overlap
        // bucket counts, so accumulate ALL from bucket totals only.
        jdbc.query(BASE+"""
            SELECT bucket,COUNT(*),
                   COUNT(*) FILTER(WHERE order_status='CONFIRMED' AND bucket IN ('ELIGIBLE','OVERDUE')),
                   COUNT(*) FILTER(WHERE order_status='PREPARING'),
                   COUNT(*) FILTER(WHERE order_status='READY_FOR_PICKUP' AND fulfillment_type='PICKUP')
            FROM planned
            """+scope+" GROUP BY bucket",rs->{
                long bucketTotal=rs.getLong(2);
                counts.put(rs.getString(1),bucketTotal);
                counts.merge("ALL",bucketTotal,Long::sum);
                counts.merge("WAITING",rs.getLong(3),Long::sum);
                counts.merge("IN_PROGRESS",rs.getLong(4),Long::sum);
                counts.merge("HANDOVER",rs.getLong(5),Long::sum);
            },params.toArray());
        switch(filter) {
            case WAITING -> scope+=" AND order_status='CONFIRMED' AND bucket IN ('ELIGIBLE','OVERDUE')";
            case IN_PROGRESS -> scope+=" AND order_status='PREPARING'";
            case HANDOVER -> scope+=" AND order_status='READY_FOR_PICKUP' AND fulfillment_type='PICKUP'";
            case ALL -> { }
            default -> {scope+=" AND bucket=?";params.add(filter.name());}
        }
        long total=counts.get(filter.name());params.add(20);params.add(page*20);
        var rows=jdbc.query(BASE+"SELECT order_number,customer_name,fulfillment_type,order_status,bucket,service_date,starts_at,ends_at,preparation_at,customer_order_number,early_preparation FROM planned"+scope+" ORDER BY service_date,starts_at,id LIMIT ? OFFSET ?",
            (rs,n)->new Row(rs.getString(1),rs.getObject(10,Long.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getObject(6,LocalDate.class),rs.getObject(7,LocalTime.class),rs.getObject(8,LocalTime.class),rs.getObject(9,LocalDateTime.class),rs.getBoolean(11),List.of()),params.toArray());
        if(!rows.isEmpty()) {
            var items=new HashMap<String,List<Item>>();
            jdbc.query("SELECT o.order_number,i.product_id,i.product_name,i.sale_mode,i.quantity,i.weight_grams FROM order_items i JOIN orders o ON o.id=i.order_id WHERE o.branch_id=? AND o.order_number IN ("+String.join(",",Collections.nCopies(rows.size(),"?"))+") ORDER BY i.id",
                rs->{items.computeIfAbsent(rs.getString(1),key->new ArrayList<>()).add(new Item(rs.getLong(2),rs.getString(3),rs.getString(4),rs.getInt(5),rs.getObject(6,Integer.class)));},
                java.util.stream.Stream.concat(java.util.stream.Stream.of(branchId),rows.stream().map(Row::orderNumber)).toArray());
            rows=rows.stream().map(r->new Row(r.orderNumber(),r.customerOrderNumber(),r.customerName(),r.fulfillmentType(),r.orderStatus(),r.bucket(),r.date(),r.start(),r.end(),r.preparationAt(),r.earlyPreparation(),items.getOrDefault(r.orderNumber(),List.of()))).toList();
        }
        return new Plan(rows,slots,counts,page,total,now);
    }

    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    @Transactional(readOnly=true)
    public AlertCounts alerts(long branchId) {
        if (!flags.isAdminPreparationBoard()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (branchId <= 0) throw new IllegalArgumentException("Choose a valid branch.");
        staff.requireBranchAccess(branchId);
        var now = Timestamp.valueOf(clock.now());
        return jdbc.queryForObject(BASE + """
            SELECT COUNT(*) FILTER(WHERE order_status='CONFIRMED' AND bucket IN ('ELIGIBLE','OVERDUE')),
                   COUNT(*) FILTER(WHERE order_status='PREPARING' AND bucket='OVERDUE') FROM planned
            """, (rs, row) -> new AlertCounts(rs.getLong(1), rs.getLong(2)),
            windows.getDeliveryLeadMinutes(), windows.getPriorityLeadMinutes(), windows.getAdminOverrideLeadMinutes(),
            windows.getNormalLeadMinutes(), branchId, now, now);
    }
}
