package com.gokulsweets.restaurant.category;

import com.gokulsweets.restaurant.category.dto.CategoryResponse;
import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for category operations. */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * Returns active categories.
     *
     * @return the get active categories result
     */
    @GetMapping
    public List<CategoryResponse> getActiveCategories() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CategoryController.class, "getActiveCategories()");
        try {
            return categoryService.getActiveCategories();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CategoryController.class, "getActiveCategories()");
        }
    }

    /**
     * Returns category.
     *
     * @param id the id
     * @return the get category result
     */
    @GetMapping("/{id}")
    public CategoryResponse getCategory(@PathVariable Long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CategoryController.class, "getCategory(Long)");
        try {
            return categoryService.getCategory(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CategoryController.class, "getCategory(Long)");
        }
    }
}
