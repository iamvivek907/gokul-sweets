package com.gokulsweets.restaurant.staff;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StaffUserRepository
        extends JpaRepository<StaffUser, Long> {

    @EntityGraph(
            attributePaths = {
                    "role",
                    "role.permissions",
                    "branches"
            }
    )
    Optional<StaffUser> findByUsername(
            String username
    );

    boolean existsByUsername(
            String username
    );

    @EntityGraph(
            attributePaths = {
                    "role",
                    "role.permissions",
                    "branches"
            }
    )
    Optional<StaffUser> findDetailedById(
            Long id
    );

    @EntityGraph(
            attributePaths = {
                    "role",
                    "branches"
            }
    )
    @Query("""
        SELECT DISTINCT s
        FROM StaffUser s
        ORDER BY s.fullName
        """)
    List<StaffUser> findAllWithRoleAndBranches();

    long countByRoleNameAndActiveTrue(
            String roleName
    );
}