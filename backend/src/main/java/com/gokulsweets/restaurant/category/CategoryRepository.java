package com.gokulsweets.restaurant.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("select e from Category e where lower(e.name) in :names")
    List<Category> findByNormalizedNames(@Param("names") Collection<String> names);

    boolean
    existsByCode(String code);
}
