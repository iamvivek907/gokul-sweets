package com.gokulsweets.restaurant.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository
        extends JpaRepository<Category, Long> {

    List<Category>
    findByActiveTrueOrderByDisplayOrderAsc();

    Optional<Category>
    findByCode(String code);

    Optional<Category>
    findByNameIgnoreCase(String name);

    List<Category>
    findByCodeIn(Collection<String> codes);

    boolean
    existsByCode(String code);
}
