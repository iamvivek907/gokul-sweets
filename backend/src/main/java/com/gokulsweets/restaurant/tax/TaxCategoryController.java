package com.gokulsweets.restaurant.tax;

import com.gokulsweets.restaurant.tax.dto.TaxCategoryResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tax-categories")
public class TaxCategoryController {

    private final TaxCategoryService taxCategoryService;

    public TaxCategoryController(
            TaxCategoryService taxCategoryService
    ) {
        this.taxCategoryService = taxCategoryService;
    }

    @GetMapping
    public List<TaxCategoryResponse> getActiveTaxCategories() {

        return taxCategoryService
                .getActiveTaxCategories();
    }

    @GetMapping("/{id}")
    public TaxCategoryResponse getTaxCategory(
            @PathVariable Long id
    ) {

        return taxCategoryService
                .getTaxCategory(id);
    }
}