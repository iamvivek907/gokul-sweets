/** Paid, owner-bound orders only. A badge is recognition, not a loyalty balance. */
export const accountMilestones = [
    {orders: 1, title: "First visit", description: "Your first paid order"},
    {orders: 5, title: "Regular", description: "Five paid orders"},
    {orders: 20, title: "Gokul favourite", description: "Twenty paid orders"}
] as const;

export function currentMilestone(paidOrders: number) {
    return [...accountMilestones].reverse().find(item => paidOrders >= item.orders) ?? null;
}
