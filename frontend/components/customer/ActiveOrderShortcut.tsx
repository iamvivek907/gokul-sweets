"use client";
import {useEffect,useRef,useState,useSyncExternalStore} from "react";
import Link from "next/link";
import CustomerIcon from "./CustomerIcon";
import {getActiveOrder,getServerActiveOrder,subscribeActiveOrder} from "@/lib/activeOrderStore";
import {getOrderStatusPresentation,formatOrderTime} from "@/lib/orderTracking";
import {checkOperationalBranch} from "@/lib/branchOperationalCache";
import {T,useLanguage} from "@/lib/language";
import type {Branch} from "@/types/branch";
import type {CustomerOrderSummaryResponse} from "@/types/order";
import {groupUpcomingOrders,orderWindow,reminderDay,reminderTime} from "@/lib/orderReminder";
import {claimOrderIntroduction,dismissOrderReminder,reminderDismissed,reminderServerDismissed,subscribeOrderReminder} from "@/lib/orderReminderSession";
import MenuDiscoverySheet from "@/components/menu/MenuDiscoverySheet";
function OrderActions({order}:{order:CustomerOrderSummaryResponse}){
 const [branch,setBranch]=useState<Branch|null>(null);
 useEffect(()=>{let current=true;void checkOperationalBranch(order.branchId).then(value=>{if(current)setBranch(value);}).catch(()=>{});return()=>{current=false;};},[order.branchId]);
 const href=`/orders/${encodeURIComponent(order.orderNumber)}`;
 const code=order.fulfillmentType==='PICKUP'&&['CONFIRMED','PREPARING','READY_FOR_PICKUP'].includes(order.orderStatus);
 return <div className="active-order-actions"><Link className="active-order-primary" href={href}><T text={order.orderStatus==='PENDING_PAYMENT'?'Continue payment':'View order'}/></Link>{code&&<Link href={`${href}#pickup-code`}><T text="View pickup code"/></Link>}{branch?.id===order.branchId&&<>{branch.phone&&<a href={`tel:${branch.phone.replace(/[^+\d]/g,'')}`}><T text="Call"/></a>}{branch.address&&<a target="_blank" rel="noopener noreferrer" href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent([branch.name,branch.address,branch.city,branch.pincode].filter(Boolean).join(', '))}`}><T text="Directions"/></a>}</>}</div>;
}
export default function ActiveOrderShortcut(){
 const dismissed=useSyncExternalStore(subscribeOrderReminder,reminderDismissed,reminderServerDismissed);
 return dismissed?null:<OrderReminder/>;
}
function OrderReminder(){
 const {orders,more,stale}=useSyncExternalStore(subscribeActiveOrder,getActiveOrder,getServerActiveOrder);
 const [open,setOpen]=useState(false),[expanded,setExpanded]=useState<string|null>(null),[introduced,setIntroduced]=useState(false);
 const [trigger,setTrigger]=useState<HTMLElement|null>(null);
 const gesture=useRef<{x:number;y:number}|null>(null),swiped=useRef(false);
 const hasOrder=orders.length>0;
 const locale=useLanguage(),groups=groupUpcomingOrders(orders),order=groups[0]?.orders[0];
 useEffect(()=>{
  let current=true;
  if(!hasOrder){queueMicrotask(()=>{if(current)setOpen(false);});return()=>{current=false;};}
  // Wait for launch/other modal work before spending this visit's introduction.
  let timer:ReturnType<typeof setTimeout>|undefined;
  const observer=new MutationObserver(start);
  function start(){
   if(document.querySelector('dialog[open],.ordering-tour-overlay')||document.visibilityState!=="visible")return;
   if(claimOrderIntroduction()){setIntroduced(true);timer=setTimeout(()=>setIntroduced(false),6000);}
   observer.disconnect();document.removeEventListener("visibilitychange",start);
  }
  observer.observe(document.body,{subtree:true,attributes:true,attributeFilter:["open"],childList:true});
  document.addEventListener("visibilitychange",start);queueMicrotask(()=>{if(current)start();});
  return()=>{current=false;observer.disconnect();document.removeEventListener("visibilitychange",start);if(timer)clearTimeout(timer);setIntroduced(false);};
 },[hasOrder]);
 if(!order)return null;
 const allPickup=orders.every(value=>value.fulfillmentType==="PICKUP");
 const branches=new Set(orders.map(value=>value.branchId)).size;
 const count=`${orders.length}${more?"+":""}`;
 const title=orders.length>1
  ?locale==="hi"?`${count} आगामी ऑर्डर`:`${count} ${allPickup?"pickups":"orders"}`
  :order.orderStatus==="PENDING_PAYMENT"?locale==="hi"?"भुगतान बाकी है":"Payment pending"
  :locale==="hi"?allPickup?"आपका पिकअप":"आपकी डिलीवरी":allPickup?"Your pickup":"Your delivery";
 const {date,start}=orderWindow(order);
 const compact=orders.length>1?title:order.orderStatus==="PENDING_PAYMENT"?title:
  `${date?reminderDay(date,locale):""}${start?` · ${formatOrderTime(start)}`:""}`||title;
 function dismiss(){setOpen(false);dismissOrderReminder();}
 return <>
  <aside className="active-order-edge" data-expanded={introduced||undefined} data-stale={stale||undefined} aria-label="Active order">
   <button type="button" className="active-order-edge-trigger" aria-haspopup="dialog" aria-expanded={open}
    aria-label={`${title} · ${reminderTime(order,locale)} · ${orders.length>1?`${branches} ${locale==='hi'?'शाखाएँ':'branches'}`:order.branchName}`}
    onPointerDown={event=>{swiped.current=false;gesture.current={x:event.clientX,y:event.clientY};try{event.currentTarget.setPointerCapture(event.pointerId);}catch{/* Synthetic or cancelled pointer. */}}}
    onPointerCancel={()=>{gesture.current=null;}}
    onPointerUp={event=>{const point=gesture.current;gesture.current=null;if(point&&event.clientX-point.x>40&&Math.abs(event.clientY-point.y)<30){swiped.current=true;dismiss();}}}
    onClick={event=>{if(swiped.current){swiped.current=false;return;}setIntroduced(false);setTrigger(event.currentTarget);setExpanded(order.orderNumber);setOpen(true);}}>
    <CustomerIcon kind="receipt"/>
    <span className="active-order-edge-compact">{stale?<T text="Open for latest status"/>:compact}</span>
    <span className="active-order-edge-copy"><strong>{title}</strong><small>{stale?<T text="Open for latest status"/>:reminderTime(order,locale)}</small><small>{orders.length>1?(locale==='hi'?`${branches}${more?'+':''} शाखाओं में`:`Across ${branches}${more?'+':''} ${branches===1&&!more?'branch':'branches'}`):order.branchName}</small></span>
    <span className="active-order-edge-chevron" aria-hidden="true">›</span>
   </button>
  </aside>
  {open&&<MenuDiscoverySheet title="Your upcoming orders" tactile restoreFocus={trigger} onClose={()=>setOpen(false)} footer={<button type="button" className="active-order-dismiss" onClick={dismiss}>{locale==='hi'?'इस बार रिमाइंडर छिपाएँ':'Hide reminder for this visit'}</button>}>
   <div className="active-order-list">
    {stale&&<p role="status"><T text="Status may have changed. Open your order for the latest update."/></p>}
    {groups.map(group=><section key={group.date} aria-label={reminderDay(group.date,locale)}><h3>{reminderDay(group.date,locale)}</h3>{group.orders.map(value=><article key={value.orderNumber}>
     <button type="button" aria-expanded={expanded===value.orderNumber} onClick={()=>setExpanded(expanded===value.orderNumber?null:value.orderNumber)}>
      <span><strong>{value.branchName}</strong><small><T text={value.fulfillmentType==='DELIVERY'?'Delivery':'Pickup'}/> · #{value.customerOrderNumber??value.orderNumber.slice(-6)} · {reminderTime(value,locale)}</small></span>
      <span><T text={getOrderStatusPresentation(value.orderStatus,value.fulfillmentType).label}/></span>
     </button>
     {expanded===value.orderNumber&&<OrderActions order={value}/>}
    </article>)}</section>)}
    <Link className="active-order-history" href="/orders?status=ACTIVE"><T text="View all orders"/> →</Link>
   </div>
  </MenuDiscoverySheet>}
 </>;
}
