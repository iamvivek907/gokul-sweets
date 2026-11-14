import type {CartAvailability, ItemAvailability, SlotAvailability} from "../services/availabilityApi";
import type {PickupSelection} from "../types/pickup";
import {pickupIsFresh} from "./pickupFreshness";
/** A menu preview needs at least one orderable item, rather than every unchosen item. */
export function menuPickupOptions(data:CartAvailability,ids:number[],now=new Date()):PickupSelection[]{
 return data.dates.flatMap(day=>day.slots.flatMap(value=>{
  const unavailable=new Set(value.issues?.filter(i=>!i.available).map(i=>i.productId)??[]);
  if(!ids.some(id=>!unavailable.has(id))||value.code==="PICKUP_WINDOW")return [];
  const base={date:day.date,slot:value.slot};
  if(!pickupIsFresh({...base,pickupType:"NORMAL"},now))return [];
  return [...(value.slot.remainingCapacity>0?[{...base,pickupType:"NORMAL" as const}]:[]),...(value.slot.priorityEnabled&&value.slot.priorityRemainingCapacity>0?[{...base,pickupType:"PRIORITY" as const}]:[])];
 }));
}

/** Cart choices require every requested amount to fit, including booking horizons. */
export function cartPickupOptions(data:CartAvailability,now=new Date()):PickupSelection[]{
 return data.dates.flatMap(day=>day.slots.flatMap(value=>{
  const base={date:day.date,slot:value.slot};
  if(!pickupIsFresh({...base,pickupType:"NORMAL"},now))return [];
  return [...(value.normalAvailable?[{...base,pickupType:"NORMAL" as const}]:[]),...(value.priorityAvailable?[{...base,pickupType:"PRIORITY" as const}]:[])];
 }));
}

/** Empty-cart browsing chooses branch capacity independently of dish inventory. */
export function branchPickupOptions(data:CartAvailability,now=new Date()):PickupSelection[]{
 return data.dates.flatMap(day=>day.slots.flatMap(value=>{
  const base={date:day.date,slot:value.slot};
  if(value.code==='PICKUP_WINDOW'||!pickupIsFresh({...base,pickupType:'NORMAL'},now))return [];
  return [...(value.slot.remainingCapacity>0?[{...base,pickupType:'NORMAL' as const}]:[]),...(value.slot.priorityEnabled&&value.slot.priorityRemainingCapacity>0?[{...base,pickupType:'PRIORITY' as const}]:[])];
 }));
}

/** Keep a slot-wide restriction separate from an individual dish's stock decision. */
export function menuItemsForPickup(
 items: ItemAvailability[] | undefined, slot: SlotAvailability | undefined, pickupType: PickupSelection["pickupType"] | undefined
): ItemAvailability[] | undefined {
 const issues = new Map(slot?.issues?.map(issue => [issue.productId, issue]));
 return items?.map(item => {
  const issue = issues.get(item.productId) ?? item;
  if (!pickupType || issue.code === "SOLD_OUT") return issue;
  if (!slot) return {...item, available:false, code:"NO_SLOTS", reason:"Choose another pickup time."};
  if (slot.code === "PICKUP_WINDOW") return {...item, available:false, code:slot.code, reason:slot.reason};
  const full = pickupType === "PRIORITY"
   ? !slot.slot.priorityEnabled || slot.slot.priorityRemainingCapacity <= 0
   : slot.slot.remainingCapacity <= 0;
  return full ? {...item, available:false, code:"SLOT_FULL", reason:"This time is fully booked. Choose another pickup time."} : issue;
 });
}
