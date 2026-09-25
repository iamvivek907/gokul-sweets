import {getMenu} from "@/services/menuApi";
import {checkInventory} from "@/services/inventoryApi";
import {getPickupSlots} from "@/services/pickupApi";
import type {CartItem} from "@/types/cart";
import type {MenuProduct} from "@/types/menu";

export interface PreviewLine {
    item: CartItem;
    proposed: MenuProduct | null;
    orderable: boolean;
    availableQuantity: number | null;
    reason: string | null;
}

export interface CartSwitchPreview {
    branchId: number;
    date: string;
    lines: PreviewLine[];
    oldSubtotal: number;
    newSubtotal: number | null;
    availableSlots: number;
    conflicts: boolean;
}

function lineTotal(item: CartItem, price: number): number {
    return item.product.saleMode === "WEIGHT"
        ? price * (item.weightGrams ?? 0) / 1000 : price * item.quantity;
}

/** Read-only preview: no cart, branch or pickup preference is written here. */
export async function previewCartSwitch(branchId: number, date: string, items: CartItem[], signal: AbortSignal): Promise<CartSwitchPreview> {
    const [categories, slots] = await Promise.all([getMenu(branchId, signal), getPickupSlots(branchId, date, signal)]);
    const products = new Map(categories.flatMap(category => category.products).map(product => [product.id, product]));
    const offered = items.filter(item => products.has(item.product.id));
    const inventory = offered.length ? await checkInventory(branchId, {
        serviceDate: date,
        items: offered.map(item => ({productId: item.product.id,
            quantity: item.product.saleMode === "WEIGHT" ? null : item.quantity,
            weightGrams: item.product.saleMode === "WEIGHT" ? item.weightGrams : null}))
    }, signal) : null;
    const stock = new Map(inventory?.items.map(item => [item.productId, item]) ?? []);
    const lines = items.map(item => {
        const proposed = products.get(item.product.id) ?? null;
        const availability = stock.get(item.product.id);
        const weightValid = proposed?.saleMode !== "WEIGHT" ||
            ((item.weightGrams ?? 0) >= (proposed.minimumWeightGrams ?? 250) &&
                ((item.weightGrams ?? 0) - (proposed.minimumWeightGrams ?? 250)) % (proposed.weightStepGrams ?? 50) === 0);
        const orderable = !!proposed?.available && availability?.orderable === true &&
            proposed.saleMode === item.product.saleMode && weightValid;
        return {item, proposed, orderable,
            availableQuantity: availability?.availableQuantity ?? null,
            reason: !proposed ? "Not offered at this branch" : !proposed.available ? "Unavailable at this branch"
                : proposed.saleMode !== item.product.saleMode ? "Sale unit changed; choose this item again"
                : !weightValid ? "Weight does not fit this branch's pack sizes"
                : availability?.orderable === false ? availability.unavailableReason ?? "Not enough stock for this date"
                : availability ? null : "Stock could not be confirmed"};
    });
    const availableSlots = slots.filter(slot => slot.active &&
        (slot.remainingCapacity > 0 || slot.priorityEnabled && slot.priorityRemainingCapacity > 0)).length;
    const conflicts = lines.some(line => !line.orderable) || availableSlots === 0;
    return {branchId, date, lines, oldSubtotal: items.reduce((sum, item) => sum + lineTotal(item, item.product.price), 0),
        newSubtotal: conflicts ? null : lines.reduce((sum, line) => sum + lineTotal(line.item, line.proposed!.price), 0),
        availableSlots, conflicts};
}
