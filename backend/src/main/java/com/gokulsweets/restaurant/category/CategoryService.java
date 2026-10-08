package com.gokulsweets.restaurant.category;

import com.gokulsweets.restaurant.category.dto.CategoryResponse;
import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.stereotype.Service;

import java.util.List;

/** Coordinates category operations. */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * Returns active categories.
     *
     * @return the get active categories result
     */
    public List<CategoryResponse> getActiveCategories() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CategoryService.class, "getActiveCategories()");
        try {
            return categoryRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                    .map(CategoryResponse::from)
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CategoryService.class, "getActiveCategories()");
        }
    }

    /**
     * Returns category.
     *
     * @param id the id
     * @return the get category result
     */
    public CategoryResponse getCategory(Long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CategoryService.class, "getCategory(Long)");
        try {
            Category category =
                    categoryRepository
                            .findById(id)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Category not found: " + id));
            return CategoryResponse.from(category);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CategoryService.class, "getCategory(Long)");
        }
    }
}
