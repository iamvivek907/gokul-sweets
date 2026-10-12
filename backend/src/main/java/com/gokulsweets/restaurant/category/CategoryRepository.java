package com.gokulsweets.restaurant.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence operations for category records. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Performs the find by active true order by display order asc operation for category
     * repository.
     *
     * @return the find by active true order by display order asc result
     */
    List<Category> findByActiveTrueOrderByDisplayOrderAsc();

    /**
     * Finds by code.
     *
     * @param code the code
     * @return the find by code result
     */
    Optional<Category> findByCode(String code);

    /**
     * Finds by name ignore case.
     *
     * @param name the name
     * @return the find by name ignore case result
     */
    Optional<Category> findByNameIgnoreCase(String name);

    /**
     * Finds by code in.
     *
     * @param codes the codes
     * @return the find by code in result
     */
    List<Category> findByCodeIn(Collection<String> codes);

    /**
     * Finds by normalized names.
     *
     * @param names the names
     * @return the find by normalized names result
     */
    @Query("select e from Category e where lower(e.name) in :names")
    List<Category> findByNormalizedNames(@Param("names") Collection<String> names);

    /**
     * Existses by code.
     *
     * @param code the code
     * @return the exists by code result
     */
    boolean existsByCode(String code);
}
