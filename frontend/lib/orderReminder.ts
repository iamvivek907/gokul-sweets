import type {CustomerOrderSummaryResponse} from "@/types/order";
import {businessDateOffset} from "@/lib/businessTime";
import {formatOrderDate,formatOrderTime} from "@/lib/orderTracking";

export function orderWindow(order:CustomerOrderSummaryResponse){
 const delivery=order.fulfillmentType==="DELIVERY";
 return {date:delivery?order.deliveryDate:order.pickupDate,start:delivery?order.deliveryStartTime:order.pickupStartTime,end:delivery?order.deliveryEndTime:order.pickupEndTime};
}
export function groupUpcomingOrders(orders:CustomerOrderSummaryResponse[]){
 const sorted=[...orders].sort((a,b)=>{
  const x=orderWindow(a),y=orderWindow(b);
  return (x.date||"9999").localeCompare(y.date||"9999")||(x.start||"99").localeCompare(y.start||"99")||a.orderNumber.localeCompare(b.orderNumber);
 });
 const groups=new Map<string,CustomerOrderSummaryResponse[]>();
 for(const order of sorted){const date=orderWindow(order).date??"";groups.set(date,[...(groups.get(date)??[]),order]);}
 return [...groups].map(([date,items])=>({date,orders:items}));
}
export function reminderDay(date:string|null|undefined,locale:string,now=new Date()){
 if(!date)return locale==="hi"?"समय के लिए ऑर्डर देखें":"Open order for time";
 if(date===businessDateOffset(0,now))return locale==="hi"?"आज":"Today";
 if(date===businessDateOffset(1,now))return locale==="hi"?"कल":"Tomorrow";
 return formatOrderDate(date);
}
export function reminderTime(order:CustomerOrderSummaryResponse,locale:string){
 const {date,start,end}=orderWindow(order);
 return `${reminderDay(date,locale)}${start?` · ${formatOrderTime(start)}${end?`–${formatOrderTime(end)}`:""}`:""}`;
}
