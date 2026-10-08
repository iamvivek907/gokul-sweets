package com.gokulsweets.restaurant.branch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Persistence operations for branch records. */
public interface BranchRepository extends JpaRepository<Branch, Long> {

    /**
     * Locks fee branch.
     *
     * @param id the id
     * @return the lock fee branch result
     */
    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from Branch b where b.id=:id")
    Optional<Branch> lockFeeBranch(@org.springframework.data.repository.query.Param("id") Long id);

    /**
     * Finds by code.
     *
     * @param code the code
     * @return the find by code result
     */
    Optional<Branch> findByCode(String code);

    /**
     * Finds by id and active true.
     *
     * @param id the id
     * @return the find by id and active true result
     */
    Optional<Branch> findByIdAndActiveTrue(Long id);

    /**
     * Finds by active true order by name asc.
     *
     * @return the find by active true order by name asc result
     */
    List<Branch> findByActiveTrueOrderByNameAsc();
}
