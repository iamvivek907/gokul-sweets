package com.gokulsweets.restaurant.kot.dto;

public record AdminKotItemResponse(

        Long id,

        Long productId,

        String productName,

        Integer quantity,

        Integer displayOrder

) {
}