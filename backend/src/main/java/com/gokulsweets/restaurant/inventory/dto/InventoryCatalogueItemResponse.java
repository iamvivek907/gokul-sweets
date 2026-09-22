package com.gokulsweets.restaurant.inventory.dto;

public record InventoryCatalogueItemResponse(
        Long branchProductId,
        Long productId,
        Long categoryId,
        String categoryName,
        String productCode,
        String productName,
        String saleMode,
        boolean menuAvailable,
        InventoryPolicyResponse policy,
        InventoryAllocationResponse allocation,
        boolean needsAttention,
        String attentionCode,
        String attentionMessage
) {
}
