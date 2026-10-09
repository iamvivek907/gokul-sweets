package com.gokulsweets.restaurant.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence operations for role records. */
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Finds by name.
     *
     * @param name the name
     * @return the find by name result
     */
    Optional<Role> findByName(String name);
}
