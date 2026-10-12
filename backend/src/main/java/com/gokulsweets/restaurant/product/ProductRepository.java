package com.gokulsweets.restaurant.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence operations for product records. */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Finds by active true.
     *
     * @return the find by active true result
     */
    List<Product> findByActiveTrue();

    /**
     * Finds by category id and active true.
     *
     * @param categoryId the category id
     * @return the find by category id and active true result
     */
    List<Product> findByCategoryIdAndActiveTrue(Long categoryId);

    /**
     * Finds by code.
     *
     * @param code the code
     * @return the find by code result
     */
    Optional<Product> findByCode(String code);

    /**
     * Finds by name ignore case.
     *
     * @param name the name
     * @return the find by name ignore case result
     */
    Optional<Product> findByNameIgnoreCase(String name);

    /**
     * Finds by code in.
     *
     * @param codes the codes
     * @return the find by code in result
     */
    List<Product> findByCodeIn(Collection<String> codes);

    /**
     * Finds by normalized names.
     *
     * @param names the names
     * @return the find by normalized names result
     */
    @Query("select e from Product e where lower(e.name) in :names")
    List<Product> findByNormalizedNames(@Param("names") Collection<String> names);

    /**
     * Existses by code.
     *
     * @param code the code
     * @return the exists by code result
     */
    boolean existsByCode(String code);
}
