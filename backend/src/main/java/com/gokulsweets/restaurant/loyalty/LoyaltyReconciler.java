package com.gokulsweets.restaurant.loyalty;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/** Repairs interrupted lifecycle callbacks. Enrolment snapshot excludes historical/guest orders. */
@Component @RequiredArgsConstructor
public class LoyaltyReconciler {
 private final JdbcTemplate jdbc;
 private final LoyaltyService loyalty;
 private final LoyaltyProperties rules;
 @Scheduled(fixedDelayString="${gokul.loyalty.reconcile-delay-ms:15000}")
 public void reconcile(){
  var ids=jdbc.queryForList("""
   SELECT o.id FROM orders o WHERE
    (EXISTS(SELECT 1 FROM loyalty_holds h WHERE h.order_id=o.id AND h.state<>'RELEASED') AND
     (o.order_status IN ('CANCELLED','PAYMENT_FAILED') OR EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND
       (p.payment_status='REFUNDED' OR (p.payment_status='PAID' AND EXISTS(SELECT 1 FROM loyalty_holds h WHERE h.order_id=o.id AND h.state='RESERVED'))))))
    OR (o.loyalty_enrolled AND EXISTS(SELECT 1 FROM verified_order_ownership own WHERE own.order_id=o.id) AND
      ((o.order_status IN ('PICKED_UP','DELIVERED') AND EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status='PAID') AND
        NOT EXISTS(SELECT 1 FROM loyalty_ledger l WHERE l.order_id=o.id AND l.kind='EARNED')) OR
       ((o.order_status='CANCELLED' OR EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status='REFUNDED')) AND
         EXISTS(SELECT 1 FROM loyalty_ledger l WHERE l.order_id=o.id AND l.kind IN ('EARNED','WELCOME')) AND
         NOT EXISTS(SELECT 1 FROM loyalty_ledger l WHERE l.order_id=o.id AND l.kind='REVERSED'))))
   ORDER BY o.id LIMIT 500
   """,Long.class);
  for(long id:ids)loyalty.reconcile(id);
  var expiring=jdbc.queryForList("SELECT DISTINCT l.environment,l.subject_id FROM loyalty_lots p JOIN loyalty_ledger l ON l.id=p.ledger_id JOIN loyalty_accounts a ON a.environment=l.environment AND a.subject_id=l.subject_id WHERE p.remaining>0 AND GREATEST(l.expires_at,a.last_qualifying_activity+(?*INTERVAL '1 day'))<=CURRENT_TIMESTAMP LIMIT 500",rules.getExpiryDays());
  for(var account:expiring)loyalty.expireAccount((String)account.get("environment"),(java.util.UUID)account.get("subject_id"));
 }
}
