"use client";
import {confirmWalkthroughPickup} from "@/lib/orderingTour";
import dynamic from "next/dynamic";
import {useEffect,useRef,useState} from "react";
import {savePickupSlot,getPickupSlotSnapshot,clearPickupSlot} from "@/lib/checkoutStorage";
import {savePickupIntent} from "@/hooks/usePickupIntent";
import {getStoredBranchSnapshot} from "@/lib/branchStorage";
import {getCartSnapshot,parseCart} from "@/lib/cartStorage";
import {availabilityItems,checkCartAvailability,checkMenuAvailability,discoverPickupDates,type CartAvailability} from "@/services/availabilityApi";
import {menuPickupOptions} from "@/lib/menuPickupOptions";
import {T,useLanguage,translate} from "@/lib/language";
import type {MenuProduct} from "@/types/menu";
import type {PickupSelection} from "@/types/pickup";
const Dialog=dynamic(()=>import("@/components/checkout/MobilePickupDialog"));
export default function MobileMenuPickup({branchId,products,today,days,selection,date,expired,selectionUnavailable=false,noPickupMessage,availabilityError,onRetry}:{branchId:number;products:MenuProduct[];today:string;days:number;selection:PickupSelection|null;date?:string|null;expired:boolean;selectionUnavailable?:boolean;noPickupMessage?:string|null;availabilityError?:string|null;onRetry?:()=>void}){
 const locale=useLanguage();
 const displayDate=selection?.date??date;
 const dateLabel=displayDate?new Intl.DateTimeFormat(locale==="hi"?"hi-IN":"en-IN",{weekday:"short",day:"numeric",month:"short",timeZone:"Asia/Kolkata"}).format(new Date(`${displayDate}T12:00:00+05:30`)):"";
 const namedDate=displayDate===today?translate("Today",locale):dateLabel;
 const timeLabel=(time:string)=>new Intl.DateTimeFormat(locale==="hi"?"hi-IN":"en-IN",{hour:"numeric",minute:"2-digit",hour12:true,timeZone:"Asia/Kolkata"}).format(new Date(`2000-01-01T${time}+05:30`));
 const [data,setData]=useState<CartAvailability|null>(null),[open,setOpen]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState("");
 const request=JSON.stringify(products.filter(p=>p.available).map(p=>({productId:p.id,quantity:p.saleMode==="UNIT"?1:null,weightGrams:p.saleMode==="WEIGHT"?p.minimumWeightGrams??250:null})));
 const controller=useRef<AbortController|null>(null);
 useEffect(()=>{
  let active=true;
  queueMicrotask(()=>{if(active){setBusy(false);setOpen(false);setData(null);setError("");}});
  return()=>{active=false;controller.current?.abort();};
 },[branchId,request]);
 async function choose(){
  if(busy)return;
  setBusy(true);setError("");const c=new AbortController();controller.current=c;
  try{const value=await discoverPickupDates(branchId,today,days+1,AbortSignal.any([c.signal,AbortSignal.timeout(15000)]));if(!c.signal.aborted){setData(value);setOpen(true);}}
  catch{if(!c.signal.aborted)setError("We couldn’t load pickup times. Your cart is saved. Try again.");}
  finally{if(!c.signal.aborted)setBusy(false);}
 }
 const ids=JSON.parse(request).map((item:{productId:number})=>item.productId);
 return <section className="mobile-menu-pickup" aria-label="Menu pickup time"><svg className="reference-pickup-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><rect x="4" y="5" width="16" height="16" rx="2"/><path d="M8 2v6m8-6v6M4 10h16"/></svg><div><span><T text="PICKUP TIME"/></span><strong>{selection?`${namedDate} · ${timeLabel(selection.slot.startTime)}–${timeLabel(selection.slot.endTime)} IST`:displayDate?`${namedDate} · ${translate("Time not selected",locale)}`:<T text="Choose pickup date & time"/>}</strong></div><button data-ordering-target="pickup" type="button" disabled={busy||!ids.length} onClick={()=>void choose()}><T text={busy?"Checking times…":selection?"Change time":"Choose time"}/></button>
 {expired&&<p className="menu-pickup-conflict" role="status"><T text="Your previous pickup has passed. Choose a new time; your cart is saved."/></p>}
 <p role="status"><T text={displayDate?"Availability follows your selected pickup time. Confirm it at checkout.":"Choose pickup to see which items you can add."}/></p>
 {selectionUnavailable&&<p className="menu-pickup-conflict" role="status"><T text="Your saved pickup time no longer fits your cart. Adjust items or choose another time. Your cart is saved."/></p>}
 {noPickupMessage&&<p className="menu-pickup-conflict" role="status">{noPickupMessage}</p>}
 {availabilityError&&<p role="alert">{availabilityError} <button type="button" onClick={onRetry}>Retry availability</button></p>}
 {error&&<p role="alert">{error}</p>}
 {open&&data&&<Dialog advisory today={today} dates={data.dates} options={menuPickupOptions(data,ids)} chosen={selection} initialDate={date??today} disabled={false} onClose={()=>{controller.current?.abort();setOpen(false);}} onConfirm={async value=>{
  const branch=getStoredBranchSnapshot(),pickup=getPickupSlotSnapshot(),cartSnapshot=getCartSnapshot();
  const cart=parseCart(cartSnapshot);
  if(cart.items.length&&cart.branchId!==branchId)throw new Error("Choose your cart’s branch before changing pickup.");
  // Keep actual cart validation together; batch unchosen catalogue items.
  const cartItems=availabilityItems(cart.items);
  if(cartItems.length>100)throw new Error("Review this large cart at checkout before changing pickup. Your previous pickup is saved.");
  const amounts=new Map(cartItems.map(item=>[item.productId,item]));
  const menuItems=JSON.parse(request) as ReturnType<typeof availabilityItems>;
  const requested=cartItems.length?cartItems:menuItems;
  const c=new AbortController();controller.current=c;
  const fresh=await (cartItems.length?checkCartAvailability:checkMenuAvailability)(branchId,value.date,1,requested,AbortSignal.any([c.signal,AbortSignal.timeout(15000)]),true);
  if(c.signal.aborted||branch!==getStoredBranchSnapshot()||pickup!==getPickupSlotSnapshot()||cartSnapshot!==getCartSnapshot())return false;
  const slot=fresh.dates.find(day=>day.date===value.date)?.slots.find(slot=>slot.slot.id===value.slot.id);
  const conflict=slot?.issues?.find(issue=>!issue.available&&amounts.has(issue.productId));
  if(conflict)throw new Error(`${conflict.productName}: ${conflict.reason??"Not available for this pickup."} Adjust your cart or choose another time. Your previous pickup is saved.`);
  const checked=menuPickupOptions(fresh,requested.map(item=>item.productId)).find(s=>s.slot.id===value.slot.id&&s.date===value.date&&s.pickupType===value.pickupType);
  if(!checked){setData(previous=>previous?{...previous,dates:previous.dates.map(d=>fresh.dates.find(f=>f.date===d.date)??d)}:fresh);return false;}
  clearPickupSlot();savePickupIntent(branchId,checked.date);savePickupSlot(checked);confirmWalkthroughPickup(branchId);setOpen(false);return true;
 }}/>}</section>;
}
