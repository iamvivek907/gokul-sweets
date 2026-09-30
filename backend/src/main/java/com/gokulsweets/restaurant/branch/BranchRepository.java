package com.gokulsweets.restaurant.branch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository
        extends JpaRepository<Branch, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from Branch b where b.id=:id")
    Optional<Branch> lockFeeBranch(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Branch> findByCode(String code);

    Optional<Branch> findByIdAndActiveTrue(Long id);

    List<Branch> findByActiveTrueOrderByNameAsc();
}