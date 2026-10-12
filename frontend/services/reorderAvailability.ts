import {availabilityItems, checkCartAvailability, type CartAvailability} from "./availabilityApi";
import {checkInventory} from "./inventoryApi";
import {getPickupSlots} from "./pickupApi";
import type {CartItem} from "@/types/cart";

/** Legacy ordering checks one chosen date; never call the gated smart endpoint when OFF. */
export async function checkReorderAvailability(branchId:number, date:string, days:number, items:CartItem[], smart:boolean, signal:AbortSignal):Promise<CartAvailability> {
    if (smart) return checkCartAvailability(branchId,date,days,availabilityItems(items),signal);
    const [stock,slots] = await Promise.all([
        checkInventory(branchId,{serviceDate:date,items:availabilityItems(items)},signal),
        getPickupSlots(branchId,date,signal)
    ]);
    const orderable = stock.orderable && items.every(item=>item.product.available &&
        (stock.enforcementEnabled===false || stock.items.some(line=>line.productId===item.product.id && line.orderable)));
    const choices = slots.map(slot=>({slot,
        normalAvailable:orderable && slot.active && slot.remainingCapacity>0,
        priorityAvailable:orderable && slot.active && slot.priorityEnabled && slot.priorityRemainingCapacity>0,
        reason:null}));
    return {fulfilmentType:"PICKUP",today:date,maximumDate:date,dates:[{date,
        available:choices.some(slot=>slot.normalAvailable||slot.priorityAvailable),slots:choices,
        reason:orderable?"No pickup time is available. Try another date.":"These quantities are unavailable. Try another date or reduce quantities."}]};
}
