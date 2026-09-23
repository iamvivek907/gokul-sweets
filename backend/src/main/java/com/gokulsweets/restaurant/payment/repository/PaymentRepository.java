package com.gokulsweets.restaurant.payment.repository;

import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByCreatedAtDesc(
            Long orderId
    );


    Optional<Payment> findByProviderPaymentId(
            String providerPaymentId
    );


    Optional<Payment> findByProviderAndProviderPaymentId(
            PaymentProviderType provider,
            String providerPaymentId
    );


    Optional<Payment> findByProviderOrderId(
            String providerOrderId
    );


    Optional<Payment> findByProviderAndProviderOrderId(
            PaymentProviderType provider,
            String providerOrderId
    );


    Optional<Payment> findByProviderRefundId(
            String providerRefundId
    );


    Optional<Payment> findByRefundReferenceId(
            String refundReferenceId
    );


    boolean existsByOrderIdAndPaymentStatus(
            Long orderId,
            PaymentStatus paymentStatus
    );


    boolean existsByOrderId(
            Long orderId
    );


    List<Payment> findTop100ByPaymentStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            PaymentStatus paymentStatus,
            LocalDateTime expiresAt
    );


    List<Payment> findTop100ByPaymentStatusOrderByUpdatedAtAsc(
            PaymentStatus paymentStatus
    );


    @EntityGraph(attributePaths = {"order"})
    @Query("""
            SELECT payment
            FROM Payment payment
            WHERE payment.id = :paymentId
            """)
    Optional<Payment> findByIdWithOrder(
            @Param("paymentId") Long paymentId
    );


    @EntityGraph(attributePaths = {"order"})
    @Query("""
            SELECT payment
            FROM Payment payment
            WHERE payment.order.orderNumber = :orderNumber
              AND payment.paymentStatus = :status
            ORDER BY payment.createdAt DESC, payment.id DESC
            """)
    List<Payment> findForOrderAndStatus(
            @Param("orderNumber") String orderNumber,
            @Param("status") PaymentStatus status
    );


    /*
     * =========================================================
     * LATEST PAYMENT FOR ORDER
     * =========================================================
     *
     * Used when the browser returns from an external payment
     * provider and localStorage no longer contains paymentId.
     */
    @EntityGraph(attributePaths = {"order"})
    Optional<Payment> findFirstByOrderOrderNumberOrderByCreatedAtDesc(
            String orderNumber
    );


    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            UPDATE Payment payment
            SET payment.paymentStatus = :newStatus
            WHERE payment.id = :paymentId
              AND payment.paymentStatus = :expectedStatus
            """)
    int transitionStatus(
            @Param("paymentId") Long paymentId,
            @Param("expectedStatus") PaymentStatus expectedStatus,
            @Param("newStatus") PaymentStatus newStatus
    );


    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            UPDATE Payment payment
            SET payment.paymentStatus = :newStatus
            WHERE payment.id = :paymentId
              AND payment.paymentStatus IN :expectedStatuses
            """)
    int transitionStatusFromAny(
            @Param("paymentId") Long paymentId,
            @Param("expectedStatuses")
            Collection<PaymentStatus> expectedStatuses,
            @Param("newStatus") PaymentStatus newStatus
    );


    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(
            Long orderId
    );


    @Query("""
            SELECT payment
            FROM Payment payment
            WHERE payment.order.id IN :orderIds
              AND payment.id = (
                  SELECT MAX(other.id)
                  FROM Payment other
                  WHERE other.order.id = payment.order.id
              )
            """)
    List<Payment> findLatestPaymentsForOrders(
            @Param("orderIds")
            Collection<Long> orderIds
    );
}