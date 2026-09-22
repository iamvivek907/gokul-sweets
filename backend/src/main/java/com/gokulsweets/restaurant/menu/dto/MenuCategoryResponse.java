package com.gokulsweets.restaurant.menu.dto;

import java.util.List;

public record MenuCategoryResponse(

        Long id,

        String name,

        String description,

        Integer displayOrder,

        List<MenuProductResponse> products

) {
}