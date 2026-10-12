package com.gokulsweets.restaurant.staff;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/** Persistence operations for staff user records. */
public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {

    /**
     * Finds by username.
     *
     * @param username the username
     * @return the find by username result
     */
    @EntityGraph(attributePaths = {"role", "role.permissions", "branches"})
    Optional<StaffUser> findByUsername(String username);

    /**
     * Existses by username.
     *
     * @param username the username
     * @return the exists by username result
     */
    boolean existsByUsername(String username);

    /**
     * Finds detailed by id.
     *
     * @param id the id
     * @return the find detailed by id result
     */
    @EntityGraph(attributePaths = {"role", "role.permissions", "branches"})
    Optional<StaffUser> findDetailedById(Long id);

    /**
     * Finds all with role and branches.
     *
     * @return the find all with role and branches result
     */
    @EntityGraph(attributePaths = {"role", "branches"})
    @Query(
            """
            SELECT DISTINCT s
            FROM StaffUser s
            ORDER BY s.fullName
            """)
    List<StaffUser> findAllWithRoleAndBranches();

    /**
     * Counts by role name and active true.
     *
     * @param roleName the role name
     * @return the count by role name and active true result
     */
    long countByRoleNameAndActiveTrue(String roleName);
}
