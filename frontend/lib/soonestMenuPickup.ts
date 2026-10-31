import {menuPickupOptions,cartPickupOptions,branchPickupOptions} from "./menuPickupOptions";
import {validPickupDate} from "./pickupFreshness";
import type {CartAvailability} from "../services/availabilityApi";
import type {PickupSelection} from "../types/pickup";
/** Empty carts use branch discovery only. Existing carts still require a fresh
 * whole-cart check for each candidate date; never skip failed verification. */
export async function findSoonestMenuPickup(discovery: CartAvailability, ids: number[], today: string, days: number,
    check: (date: string) => Promise<CartAvailability>, signal: AbortSignal,
    now: () => Date = () => new Date(), cart = false): Promise<PickupSelection | null> {
    const dates = [...discovery.dates].filter(day => validPickupDate(day.date,today,days) && day.date <= discovery.maximumDate)
        .sort((a,b) => a.date.localeCompare(b.date));
    for (const day of dates) {
        signal.throwIfAborted();
        const candidates = branchPickupOptions({...discovery,dates:[day]},now())
            .filter(value=>value.pickupType === "NORMAL").sort((a,b)=>a.slot.startTime.localeCompare(b.slot.startTime));
        if (!candidates.length) continue;
        if (!cart) return candidates[0];
        const verified = await check(day.date);
        signal.throwIfAborted();
        const dayCheck = {...verified,dates:verified.dates.filter(value => value.date === day.date)};
        const first = (cart ? cartPickupOptions(dayCheck,now()) : menuPickupOptions(dayCheck,ids,now()))
            .filter(value => value.pickupType === "NORMAL")
            .sort((a,b) => a.slot.startTime.localeCompare(b.slot.startTime))[0];
        if (first) return first;
    }
    return null;
}
