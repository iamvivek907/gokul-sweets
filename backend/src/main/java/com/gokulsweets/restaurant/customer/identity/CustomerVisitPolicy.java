package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.order.entity.Order;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class CustomerVisitPolicy {
  private final JdbcTemplate jdbc;

  public long completed(String environment, UUID subject) {
    return jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM verified_order_ownership own JOIN orders o ON o.id=own.order_id
        WHERE own.environment=? AND own.verified_subject_id=? AND o.order_status IN ('PICKED_UP','DELIVERED')
          AND EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status='PAID')
          AND NOT EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status IN ('REFUND_PENDING','REFUNDED','REFUND_FAILED'))
        """,
        Long.class,
        environment,
        subject);
  }

  public boolean offerEligible(long rebateId, Order order) {
    // The parent lock also protects a rule which has not been inserted yet.
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && !TransactionSynchronizationManager.isCurrentTransactionReadOnly())
      jdbc.queryForList("SELECT id FROM rebates WHERE id=? FOR SHARE", Long.class, rebateId);
    var minimum =
        jdbc
            .query(
                "SELECT minimum_completed_orders FROM rebate_visit_rules WHERE rebate_id=?",
                (rs, n) -> rs.getInt(1),
                rebateId)
            .stream()
            .findFirst()
            .orElse(0);
    if (minimum == 0) return true;
    // Draft quotes have no order id; only a validated phone-bound owner can qualify.
    if (order.getVerifiedOfferSubject() != null) {
      var subject = order.getVerifiedOfferSubject();
      return completed(subject.environment(), subject.id()) >= minimum;
    }
    var owners =
        order.getId() == null
            ? java.util.List.<java.util.Map<String, Object>>of()
            : jdbc.queryForList(
                "SELECT environment,verified_subject_id FROM verified_order_ownership WHERE"
                    + " order_id=?",
                order.getId());
    if (owners.isEmpty()) return false;
    var owner = owners.getFirst();
    return completed((String) owner.get("environment"), (UUID) owner.get("verified_subject_id"))
        >= minimum;
  }
}
