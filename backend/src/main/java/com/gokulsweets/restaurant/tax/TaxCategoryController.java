package com.gokulsweets.restaurant.tax;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.tax.dto.TaxCategoryResponse;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for tax category operations. */
@RestController
@RequestMapping("/api/tax-categories")
public class TaxCategoryController {

    private final TaxCategoryService taxCategoryService;

    public TaxCategoryController(TaxCategoryService taxCategoryService) {
        this.taxCategoryService = taxCategoryService;
    }

    /**
     * Returns active tax categories.
     *
     * @return the get active tax categories result
     */
    @GetMapping
    public List<TaxCategoryResponse> getActiveTaxCategories() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(TaxCategoryController.class, "getActiveTaxCategories()");
        try {
            return taxCategoryService.getActiveTaxCategories();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    TaxCategoryController.class,
                    "getActiveTaxCategories()");
        }
    }

    /**
     * Returns tax category.
     *
     * @param id the id
     * @return the get tax category result
     */
    @GetMapping("/{id}")
    public TaxCategoryResponse getTaxCategory(@PathVariable Long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(TaxCategoryController.class, "getTaxCategory(Long)");
        try {
            return taxCategoryService.getTaxCategory(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, TaxCategoryController.class, "getTaxCategory(Long)");
        }
    }
}
