package com.gokulsweets.restaurant.tax;

import com.gokulsweets.restaurant.tax.dto.TaxCategoryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaxCategoryService {

    private static final Logger log =
            LoggerFactory.getLogger(TaxCategoryService.class);

    private final TaxCategoryRepository taxCategoryRepository;

    public TaxCategoryService(
            TaxCategoryRepository taxCategoryRepository
    ) {
        this.taxCategoryRepository = taxCategoryRepository;
    }

    @Transactional(readOnly = true)
    public List<TaxCategoryResponse> getActiveTaxCategories() {

        log.debug("Fetching active tax categories");

        List<TaxCategoryResponse> taxCategories =
                taxCategoryRepository
                        .findByActiveTrueOrderByNameAsc()
                        .stream()
                        .map(TaxCategoryResponse::from)
                        .toList();

        log.debug(
                "Found {} active tax categories",
                taxCategories.size()
        );

        return taxCategories;
    }

    @Transactional(readOnly = true)
    public TaxCategoryResponse getTaxCategory(Long id) {

        log.debug(
                "Fetching tax category with id={}",
                id
        );

        TaxCategory taxCategory =
                taxCategoryRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Tax category not found with id={}",
                                    id
                            );

                            return new IllegalArgumentException(
                                    "Tax category not found: " + id
                            );
                        });

        return TaxCategoryResponse.from(taxCategory);
    }
}