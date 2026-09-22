package com.gokulsweets.restaurant.staff;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "permissions")
@Getter
@Setter
public class Permission {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            unique = true,
            length = 80
    )
    private PermissionName name;

    @Column(length = 255)
    private String description;
}