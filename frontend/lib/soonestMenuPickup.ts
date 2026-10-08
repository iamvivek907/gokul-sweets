import {menuPickupOptions} from "./menuPickupOptions";
import {validPickupDate} from "./pickupFreshness";
import type {CartAvailability} from "../services/availabilityApi";
import type {PickupSelection} from "../types/pickup";
/** Discovery is capacity-only. Validate each candidate date against the full menu,
 * sequentially, stopping at the first verified normal slot. Never skip a failed read. */
export async function findSoonestMenuPickup(discovery: CartAvailability, ids: number[], today: string, days: number,
    check: (date: string) => Promise<CartAvailability>, signal: AbortSignal,
    now: () => Date = () => new Date()): Promise<PickupSelection | null> {
    const dates = [...discovery.dates].filter(day => validPickupDate(day.date,today,days) && day.date <= discovery.maximumDate)
        .sort((a,b) => a.date.localeCompare(b.date));
    for (const day of dates) {
        signal.throwIfAborted();
        if (!menuPickupOptions({...discovery,dates:[day]},ids,now()).some(value => value.pickupType === "NORMAL")) continue;
        const verified = await check(day.date);
        signal.throwIfAborted();
        const first = menuPickupOptions({...verified,dates:verified.dates.filter(value => value.date === day.date)},ids,now())
            .filter(value => value.pickupType === "NORMAL")
            .sort((a,b) => a.slot.startTime.localeCompare(b.slot.startTime))[0];
        if (first) return first;
    }
    return null;
}
