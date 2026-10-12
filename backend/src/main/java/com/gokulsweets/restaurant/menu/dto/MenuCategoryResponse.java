package com.gokulsweets.restaurant.menu.dto;

import java.util.List;

/**
 * Immutable menu category response data contract.
 *
 * @param id the id
 * @param name the name
 * @param description the description
 * @param displayOrder the display order
 * @param products the products
 */
public record MenuCategoryResponse(
        Long id,
        String name,
        String description,
        Integer displayOrder,
        List<MenuProductResponse> products) {}
