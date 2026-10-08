import type {CartAvailability, ItemAvailability} from "../services/availabilityApi";
import type {MenuProduct} from "../types/menu";
import type {PortionGroup} from "./mobileMenu";
import {menuPickupOptions} from "./menuPickupOptions";

/** Defaults never advance the date or incur a priority fee. */
export function earliestNormalMenuPickup(data: CartAvailability, ids: number[], today: string, now = new Date()) {
    return menuPickupOptions(data, ids, now).filter(option => option.date === today && option.pickupType === "NORMAL")
        .sort((a, b) => a.slot.startTime.localeCompare(b.slot.startTime))[0] ?? null;
}

/** Keep size groups together; each size still retains its own eligibility in the picker. */
export function partitionPickupProducts(products: MenuProduct[], items: ItemAvailability[], groups: PortionGroup[] = [], catalog: MenuProduct[] = products) {
    const eligible = new Set(items.filter(item => item.available).map(item => item.productId));
    const availableGroupIds = new Set(groups.filter(group => group.choices.some(choice =>
        catalog.some(product => product.id === choice.productId && product.available && eligible.has(product.id))))
        .flatMap(group => group.choices.map(choice => choice.productId)));
    const fits = (product: MenuProduct) => product.available && eligible.has(product.id) || availableGroupIds.has(product.id);
    return {available: products.filter(fits), other: products.filter(product => !fits(product))};
}

export function noDefaultPickupMessage(data: CartAvailability, today: string) {
    const slots = data.dates.find(day => day.date === today)?.slots ?? [];
    const inWindow = slots.filter(slot => slot.code !== "PICKUP_WINDOW" && slot.slot.active);
    if (!inWindow.length) return "Today's pickup booking is closed. Choose another pickup date.";
    if (inWindow.every(slot => slot.slot.remainingCapacity <= 0))
        return "Today's normal pickup times are fully booked. Choose another date or review priority times.";
    return "No menu items can currently be booked for today's remaining pickup times. Choose another date.";
}
