import {parsePickupSlot} from "./checkoutStorage";
import {pickupIsFresh} from "./pickupFreshness";
/** Never let an open item picker adopt an unseen automatic or cross-tab pickup. */
export function pickerPickupMatches(opened: string, current: string, now = new Date()): boolean {
    const selection = parsePickupSlot(opened);
    return opened === current && !!selection && pickupIsFresh(selection,now);
}
