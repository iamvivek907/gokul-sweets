package com.gokulsweets.restaurant.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaxCategoryRepository
        extends JpaRepository<TaxCategory, Long> {

    List<TaxCategory> findByActiveTrueOrderByNameAsc();

    Optional<TaxCategory> findByCode(String code);

    List<TaxCategory> findByCodeIn(
            Collection<String> codes
    );
}
