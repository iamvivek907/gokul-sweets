export interface OrderHistoryEntry {
    orderNumber: string;
    customerOrderNumber?: number | null;
    createdAt: string;
}