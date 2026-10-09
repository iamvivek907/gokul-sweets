package com.gokulsweets.restaurant.inventory.dto;

/**
 * Immutable inventory catalogue item response data contract.
 *
 * @param branchProductId the branch product id
 * @param productId the product id
 * @param categoryId the category id
 * @param categoryName the category name
 * @param productCode the product code
 * @param productName the product name
 * @param saleMode the sale mode
 * @param menuAvailable the menu available
 * @param policy the policy
 * @param allocation the allocation
 * @param needsAttention the needs attention
 * @param attentionCode the attention code
 * @param attentionMessage the attention message
 */
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
        String attentionMessage) {}
