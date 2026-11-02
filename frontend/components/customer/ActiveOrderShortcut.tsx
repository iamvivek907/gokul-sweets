"use client";
import {useEffect,useState,useSyncExternalStore} from "react";
import Link from "next/link";
import CustomerIcon from "./CustomerIcon";
import {getActiveOrder,getServerActiveOrder,subscribeActiveOrder} from "@/lib/activeOrderStore";
import {getOrderStatusPresentation,formatOrderDate,formatOrderTime} from "@/lib/orderTracking";
import {checkOperationalBranch} from "@/lib/branchOperationalCache";
import {T,useLanguage} from "@/lib/language";
import type {Branch} from "@/types/branch";
import type {CustomerOrderSummaryResponse} from "@/types/order";
import MenuDiscoverySheet from "@/components/menu/MenuDiscoverySheet";
function OrderActions({order}:{order:CustomerOrderSummaryResponse}){
 const [branch,setBranch]=useState<Branch|null>(null);
 useEffect(()=>{let current=true;void checkOperationalBranch(order.branchId).then(value=>{if(current)setBranch(value);}).catch(()=>{});return()=>{current=false;};},[order.branchId]);
 const href=`/orders/${encodeURIComponent(order.orderNumber)}`;
 const code=order.fulfillmentType==='PICKUP'&&['CONFIRMED','PREPARING','READY_FOR_PICKUP'].includes(order.orderStatus);
 return <div className="active-order-actions"><Link className="active-order-primary" href={href}><T text={order.orderStatus==='PENDING_PAYMENT'?'Continue payment':'View order'}/></Link>{code&&<Link href={`${href}#pickup-code`}><T text="View pickup code"/></Link>}{branch?.id===order.branchId&&<>{branch.phone&&<a href={`tel:${branch.phone.replace(/[^+\d]/g,'')}`}><T text="Call"/></a>}{branch.address&&<a target="_blank" rel="noopener noreferrer" href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent([branch.name,branch.address,branch.city,branch.pincode].filter(Boolean).join(', '))}`}><T text="Directions"/></a>}</>}</div>;
}
export default function ActiveOrderShortcut(){
 const {order,orders,more,stale}=useSyncExternalStore(subscribeActiveOrder,getActiveOrder,getServerActiveOrder);
 const [open,setOpen]=useState(false),[expanded,setExpanded]=useState<string|null>(null),[bottom,setBottom]=useState<number|null>(null);
 const [trigger,setTrigger]=useState<HTMLElement|null>(null);const locale=useLanguage();
 useEffect(()=>{
  let cart:HTMLElement|null=null;
  const update=()=>{const next=document.querySelector<HTMLElement>('.gokul-floating-cart');if(next!==cart){if(cart)observer.unobserve(cart);cart=next;if(cart)observer.observe(cart);}setBottom(cart?Math.max(0,innerHeight-cart.getBoundingClientRect().top)+4:null);};
  const observer=new ResizeObserver(update),mutations=new MutationObserver(update);
  mutations.observe(document.body,{childList:true,subtree:true});update();window.addEventListener('resize',update);
  return()=>{observer.disconnect();mutations.disconnect();window.removeEventListener('resize',update);};
 },[]);
 if(!order)return null;
 const date=order.fulfillmentType==='DELIVERY'?order.deliveryDate:order.pickupDate,time=order.fulfillmentType==='DELIVERY'?order.deliveryStartTime:order.pickupStartTime;
 return <><aside className="active-order-shortcut" data-stale={stale||undefined} aria-label="Active order" style={bottom===null?undefined:{bottom}}><button type="button" className="active-order-tracker" onClick={event=>{setTrigger(event.currentTarget);setExpanded(order.orderNumber);setOpen(true);}} aria-haspopup="dialog"><span className="active-order-mark" aria-hidden="true"><CustomerIcon kind="receipt"/><i/></span><span className="active-order-tracker-copy"><strong>{orders.length>1?`${orders.length}${more?'+':''} ${locale==='hi'?'आगामी ऑर्डर':'upcoming orders'}`:<T text={getOrderStatusPresentation(order.orderStatus,order.fulfillmentType).label}/>}</strong><small>{stale?<T text="Open for latest status"/>:<>{date&&formatOrderDate(date)}{time&&` · ${formatOrderTime(time)}`} · {order.branchName}</>}</small></span><span className="active-order-view"><T text={orders.length>1?'View all':'View'}/> ›</span></button></aside>{open&&<MenuDiscoverySheet title="Your upcoming orders" tactile restoreFocus={trigger} onClose={()=>setOpen(false)}><div className="active-order-list">{stale&&<p role="status"><T text="Status may have changed. Open your order for the latest update."/></p>}{orders.map(value=>{const day=value.fulfillmentType==='DELIVERY'?value.deliveryDate:value.pickupDate,start=value.fulfillmentType==='DELIVERY'?value.deliveryStartTime:value.pickupStartTime;return <article key={value.orderNumber}><button type="button" aria-expanded={expanded===value.orderNumber} onClick={()=>setExpanded(expanded===value.orderNumber?null:value.orderNumber)}><span><strong>{value.branchName}</strong><small>#{value.customerOrderNumber??value.orderNumber.slice(-6)} · {day&&formatOrderDate(day)}{start&&` · ${formatOrderTime(start)}`}</small></span><span><T text={getOrderStatusPresentation(value.orderStatus,value.fulfillmentType).label}/></span></button>{expanded===value.orderNumber&&<OrderActions order={value}/>}</article>;})}<Link className="active-order-history" href="/orders?status=ACTIVE"><T text="View all orders"/> →</Link></div></MenuDiscoverySheet>}</>;
}
