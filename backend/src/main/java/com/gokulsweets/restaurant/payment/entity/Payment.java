package com.gokulsweets.restaurant.payment.entity;

import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(
                        name = "idx_payments_order_id",
                        columnList = "order_id"
                ),
                @Index(
                        name = "idx_payments_status",
                        columnList = "payment_status"
                ),
                @Index(
                        name = "idx_payments_provider_payment_id",
                        columnList = "provider_payment_id"
                ),
                @Index(
                        name = "idx_payments_provider_order_id",
                        columnList = "provider_order_id"
                ),
                @Index(
                        name = "idx_payments_provider_refund_id",
                        columnList = "provider_refund_id"
                ),
                @Index(
                        name = "idx_payments_created_at",
                        columnList = "created_at"
                )
        }
)
@Getter
@Setter
public class Payment {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Asia/Kolkata"
            );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false
    )
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 50
    )
    private PaymentProviderType provider;

    @Column(
            name = "provider_payment_id",
            length = 150
    )
    private String providerPaymentId;

    @Column(
            name = "provider_order_id",
            length = 150
    )
    private String providerOrderId;

    @Column(
            name = "refund_reference_id",
            length = 50
    )
    private String refundReferenceId;

    @Column(
            name = "provider_refund_id",
            length = 150
    )
    private String providerRefundId;

    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal amount;

    @Column(
            nullable = false,
            length = 10
    )
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(
            name = "payment_status",
            nullable = false,
            length = 40
    )
    private PaymentStatus paymentStatus;

    @Column(
            name = "failure_reason",
            length = 500
    )
    private String failureReason;

    @Column(
            name = "refund_failure_reason",
            length = 500
    )
    private String refundFailureReason;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "refund_requested_at")
    private LocalDateTime refundRequestedAt;

    @Column(name = "refund_last_checked_at")
    private LocalDateTime refundLastCheckedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {

        LocalDateTime now =
                LocalDateTime.now(
                        BUSINESS_ZONE
                );

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt =
                LocalDateTime.now(
                        BUSINESS_ZONE
                );
    }
}
