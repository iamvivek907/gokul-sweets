/** Display only. Keep orderNumber for routes, provider recovery and mutations. */
export function orderDisplayNumber(order: {orderNumber: string; customerOrderNumber?: number | null}): string {
    return order.customerOrderNumber != null ? `#${order.customerOrderNumber}` : order.orderNumber;
}
