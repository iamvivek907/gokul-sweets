package com.gokulsweets.restaurant.category.dto;

import com.gokulsweets.restaurant.category.Category;
import com.gokulsweets.restaurant.observability.MethodTiming;

/**
 * Immutable category response data contract.
 *
 * @param id the id
 * @param name the name
 * @param description the description
 * @param displayOrder the display order
 * @param active the active
 */
public record CategoryResponse(
        Long id, String name, String description, Integer displayOrder, boolean active) {

    /**
     * Froms the operation.
     *
     * @param category the category
     * @return the from result
     */
    public static CategoryResponse from(Category category) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CategoryResponse.class, "from(Category)");
        try {
            return new CategoryResponse(
                    category.getId(),
                    category.getName(),
                    category.getDescription(),
                    category.getDisplayOrder(),
                    category.isActive());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CategoryResponse.class, "from(Category)");
        }
    }
}
