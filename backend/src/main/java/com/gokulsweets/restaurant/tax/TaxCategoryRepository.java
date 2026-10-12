package com.gokulsweets.restaurant.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence operations for tax category records. */
public interface TaxCategoryRepository extends JpaRepository<TaxCategory, Long> {

    /**
     * Finds by active true order by name asc.
     *
     * @return the find by active true order by name asc result
     */
    List<TaxCategory> findByActiveTrueOrderByNameAsc();

    /**
     * Finds by code.
     *
     * @param code the code
     * @return the find by code result
     */
    Optional<TaxCategory> findByCode(String code);

    /**
     * Finds by code in.
     *
     * @param codes the codes
     * @return the find by code in result
     */
    List<TaxCategory> findByCodeIn(Collection<String> codes);
}
