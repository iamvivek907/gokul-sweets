import type {CartAvailability} from "../services/availabilityApi";
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
