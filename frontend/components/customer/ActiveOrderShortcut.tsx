"use client";
import {useEffect,useState,useSyncExternalStore} from "react";
import Link from "next/link";
import {getActiveOrder,getServerActiveOrder,subscribeActiveOrder} from "@/lib/activeOrderStore";
import {getOrderStatusPresentation,formatOrderDate,formatOrderTime} from "@/lib/orderTracking";
import {checkOperationalBranch} from "@/lib/branchOperationalCache";
import {T} from "@/lib/language";
import type {Branch} from "@/types/branch";

export default function ActiveOrderShortcut(){
 const {order,stale}=useSyncExternalStore(subscribeActiveOrder,getActiveOrder,getServerActiveOrder);
 const [branch,setBranch]=useState<Branch|null>(null);
 const branchId=order?.branchId;
 useEffect(()=>{let current=true;if(branchId)void checkOperationalBranch(branchId).then(value=>{if(current)setBranch(value);}).catch(()=>{if(current)setBranch(null);});return()=>{current=false;};},[branchId]);
 if(!order)return null;
 const status=getOrderStatusPresentation(order.orderStatus,order.fulfillmentType),href=`/orders/${encodeURIComponent(order.orderNumber)}`;
 const contact=branch?.id===order.branchId?branch:null;
 const date=order.fulfillmentType==="DELIVERY"?order.deliveryDate:order.pickupDate,start=order.fulfillmentType==="DELIVERY"?order.deliveryStartTime:order.pickupStartTime;
 const code=order.fulfillmentType==="PICKUP"&&["CONFIRMED","PREPARING","READY_FOR_PICKUP"].includes(order.orderStatus);
 return <aside className="active-order-shortcut" aria-label="Active order"><div><span className="active-order-eyebrow"><T text="Your active order"/></span><h2><T text={status.label}/></h2><p>{order.branchName}{date?` · ${formatOrderDate(date)}`:""}{start?` · ${formatOrderTime(start)} IST`:""}</p>{stale&&<p role="status"><T text="Status may have changed. Open your order for the latest update."/></p>}</div><div className="active-order-actions"><Link className="active-order-primary" href={href}><T text={order.orderStatus==="PENDING_PAYMENT"?"Continue payment":"View order"}/><span aria-hidden="true"> ↗</span></Link>{code&&<Link href={`${href}#pickup-code`}><T text="View pickup code"/></Link>}{contact?.phone&&<a href={`tel:${contact.phone.replace(/[^+\d]/g,"")}`}><T text="Call"/></a>}{contact?.address&&<a target="_blank" rel="noopener noreferrer" href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent([contact.name,contact.address,contact.city,contact.pincode].filter(Boolean).join(", "))}`}><T text="Directions"/></a>}</div></aside>;
}
