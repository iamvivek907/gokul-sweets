package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.time.*;
import java.util.*;

/** Committed demand, not available stock; the entire range is aggregated in the database. */
@Service @RequiredArgsConstructor
public class OrderDemandService {
    private final JdbcTemplate jdbc;
    private final StaffAuthorizationService staff;
    private final EnhancementProperties flags;
    public record Demand(LocalDate date, long productId, String productName, String saleMode,
                         long ordered, long waiting, long preparing, long ready, long completed, long orderCount) {}
    public record Policy(long productId, String productName, boolean earlyPreparationAllowed) {}
    private static final String LINES="""
        SELECT o.order_number,o.customer_order_number,o.order_status,
         CASE WHEN o.fulfillment_type='DELIVERY' THEN w.service_date ELSE s.slot_date END service_date,
         CASE WHEN o.fulfillment_type='DELIVERY' THEN w.starts_at ELSE s.start_time END pickup_time,
         i.product_id,i.product_name,i.sale_mode,
         CASE WHEN i.sale_mode='WEIGHT' THEN i.weight_grams ELSE i.quantity END amount
        FROM orders o JOIN order_items i ON i.order_id=o.id
         LEFT JOIN pickup_slots s ON s.id=o.pickup_slot_id AND s.branch_id=o.branch_id
         LEFT JOIN delivery_capacity_windows w ON w.id=o.delivery_window_id
         LEFT JOIN delivery_zones z ON z.id=w.zone_id AND z.branch_id=o.branch_id
        WHERE o.branch_id=? AND o.order_status IN
         ('CONFIRMED','PREPARING','READY_FOR_PICKUP','READY_FOR_DELIVERY','PICKED_UP','OUT_FOR_DELIVERY','DELIVERED')
         AND (o.fulfillment_type<>'DELIVERY' OR z.id IS NOT NULL)
        """;
    private void authorize(long branchId) {
        if(!flags.isAdminPreparationBoard())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(branchId<=0)throw new IllegalArgumentException("Choose a branch.");
        var role=staff.getCurrentStaff().getRole().getName();
        if(!Set.of("OWNER_ADMIN","MANAGER").contains(role))throw new AccessDeniedException("Demand and exports are available to admin and manager only.");
        staff.requireBranchAccess(branchId);
    }
    private void range(LocalDate from,LocalDate to) {
        if(from==null||to==null||to.isBefore(from)||java.time.temporal.ChronoUnit.DAYS.between(from,to)>30)
            throw new IllegalArgumentException("Choose a date range of at most 31 days.");
    }
    private List<Demand> read(long branchId,LocalDate from,LocalDate to) {
        return jdbc.query("WITH lines AS ("+LINES+") SELECT service_date,product_id,product_name,sale_mode,SUM(amount),"+
            "COALESCE(SUM(amount) FILTER(WHERE order_status='CONFIRMED'),0),"+
            "COALESCE(SUM(amount) FILTER(WHERE order_status='PREPARING'),0),"+
            "COALESCE(SUM(amount) FILTER(WHERE order_status IN ('READY_FOR_PICKUP','READY_FOR_DELIVERY')),0),"+
            "COALESCE(SUM(amount) FILTER(WHERE order_status IN ('PICKED_UP','OUT_FOR_DELIVERY','DELIVERED')),0),COUNT(DISTINCT order_number)"+
            " FROM lines WHERE service_date BETWEEN ? AND ? GROUP BY service_date,product_id,product_name,sale_mode ORDER BY service_date,product_name,product_id,sale_mode",
            (rs,n)->new Demand(rs.getObject(1,LocalDate.class),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getLong(6),rs.getLong(7),rs.getLong(8),rs.getLong(9),rs.getLong(10)),branchId,from,to);
    }
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<Demand> get(long branchId,LocalDate from,LocalDate to) {
        authorize(branchId);range(from,to);return read(branchId,from,to);
    }
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public byte[] export(long branchId,LocalDate from,LocalDate to) {
        authorize(branchId);range(from,to);
        try(var workbook=new SXSSFWorkbook(100);var output=new ByteArrayOutputStream()) {
            var totals=workbook.createSheet("Daily quantities");
            write(totals,0,List.of("Branch ID","Pickup date (IST)","Product ID","Item","Unit","Ordered","Not started","Preparing","Ready","Completed or dispatched","Orders containing item"));
            int index=1;for(var d:read(branchId,from,to))write(totals,index++,List.of(branchId,d.date().toString(),d.productId(),d.productName(),d.saleMode().equals("WEIGHT")?"g":"pcs",d.ordered(),d.waiting(),d.preparing(),d.ready(),d.completed(),d.orderCount()));
            var details=workbook.createSheet("Order lines");
            write(details,0,List.of("Branch ID","Pickup date (IST)","Pickup time (IST)","Order number","Item","Product ID","Unit","Quantity","Status"));
            // Stream both PostgreSQL rows and spreadsheet rows; busy branches must not
            // retain an entire month of order lines and workbook cells in heap memory.
            int[] nextRow={1};
            jdbc.query(connection->{
                var statement=connection.prepareStatement("WITH lines AS ("+LINES+") SELECT * FROM lines WHERE service_date BETWEEN ? AND ? ORDER BY service_date,pickup_time,order_number,product_id");
                statement.setLong(1,branchId);statement.setObject(2,from);statement.setObject(3,to);statement.setFetchSize(500);return statement;
            },rs->{write(details,nextRow[0]++,Arrays.asList(branchId,rs.getObject("service_date").toString(),rs.getObject("pickup_time").toString(),
                Objects.toString(rs.getObject("customer_order_number"),rs.getString("order_number")),rs.getString("product_name"),rs.getLong("product_id"),
                "WEIGHT".equals(rs.getString("sale_mode"))?"g":"pcs",rs.getLong("amount"),rs.getString("order_status")));});
            totals.createFreezePane(0,1);details.createFreezePane(0,1);
            for(int col=0;col<11;col++)totals.setColumnWidth(col,22*256);
            for(int col=0;col<9;col++)details.setColumnWidth(col,22*256);
            workbook.write(output);return output.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Could not create the demand export. Try again.",e);}
    }
    private void write(org.apache.poi.ss.usermodel.Sheet sheet,int number,List<?> values) {
        var row=sheet.createRow(number);for(int i=0;i<values.size();i++){var cell=row.createCell(i);var value=values.get(i);if(value instanceof Number n)cell.setCellValue(n.doubleValue());else cell.setCellValue(Objects.toString(value,""));}
    }
    @PreAuthorize("hasAuthority('MENU_MANAGE')") @Transactional(readOnly=true)
    public List<Policy> policies(long branchId) {
        authorize(branchId);return jdbc.query("SELECT product_id,p.name,bp.early_preparation_allowed FROM branch_products bp JOIN products p ON p.id=bp.product_id WHERE bp.branch_id=? ORDER BY p.name",
            (rs,n)->new Policy(rs.getLong(1),rs.getString(2),rs.getBoolean(3)),branchId);
    }
    @PreAuthorize("hasAuthority('MENU_MANAGE')") @Transactional
    public void policy(long branchId,long productId,boolean allowed) {
        authorize(branchId);
        if(jdbc.update("UPDATE branch_products SET early_preparation_allowed=?,updated_at=CURRENT_TIMESTAMP WHERE branch_id=? AND product_id=?",allowed,branchId,productId)!=1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product is not in this branch.");
    }
}
