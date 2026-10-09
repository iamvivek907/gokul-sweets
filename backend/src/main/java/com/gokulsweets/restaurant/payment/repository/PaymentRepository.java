package com.gokulsweets.restaurant.payment.repository;

import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence operations for payment records. */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Finds by order id order by created at desc.
     *
     * @param orderId the order id
     * @return the find by order id order by created at desc result
     */
    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    /**
     * Finds by provider payment id.
     *
     * @param providerPaymentId the provider payment id
     * @return the find by provider payment id result
     */
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    /**
     * Finds by provider and provider payment id.
     *
     * @param provider the provider
     * @param providerPaymentId the provider payment id
     * @return the find by provider and provider payment id result
     */
    Optional<Payment> findByProviderAndProviderPaymentId(
            PaymentProviderType provider, String providerPaymentId);

    /**
     * Finds by provider order id.
     *
     * @param providerOrderId the provider order id
     * @return the find by provider order id result
     */
    Optional<Payment> findByProviderOrderId(String providerOrderId);

    /**
     * Finds by provider and provider order id.
     *
     * @param provider the provider
     * @param providerOrderId the provider order id
     * @return the find by provider and provider order id result
     */
    Optional<Payment> findByProviderAndProviderOrderId(
            PaymentProviderType provider, String providerOrderId);

    /**
     * Finds by provider refund id.
     *
     * @param providerRefundId the provider refund id
     * @return the find by provider refund id result
     */
    Optional<Payment> findByProviderRefundId(String providerRefundId);

    /**
     * Finds by refund reference id.
     *
     * @param refundReferenceId the refund reference id
     * @return the find by refund reference id result
     */
    Optional<Payment> findByRefundReferenceId(String refundReferenceId);

    /**
     * Existses by order id and payment status.
     *
     * @param orderId the order id
     * @param paymentStatus the payment status
     * @return the exists by order id and payment status result
     */
    boolean existsByOrderIdAndPaymentStatus(Long orderId, PaymentStatus paymentStatus);

    /**
     * Existses by order id.
     *
     * @param orderId the order id
     * @return the exists by order id result
     */
    boolean existsByOrderId(Long orderId);

    /**
     * Performs the find top100 by payment status and expires at before order by expires at asc
     * operation for payment repository.
     *
     * @param paymentStatus the payment status
     * @param expiresAt the expires at
     * @return the find top100 by payment status and expires at before order by expires at asc
     *     result
     */
    List<Payment> findTop100ByPaymentStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            PaymentStatus paymentStatus, LocalDateTime expiresAt);

    /**
     * Performs the find top100 by payment status order by updated at asc operation for payment
     * repository.
     *
     * @param paymentStatus the payment status
     * @return the find top100 by payment status order by updated at asc result
     */
    List<Payment> findTop100ByPaymentStatusOrderByUpdatedAtAsc(PaymentStatus paymentStatus);

    /**
     * Finds due refund ids.
     *
     * @param now the now
     * @param page the page
     * @return the find due refund ids result
     */
    @Query(
            """
SELECT p.id FROM Payment p
WHERE p.paymentStatus = com.gokulsweets.restaurant.payment.enums.PaymentStatus.REFUND_PENDING
  AND (p.refundNextCheckAt IS NULL OR p.refundNextCheckAt <= :now)
ORDER BY p.refundNextCheckAt ASC NULLS FIRST, p.id ASC
""")
    List<Long> findDueRefundIds(@Param("now") LocalDateTime now, Pageable page);

    /**
     * Claims refund check.
     *
     * @param id the id
     * @param now the now
     * @param leaseUntil the lease until
     * @return the claim refund check result
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
UPDATE Payment p SET p.refundNextCheckAt = :leaseUntil
WHERE p.id = :id
  AND p.paymentStatus = com.gokulsweets.restaurant.payment.enums.PaymentStatus.REFUND_PENDING
  AND (p.refundNextCheckAt IS NULL OR p.refundNextCheckAt <= :now)
""")
    int claimRefundCheck(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    /**
     * Records refund submission attempt.
     *
     * @param id the id
     * @param now the now
     * @return the record refund submission attempt result
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
UPDATE Payment p SET p.refundSubmissionAttemptedAt = :now
WHERE p.id = :id
  AND p.paymentStatus = com.gokulsweets.restaurant.payment.enums.PaymentStatus.REFUND_PENDING
  AND p.refundSubmissionAttemptedAt IS NULL
""")
    int recordRefundSubmissionAttempt(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * Finishes refund check.
     *
     * @param id the id
     * @param leaseUntil the lease until
     * @param nextCheck the next check
     * @param failures the failures
     * @param review the review
     * @param reason the reason
     * @param now the now
     * @return the finish refund check result
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
UPDATE Payment p SET p.refundNextCheckAt = :nextCheck,
    p.refundCheckFailures = :failures, p.refundReviewRequired = :review,
    p.refundFailureReason = :reason, p.refundLastCheckedAt = :now
WHERE p.id = :id
  AND p.paymentStatus = com.gokulsweets.restaurant.payment.enums.PaymentStatus.REFUND_PENDING
  AND p.refundNextCheckAt = :leaseUntil
""")
    int finishRefundCheck(
            @Param("id") Long id,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("nextCheck") LocalDateTime nextCheck,
            @Param("failures") int failures,
            @Param("review") boolean review,
            @Param("reason") String reason,
            @Param("now") LocalDateTime now);

    /**
     * Finds refund by id for update.
     *
     * @param id the id
     * @return the find refund by id for update result
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findRefundByIdForUpdate(@Param("id") Long id);

    /**
     * Finds by id with order.
     *
     * @param paymentId the payment id
     * @return the find by id with order result
     */
    @EntityGraph(attributePaths = {"order"})
    @Query(
            """
            SELECT payment
            FROM Payment payment
            WHERE payment.id = :paymentId
            """)
    Optional<Payment> findByIdWithOrder(@Param("paymentId") Long paymentId);

    /**
     * Finds for order and status.
     *
     * @param orderNumber the order number
     * @param status the status
     * @return the find for order and status result
     */
    @EntityGraph(attributePaths = {"order"})
    @Query(
            """
            SELECT payment
            FROM Payment payment
            WHERE payment.order.orderNumber = :orderNumber
              AND payment.paymentStatus = :status
            ORDER BY payment.createdAt DESC, payment.id DESC
            """)
    List<Payment> findForOrderAndStatus(
            @Param("orderNumber") String orderNumber, @Param("status") PaymentStatus status);

    /**
     * Performs the find first by order order number order by created at desc operation for payment
     * repository.
     *
     * @param orderNumber the order number
     * @return the find first by order order number order by created at desc result
     */
    @EntityGraph(attributePaths = {"order"})
    Optional<Payment> findFirstByOrderOrderNumberOrderByCreatedAtDesc(String orderNumber);

    /**
     * Transitions status.
     *
     * @param paymentId the payment id
     * @param expectedStatus the expected status
     * @param newStatus the new status
     * @return the transition status result
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            """
            UPDATE Payment payment
            SET payment.paymentStatus = :newStatus
            WHERE payment.id = :paymentId
              AND payment.paymentStatus = :expectedStatus
            """)
    int transitionStatus(
            @Param("paymentId") Long paymentId,
            @Param("expectedStatus") PaymentStatus expectedStatus,
            @Param("newStatus") PaymentStatus newStatus);

    /**
     * Transitions status from any.
     *
     * @param paymentId the payment id
     * @param expectedStatuses the expected statuses
     * @param newStatus the new status
     * @return the transition status from any result
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            """
            UPDATE Payment payment
            SET payment.paymentStatus = :newStatus
            WHERE payment.id = :paymentId
              AND payment.paymentStatus IN :expectedStatuses
            """)
    int transitionStatusFromAny(
            @Param("paymentId") Long paymentId,
            @Param("expectedStatuses") Collection<PaymentStatus> expectedStatuses,
            @Param("newStatus") PaymentStatus newStatus);

    /**
     * Performs the find first by order id order by created at desc operation for payment
     * repository.
     *
     * @param orderId the order id
     * @return the find first by order id order by created at desc result
     */
    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);

    /**
     * Finds latest payments for orders.
     *
     * @param orderIds the order ids
     * @return the find latest payments for orders result
     */
    @Query(
            """
            SELECT payment
            FROM Payment payment
            WHERE payment.order.id IN :orderIds
              AND payment.id = (
                  SELECT MAX(other.id)
                  FROM Payment other
                  WHERE other.order.id = payment.order.id
              )
            """)
    List<Payment> findLatestPaymentsForOrders(@Param("orderIds") Collection<Long> orderIds);

    /**
     * Finds latest for order.
     *
     * @param orderNumber the order number
     * @return the find latest for order result
     */
    @EntityGraph(attributePaths = {"order"})
    @Query(
            """
            SELECT payment
            FROM Payment payment
            WHERE payment.order.orderNumber = :orderNumber
            ORDER BY payment.createdAt DESC, payment.id DESC
            """)
    List<Payment> findLatestForOrder(@Param("orderNumber") String orderNumber);
}
