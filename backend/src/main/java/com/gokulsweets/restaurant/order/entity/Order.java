package com.gokulsweets.restaurant.order.entity;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.customer.CustomerContact;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.pickup.PickupSlot;
import com.gokulsweets.restaurant.rebate.Rebate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(
                        name = "idx_orders_branch_id",
                        columnList = "branch_id"
                ),
                @Index(
                        name = "idx_orders_pickup_slot_id",
                        columnList = "pickup_slot_id"
                ),
                @Index(
                        name = "idx_orders_status",
                        columnList = "order_status"
                ),
                @Index(
                        name = "idx_orders_branch_status",
                        columnList = "branch_id,order_status"
                ),
                @Index(
                        name = "idx_orders_created_at",
                        columnList = "created_at"
                ),
                @Index(
                        name = "idx_orders_customer_phone",
                        columnList = "customer_phone"
                ),
                @Index(
                        name = "idx_orders_customer_phone_normalized",
                        columnList = "customer_phone_normalized"
                ),
                @Index(
                        name = "idx_orders_customer_contact_id",
                        columnList = "customer_contact_id"
                ),
                @Index(
                        name = "idx_orders_reservation_expiry",
                        columnList = "order_status,reservation_expires_at"
                )
        }
)
@Getter
@Setter
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "order_number",
            nullable = false,
            unique = true,
            length = 50
    )
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false
    )
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "pickup_slot_id",
            nullable = false
    )
    private PickupSlot pickupSlot;

    /*
     * Permanent order snapshot.
     *
     * Never replace this value from the customer-contact row.
     * It records the name entered for this exact order.
     */
    @Column(
            name = "customer_name",
            nullable = false,
            length = 150
    )
    private String customerName;

    /*
     * Permanent raw order snapshot.
     *
     * This is what the customer entered during checkout.
     */
    @Column(
            name = "customer_phone",
            nullable = false,
            length = 20
    )
    private String customerPhone;

    /*
     * Best-effort canonical phone used only for grouping.
     *
     * Null means the entered value could not safely be
     * normalized and therefore must not be treated as an
     * identity key.
     */
    @Column(
            name = "customer_phone_normalized",
            length = 20
    )
    private String customerPhoneNormalized;

    /*
     * Guest/unverified customer grouping.
     *
     * This is deliberately nullable because bad or unsupported
     * phone input must never corrupt a different customer.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "customer_contact_id"
    )
    private CustomerContact customerContact;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "pickup_type",
            nullable = false,
            length = 30
    )
    private PickupType pickupType;

    @Column(
            name = "priority_charge",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal priorityCharge =
            BigDecimal.ZERO;

    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal subtotal =
            BigDecimal.ZERO;

    @Column(
            name = "tax_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal taxAmount =
            BigDecimal.ZERO;

    @Column(
            name = "total_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal totalAmount =
            BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "order_status",
            nullable = false,
            length = 40
    )
    private OrderStatus orderStatus;

    /*
     * Checkout reservation expiry.
     *
     * This timestamp belongs to the backend.
     *
     * Frontend countdowns are only a visual
     * representation of this authoritative value.
     *
     * IMPORTANT:
     *
     * Updating cart items later must NOT extend
     * this timestamp.
     */
    @Column(
            name = "reservation_expires_at",
            nullable = false
    )
    private LocalDateTime reservationExpiresAt;

    @Column(
            name = "admin_override",
            nullable = false
    )
    private boolean adminOverride = false;

    @Column(
            name = "override_reason",
            length = 500
    )
    private String overrideReason;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OrderItem> items =
            new ArrayList<>();

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "rebate_id"
    )
    private Rebate rebate;

    @Column(
            name = "rebate_code",
            length = 50
    )
    private String rebateCode;

    @Column(
            name = "rebate_discount_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal rebateDiscountAmount =
            BigDecimal.ZERO;

    @PrePersist
    protected void onCreate() {

        LocalDateTime now =
                LocalDateTime.now();

        createdAt =
                now;

        updatedAt =
                now;
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt =
                LocalDateTime.now();
    }

    public void addItem(
            OrderItem item
    ) {

        items.add(
                item
        );

        item.setOrder(
                this
        );
    }

    public void removeItem(
            OrderItem item
    ) {

        items.remove(
                item
        );

        item.setOrder(
                null
        );
    }
}
