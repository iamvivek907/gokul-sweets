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
    public enum Filter {ALL, OVERDUE, ELIGIBLE, SCHEDULED, PREPARING, READY}
    public record Row(String orderNumber, String customerName, String fulfillmentType, String orderStatus,
                      String bucket, LocalDate date, LocalTime start, LocalTime end, LocalDateTime preparationAt) {}
    public record Slot(LocalDate date, LocalTime start, LocalTime end, String fulfillmentType,
                       long waiting, long preparing, long ready, long total) {}
    public record Plan(List<Row> orders, List<Slot> slots, Map<String,Long> counts, int page, long total, LocalDateTime generatedAt) {}
    private static final String BASE="""
        WITH timed AS (
          SELECT o.id,o.order_number,o.customer_name,o.fulfillment_type,o.order_status,
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
          SELECT *, service_date+starts_at-(lead*INTERVAL '1 minute') preparation_at,
            CASE WHEN order_status='PREPARING' THEN 'PREPARING' WHEN order_status IN ('READY_FOR_PICKUP','READY_FOR_DELIVERY') THEN 'READY'
            WHEN service_date+starts_at<=? THEN 'OVERDUE'
            WHEN service_date+starts_at-(lead*INTERVAL '1 minute')<=? THEN 'ELIGIBLE' ELSE 'SCHEDULED' END bucket
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
        jdbc.query(BASE+"SELECT bucket,COUNT(*) FROM planned"+scope+" GROUP BY bucket",rs->{counts.put(rs.getString(1),rs.getLong(2));},params.toArray());
        counts.put("ALL",counts.values().stream().mapToLong(Long::longValue).sum());
        if(filter!=Filter.ALL){scope+=" AND bucket=?";params.add(filter.name());}
        long total=counts.get(filter.name());params.add(20);params.add(page*20);
        var rows=jdbc.query(BASE+"SELECT order_number,customer_name,fulfillment_type,order_status,bucket,service_date,starts_at,ends_at,preparation_at FROM planned"+scope+" ORDER BY service_date,starts_at,id LIMIT ? OFFSET ?",
            (rs,n)->new Row(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getObject(6,LocalDate.class),rs.getObject(7,LocalTime.class),rs.getObject(8,LocalTime.class),rs.getObject(9,LocalDateTime.class)),params.toArray());
        return new Plan(rows,slots,counts,page,total,now);
    }
}
