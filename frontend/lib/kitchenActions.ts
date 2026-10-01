export type KitchenActionRow = {orderStatus: string; bucket: string};
export function canStartKitchenOrder(row: KitchenActionRow): boolean {
    return row.orderStatus === "CONFIRMED" && ["ELIGIBLE", "OVERDUE"].includes(row.bucket);
}
export function kitchenOrderAction(row: KitchenActionRow, canStart: boolean, canReady: boolean): "start" | "ready" | null {
    if (canStart && canStartKitchenOrder(row)) return "start";
    if (canReady && row.orderStatus === "PREPARING") return "ready";
    return null;
}
