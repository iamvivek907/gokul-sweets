/** Completed paid, owner-bound orders only. A badge is recognition, not a loyalty balance. */
export const accountMilestones = [
    {orders: 1, title: "First visit", description: "Your first completed paid order"},
    {orders: 5, title: "Regular", description: "Five completed paid orders"},
    {orders: 20, title: "Gokul favourite", description: "Twenty completed paid orders"}
] as const;

export function currentMilestone(completedOrders: number) {
    return [...accountMilestones].reverse().find(item => completedOrders >= item.orders) ?? null;
}
