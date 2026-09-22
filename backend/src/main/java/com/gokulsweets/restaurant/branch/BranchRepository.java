package com.gokulsweets.restaurant.branch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository
        extends JpaRepository<Branch, Long> {

    Optional<Branch> findByCode(String code);

    Optional<Branch> findByIdAndActiveTrue(Long id);

    List<Branch> findByActiveTrueOrderByNameAsc();
}