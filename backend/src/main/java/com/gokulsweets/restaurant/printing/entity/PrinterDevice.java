package com.gokulsweets.restaurant.printing.entity;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.printing.enums.PrinterProtocol;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "printer_devices",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_printer_device_branch_code",
                        columnNames = {
                                "branch_id",
                                "code"
                        }
                )
        }
)
@Getter
@Setter
public class PrinterDevice {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;


    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "branch_id",
            nullable = false
    )
    private Branch branch;


    @Column(
            nullable = false,
            length = 50
    )
    private String code;


    @Column(
            nullable = false,
            length = 120
    )
    private String name;


    @Enumerated(
            EnumType.STRING
    )
    @Column(
            nullable = false,
            length = 50
    )
    private PrinterStation station;


    @Enumerated(
            EnumType.STRING
    )
    @Column(
            nullable = false,
            length = 30
    )
    private PrinterProtocol protocol;


    @Column(
            nullable = false,
            length = 255
    )
    private String host;


    @Column(
            nullable = false
    )
    private Integer port;


    @Column(
            name = "paper_width_mm",
            nullable = false
    )
    private Integer paperWidthMm =
            80;


    @Column(
            name = "auto_cut",
            nullable = false
    )
    private boolean autoCut =
            true;


    @Column(
            nullable = false
    )
    private boolean active =
            true;


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
                LocalDateTime.now();


        if (
                createdAt
                        ==
                        null
        ) {

            createdAt =
                    now;
        }


        updatedAt =
                now;


        if (
                paperWidthMm
                        ==
                        null
        ) {

            paperWidthMm =
                    80;
        }


        if (
                port
                        ==
                        null
        ) {

            port =
                    9100;
        }
    }


    @PreUpdate
    protected void onUpdate() {

        updatedAt =
                LocalDateTime.now();
    }
}