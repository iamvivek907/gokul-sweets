import type {CartAvailability} from "@/services/availabilityApi";
import type {PickupSelection} from "@/types/pickup";
import type {CartItem} from "@/types/cart";
/** Availability already includes item stock, readiness, date windows and slot capacity. */
export function reorderPickupOptions(value:CartAvailability,now=Date.now()):PickupSelection[]{
 return value.dates.flatMap(day=>day.slots.flatMap<PickupSelection>(option=>{
  const time=Date.parse(`${day.date}T${option.slot.startTime}+05:30`);
  if(!option.slot.active||!Number.isFinite(time)||time<=now)return [];
  return option.normalAvailable?[{date:day.date,slot:option.slot,pickupType:"NORMAL" as const}]:
   option.priorityAvailable&&option.slot.priorityEnabled?[{date:day.date,slot:option.slot,pickupType:"PRIORITY" as const}]:[];
 })).sort((a,b)=>`${a.date}T${a.slot.startTime}`.localeCompare(`${b.date}T${b.slot.startTime}`));
}
export function validReorderItems(items:CartItem[]):boolean{
 return items.length>0&&items.every(item=>item.product.saleMode==="WEIGHT"?
  Number.isSafeInteger(item.weightGrams)&&(item.weightGrams??0)<=2147483647&&(item.weightGrams??0)>=Math.max(250,item.product.minimumWeightGrams??250)&&
  ((item.weightGrams??0)-(item.product.minimumWeightGrams??250))%(item.product.weightStepGrams??50)===0:
  Number.isSafeInteger(item.quantity)&&item.quantity>0&&item.quantity<=2147483647);
}
