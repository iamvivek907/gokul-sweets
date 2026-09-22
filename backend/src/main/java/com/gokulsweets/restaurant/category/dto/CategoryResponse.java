package com.gokulsweets.restaurant.category.dto;

import com.gokulsweets.restaurant.category.Category;

public record CategoryResponse(
        Long id,
        String name,
        String description,
        Integer displayOrder,
        boolean active
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getDisplayOrder(),
                category.isActive()
        );
    }
}