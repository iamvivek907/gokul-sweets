package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.Set;

@Service @RequiredArgsConstructor
public class PickupCodeService {
    private final JdbcTemplate jdbc;
    private final PickupCodeAttempts attempts;
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final Set<String> ELIGIBLE=Set.of("CONFIRMED","PREPARING","READY_FOR_PICKUP","PICKUP_WINDOW_EXPIRED");
    public record CustomerCode(String code) {}
    @Transactional
    public CustomerCode customerCode(String orderNumber) {
        var rows=jdbc.query("SELECT o.id,o.order_status,o.fulfillment_type,(EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status='PAID') AND (SELECT COALESCE(SUM(p.amount),0) FROM payments p WHERE p.order_id=o.id AND p.payment_status='PAID')>=o.total_amount) FROM orders o WHERE o.order_number=?",
            (rs,n)->new Object[]{rs.getLong(1),rs.getString(2),rs.getString(3),rs.getBoolean(4)},orderNumber);
        if(rows.isEmpty())throw new IllegalArgumentException("Order does not exist.");
        var row=rows.getFirst();
        if(!ELIGIBLE.contains((String)row[1])||!"PICKUP".equals(row[2])||!Boolean.TRUE.equals(row[3]))return new CustomerCode(null);
        long id=(Long)row[0];issue(id);
        return new CustomerCode(jdbc.queryForObject("SELECT CASE WHEN consumed_at IS NULL THEN code END FROM order_pickup_codes WHERE order_id=?",String.class,id));
    }
    @Transactional
    public void issueForPaidPayment(long paymentId) {
        var ids=jdbc.queryForList("SELECT o.id FROM orders o JOIN payments p ON p.order_id=o.id WHERE p.id=? AND p.payment_status='PAID' AND (SELECT COALESCE(SUM(paid.amount),0) FROM payments paid WHERE paid.order_id=o.id AND paid.payment_status='PAID')>=o.total_amount AND o.fulfillment_type='PICKUP' AND o.order_status IN ('CONFIRMED','PREPARING','READY_FOR_PICKUP','PICKUP_WINDOW_EXPIRED')",Long.class,paymentId);
        ids.forEach(this::issue);
    }
    private void issue(long id) {
        String code=String.format(java.util.Locale.ROOT,"%04d",RANDOM.nextInt(10000));
        jdbc.update("INSERT INTO order_pickup_codes(order_id,code) VALUES (?,?) ON CONFLICT(order_id) DO NOTHING",id,code);
    }
    /** Caller holds the order row lock, so verification and fulfilment cannot race. */
    public void verifyAndConsume(Order order,String code) {
        if(order.getFulfillmentType()==FulfillmentType.DELIVERY||!(order.getOrderStatus()==OrderStatus.READY_FOR_PICKUP||order.getOrderStatus()==OrderStatus.PICKUP_WINDOW_EXPIRED))throw new IllegalStateException("Only a ready pickup order can be handed over.");
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM orders o WHERE o.id=? AND EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status='PAID') AND (SELECT COALESCE(SUM(p.amount),0) FROM payments p WHERE p.order_id=o.id AND p.payment_status='PAID')>=o.total_amount)",Boolean.class,order.getId())))throw new IllegalStateException("Payment must be confirmed before pickup.");
        String error=attempts.check(order.getId(),code);
        if(error!=null)throw new IllegalArgumentException(error);
        if(jdbc.update("UPDATE order_pickup_codes SET consumed_at=CURRENT_TIMESTAMP WHERE order_id=? AND consumed_at IS NULL",order.getId())!=1)throw new IllegalStateException("This pickup code has already been used.");
    }
}
