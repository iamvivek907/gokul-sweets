import type {CreateOrderRequest} from "@/types/order";
/** Price identity must depend on field values, never object insertion order. */
export function checkoutQuoteKey(request: CreateOrderRequest, orderNumber?: string): string {
    return JSON.stringify([request.branchId, request.pickupSlotId, request.pickupType,
        request.customerName, request.customerPhone,
        request.items.map(item => [item.productId, item.quantity, item.weightGrams]), orderNumber ?? null]);
}
