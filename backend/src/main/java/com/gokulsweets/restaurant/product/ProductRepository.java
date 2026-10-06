package com.gokulsweets.restaurant.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository
        extends JpaRepository<Product, Long> {

    List<Product>
    findByActiveTrue();

    List<Product>
    findByCategoryIdAndActiveTrue(
            Long categoryId
    );

    Optional<Product>
    findByCode(String code);

    Optional<Product>
    findByNameIgnoreCase(String name);

    List<Product>
    findByCodeIn(Collection<String> codes);

    @Query("select e from Product e where lower(e.name) in :names")
    List<Product> findByNormalizedNames(@Param("names") Collection<String> names);

    boolean
    existsByCode(String code);
}
