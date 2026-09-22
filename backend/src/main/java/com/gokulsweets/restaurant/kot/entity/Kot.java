package com.gokulsweets.restaurant.kot.entity;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.order.entity.Order;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "kot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_kot_kot_number",
                        columnNames = "kot_number"
                ),
                @UniqueConstraint(
                        name = "uk_kot_order_id",
                        columnNames = "order_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_kot_branch_id",
                        columnList = "branch_id"
                ),
                @Index(
                        name = "idx_kot_created_at",
                        columnList = "created_at"
                ),
                @Index(
                        name = "idx_kot_started_by_staff_id",
                        columnList = "started_by_staff_id"
                ),
                @Index(
                        name = "idx_kot_first_printed_by_staff_id",
                        columnList = "first_printed_by_staff_id"
                ),
                @Index(
                        name = "idx_kot_last_printed_by_staff_id",
                        columnList = "last_printed_by_staff_id"
                )
        }
)
@Getter
@Setter
public class Kot {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;


    @Column(
            name = "kot_number",
            nullable = false,
            unique = true,
            length = 50
    )
    private String kotNumber;


    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "order_id",
            nullable = false,
            unique = true
    )
    private Order order;


    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "branch_id",
            nullable = false
    )
    private Branch branch;


    /*
     * =========================================================
     * PREPARATION STAFF SNAPSHOT
     * =========================================================
     */

    @Column(
            name = "started_by_staff_id",
            nullable = false
    )
    private Long startedByStaffId;


    @Column(
            name = "started_by_staff_name",
            nullable = false,
            length = 150
    )
    private String startedByStaffName;


    /*
     * =========================================================
     * KOT ITEMS
     * =========================================================
     */

    @OneToMany(
            mappedBy = "kot",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("displayOrder ASC, id ASC")
    private List<KotItem> items =
            new ArrayList<>();


    /*
     * =========================================================
     * CREATED
     * =========================================================
     */

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;


    /*
     * =========================================================
     * PRINT AUDIT
     * =========================================================
     */

    @Column(
            name = "first_printed_at"
    )
    private LocalDateTime firstPrintedAt;


    @Column(
            name = "first_printed_by_staff_id"
    )
    private Long firstPrintedByStaffId;


    @Column(
            name = "first_printed_by_staff_name",
            length = 150
    )
    private String firstPrintedByStaffName;


    @Column(
            name = "last_printed_at"
    )
    private LocalDateTime lastPrintedAt;


    @Column(
            name = "last_printed_by_staff_id"
    )
    private Long lastPrintedByStaffId;


    @Column(
            name = "last_printed_by_staff_name",
            length = 150
    )
    private String lastPrintedByStaffName;


    @Column(
            name = "print_count",
            nullable = false
    )
    private Integer printCount = 0;


    @PrePersist
    protected void onCreate() {

        if (
                createdAt
                        ==
                        null
        ) {

            createdAt =
                    LocalDateTime.now();
        }


        if (
                printCount
                        ==
                        null
        ) {

            printCount =
                    0;
        }
    }


    public void addItem(
            KotItem item
    ) {

        items.add(
                item
        );


        item.setKot(
                this
        );
    }


    public void removeItem(
            KotItem item
    ) {

        items.remove(
                item
        );


        item.setKot(
                null
        );
    }


    public boolean hasBeenPrinted() {

        return printCount != null
                &&
                printCount > 0;
    }
}