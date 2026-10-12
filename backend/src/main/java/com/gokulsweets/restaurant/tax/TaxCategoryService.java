package com.gokulsweets.restaurant.tax;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.tax.dto.TaxCategoryResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Coordinates tax category operations. */
@Service
public class TaxCategoryService {

    private static final Logger log = LoggerFactory.getLogger(TaxCategoryService.class);

    private final TaxCategoryRepository taxCategoryRepository;

    /**
     * Creates a tax category service instance.
     *
     * @param taxCategoryRepository the tax category repository
     */
    public TaxCategoryService(TaxCategoryRepository taxCategoryRepository) {
        this.taxCategoryRepository = taxCategoryRepository;
    }

    /**
     * Returns active tax categories.
     *
     * @return the get active tax categories result
     */
    @Transactional(readOnly = true)
    public List<TaxCategoryResponse> getActiveTaxCategories() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(TaxCategoryService.class, "getActiveTaxCategories()");
        try {
            log.debug("Fetching active tax categories");
            List<TaxCategoryResponse> taxCategories =
                    taxCategoryRepository.findByActiveTrueOrderByNameAsc().stream()
                            .map(TaxCategoryResponse::from)
                            .toList();
            log.debug("Found {} active tax categories", taxCategories.size());
            return taxCategories;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    TaxCategoryService.class,
                    "getActiveTaxCategories()");
        }
    }

    /**
     * Returns tax category.
     *
     * @param id the id
     * @return the get tax category result
     */
    @Transactional(readOnly = true)
    public TaxCategoryResponse getTaxCategory(Long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(TaxCategoryService.class, "getTaxCategory(Long)");
        try {
            log.debug("Fetching tax category with id={}", id);
            TaxCategory taxCategory =
                    taxCategoryRepository
                            .findById(id)
                            .orElseThrow(
                                    () -> {
                                        log.warn("Tax category not found with id={}", id);
                                        return new IllegalArgumentException(
                                                "Tax category not found: " + id);
                                    });
            return TaxCategoryResponse.from(taxCategory);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, TaxCategoryService.class, "getTaxCategory(Long)");
        }
    }
}
