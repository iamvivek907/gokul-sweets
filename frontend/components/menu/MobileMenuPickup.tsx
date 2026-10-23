"use client";
import {REQUEST_MENU_PICKUP} from "@/lib/menuPickupRecovery";
import {confirmWalkthroughPickup} from "@/lib/orderingTour";
import Dialog from "@/components/checkout/MobilePickupDialog";
import {useEffect,useRef,useState} from "react";
import {createPortal} from "react-dom";
import {savePickupSlot,getPickupSlotSnapshot,clearPickupSlot} from "@/lib/checkoutStorage";
import {savePickupIntent} from "@/hooks/usePickupIntent";
import {getStoredBranchSnapshot} from "@/lib/branchStorage";
import {getCartSnapshot,parseCart} from "@/lib/cartStorage";
import {availabilityItems,checkCartAvailability,checkMenuAvailability,discoverPickupDates,type CartAvailability} from "@/services/availabilityApi";
import {cartPickupOptions,menuPickupOptions} from "@/lib/menuPickupOptions";
import {T,useLanguage,translate} from "@/lib/language";
import type {MenuProduct} from "@/types/menu";
import type {PickupSelection} from "@/types/pickup";
export default function MobileMenuPickup({compactHeader=false,branchId,products,today,days,selection,date,expired,selectionUnavailable=false,noPickupMessage,availabilityError,onRetry,automatic=false,findingSoonest=false,onChoosingChange}:{compactHeader?:boolean;branchId:number;products:MenuProduct[];today:string;days:number;selection:PickupSelection|null;date?:string|null;expired:boolean;selectionUnavailable?:boolean;noPickupMessage?:string|null;availabilityError?:string|null;onRetry?:()=>void;automatic?:boolean;findingSoonest?:boolean;onChoosingChange?:(value:boolean)=>void}){
 const locale=useLanguage();
 const pickupRef=useRef<HTMLElement>(null);
 const [headerHost,setHeaderHost]=useState<HTMLElement|null>(null),[compact,setCompact]=useState(false);
 useEffect(()=>{
  if(!compactHeader)return;
  const pickup=pickupRef.current,header=pickup?.closest('.app-container')?.querySelector<HTMLElement>('.customer-site-header');
  const host=header?.querySelector<HTMLElement>('#gokul-menu-header-slot');
  if(!pickup||!header||!host)return;
  setHeaderHost(host);
  const content=header.querySelector<HTMLElement>('[data-header-content]');
  let active=false,frame=0;
  const measure=()=>{
   frame=0;
   // The original selector stays in flow. Neither threshold nor header height changes on promotion.
   const phone=matchMedia('(max-width:640px)').matches;
   const bottom=pickup.getBoundingClientRect().bottom,edge=header.getBoundingClientRect().bottom;
   const next=phone&&(active?bottom<edge+24:bottom<edge-12);
   if(next!==active){active=next;setCompact(next);}
   header.toggleAttribute('data-menu-compact',next);
   if(content){content.inert=next;if(next)content.setAttribute('aria-hidden','true');else content.removeAttribute('aria-hidden');}
  };
  const schedule=()=>{if(!frame)frame=requestAnimationFrame(measure);};
  const resize=new ResizeObserver(schedule);resize.observe(pickup);resize.observe(header);
  window.addEventListener('scroll',schedule,{passive:true});window.addEventListener('resize',schedule);measure();
  return()=>{cancelAnimationFrame(frame);resize.disconnect();window.removeEventListener('scroll',schedule);window.removeEventListener('resize',schedule);header.removeAttribute('data-menu-compact');if(content){content.inert=false;content.removeAttribute('aria-hidden');}setHeaderHost(null);setCompact(false);};
 },[compactHeader]);
 const displayDate=selection?.date??date;
 const dateLabel=displayDate?new Intl.DateTimeFormat(locale==="hi"?"hi-IN":"en-IN",{weekday:"short",day:"numeric",month:"short",timeZone:"Asia/Kolkata"}).format(new Date(`${displayDate}T12:00:00+05:30`)):"";
 const tomorrow=new Date(`${today}T12:00:00+05:30`);tomorrow.setUTCDate(tomorrow.getUTCDate()+1);
 const tomorrowDate=new Intl.DateTimeFormat("en-CA",{timeZone:"Asia/Kolkata",year:"numeric",month:"2-digit",day:"2-digit"}).format(tomorrow);
 const relativeDate=displayDate===today?translate("Today",locale):displayDate===tomorrowDate?translate("Tomorrow",locale):"";
 const namedDate=relativeDate?`${relativeDate}, ${dateLabel}`:dateLabel;
 const timeLabel=(time:string)=>new Intl.DateTimeFormat(locale==="hi"?"hi-IN":"en-IN",{hour:"numeric",minute:"2-digit",hour12:true,timeZone:"Asia/Kolkata"}).format(new Date(`2000-01-01T${time}+05:30`));
 const [data,setData]=useState<CartAvailability|null>(null),[open,setOpen]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState("");
 const request=JSON.stringify(products.filter(p=>p.available).map(p=>({productId:p.id,quantity:p.saleMode==="UNIT"?1:null,weightGrams:p.saleMode==="WEIGHT"?p.minimumWeightGrams??250:null})));
 const [cartIds,setCartIds]=useState<number[]>([]);
 const controller=useRef<AbortController|null>(null);
 const [pickerTrigger,setPickerTrigger]=useState<HTMLElement|null>(null);
 useEffect(()=>{
  let active=true;
  queueMicrotask(()=>{if(active){setBusy(false);setOpen(false);setData(null);setError("");onChoosingChange?.(false);}});
  return()=>{active=false;controller.current?.abort();};
 },[branchId,request,onChoosingChange]);
 const candidateRef=useRef<number|null>(null);
 useEffect(()=>{
  const recover=(event:Event)=>{
   const id=(event as CustomEvent<{productId:number}>).detail?.productId;
   if(!products.some(p=>p.id===id&&p.available))return;
   const target=pickupRef.current?.querySelector<HTMLElement>('[data-ordering-target="pickup"]');
   if(target)void choose(target,id);
  };
  window.addEventListener(REQUEST_MENU_PICKUP,recover);
  return()=>window.removeEventListener(REQUEST_MENU_PICKUP,recover);
 });
 function requestedItems(cart:ReturnType<typeof parseCart>){
  const items=availabilityItems(cart.items),candidate=products.find(p=>p.id===candidateRef.current);
  if(candidate&&!items.some(item=>item.productId===candidate.id))items.push({productId:candidate.id,quantity:candidate.saleMode==="UNIT"?1:null,weightGrams:candidate.saleMode==="WEIGHT"?candidate.minimumWeightGrams??250:null});
  return items;
 }
 async function choose(trigger:HTMLElement,candidate:number|null=null){
  if(busy||open)return;
  candidateRef.current=candidate;
  setPickerTrigger(trigger);setData(null);setOpen(true);
  onChoosingChange?.(true);setBusy(true);setError("");const c=new AbortController();controller.current=c;
  try{
   const branchSnapshot=getStoredBranchSnapshot(),cartSnapshot=getCartSnapshot();
   const cart=parseCart(cartSnapshot);
   if(cart.items.length&&cart.branchId!==branchId)throw new Error("Choose your cart’s branch before changing pickup.");
   const items=requestedItems(cart);
   if(items.length>100)throw new Error("Review this large cart at checkout before changing pickup.");
   const signal=AbortSignal.any([c.signal,AbortSignal.timeout(15000)]);
   const value=items.length?await checkCartAvailability(branchId,today,days+1,items,signal):await discoverPickupDates(branchId,today,days+1,signal);
   if(!c.signal.aborted&&branchSnapshot===getStoredBranchSnapshot()&&cartSnapshot===getCartSnapshot()){
    setCartIds(items.map(item=>item.productId));setData(value);setOpen(true);
   }else if(!c.signal.aborted&&controller.current===c){setOpen(false);onChoosingChange?.(false);setError("Your cart or branch changed. Open pickup times again.");}
  }
  catch(error){if(!c.signal.aborted&&controller.current===c){setOpen(false);onChoosingChange?.(false);setError(error instanceof Error?error.message:"We couldn’t load pickup times. Your cart is saved. Try again.");}}
  finally{if(!c.signal.aborted&&controller.current===c)setBusy(false);}
 }
 const ids=JSON.parse(request).map((item:{productId:number})=>item.productId);
 const searchMenu=()=>{
  const input=document.getElementById('gokul-menu-search');
  input?.scrollIntoView({block:'center',behavior:'instant'});
  input?.focus({preventScroll:true});
 };
 const compactLabel=expired||selectionUnavailable||!selection?(findingSoonest?translate('Finding soonest pickup…',locale):translate('Choose pickup date & time',locale)):`${namedDate}`;
 const compactError=error||availabilityError;
 const compactTime=selection&&!expired&&!selectionUnavailable?`${timeLabel(selection.slot.startTime)}–${timeLabel(selection.slot.endTime)} IST`:translate('Your cart is saved',locale);
 const notices = <>
 {expired&&<p className="menu-pickup-conflict" role="status"><T text="Your previous pickup has passed. Choose a new time; your cart is saved."/></p>}
 {!expired&&selectionUnavailable&&<p className="menu-pickup-conflict" role="status"><T text="Your saved pickup time no longer fits your cart. Adjust items or choose another time. Your cart is saved."/></p>}
 {noPickupMessage&&<p className="menu-pickup-conflict" role="status"><T text={noPickupMessage}/></p>}
 {availabilityError&&<p role="alert"><T text={availabilityError}/> <button type="button" onClick={onRetry}><T text="Retry availability"/></button></p>}
 {error&&<p role="alert"><T text={error}/></p>}
</>;
 return <>{headerHost&&createPortal(<div className="menu-compact-header" aria-label="Menu pickup header" aria-hidden={!compact} inert={!compact}><button className="menu-compact-pickup" data-ordering-target={compact?"pickup":undefined} type="button" disabled={busy||open} onClick={event=>{if(availabilityError&&!error){onRetry?.();return;}void choose(event.currentTarget);}} aria-label={`${compactLabel} · ${busy?translate('Checking times…',locale):compactError?translate('Pickup times unavailable. Tap to retry.',locale):compactTime}`}><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><rect x="4" y="5" width="16" height="16" rx="2"/><path d="M8 2v6m8-6v6M4 10h16"/></svg><span><strong>{compactLabel}</strong><small role={compactError?"alert":undefined}>{busy?<T text="Checking times…"/>:compactError?<T text="Pickup times unavailable. Tap to retry."/>:compactTime}</small></span><span aria-hidden="true">⌄</span></button><button className="menu-compact-search" type="button" aria-label="Search the menu" onClick={searchMenu}><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><circle cx="10.5" cy="10.5" r="7.5"/><path d="m16 16 5 5"/></svg></button></div>,headerHost)}<section ref={pickupRef} className="mobile-menu-pickup" data-menu-refined={compactHeader || undefined} data-pickup-state={expired||selectionUnavailable||noPickupMessage||availabilityError||error?"attention":selection?"selected":"choose"} aria-label="Menu pickup time"><svg className="reference-pickup-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><rect x="4" y="5" width="16" height="16" rx="2"/><path d="M8 2v6m8-6v6M4 10h16"/></svg><div><span><T text={automatic?"Soonest pickup":"PICKUP TIME"}/></span><strong>{selection&&!expired&&!selectionUnavailable?`${namedDate} · ${timeLabel(selection.slot.startTime)}–${timeLabel(selection.slot.endTime)} IST`:findingSoonest?<T text="Finding soonest pickup…"/>:displayDate?`${namedDate} · ${translate("Time not selected",locale)}`:<T text="Choose pickup date & time"/> }</strong></div><button className="relative" data-ordering-target="pickup" type="button" aria-busy={busy} aria-label={busy?translate("Checking times…",locale):undefined} disabled={busy||open} onClick={event=>void choose(event.currentTarget)}><span className={busy?"invisible":undefined}><T text={selection?"Change time":"Choose time"}/></span>{busy&&<span className="absolute inset-0 flex items-center justify-center"><T text="Checking times…"/></span>}</button>
 {compactHeader?<div className="menu-pickup-notices">{notices}</div>:notices}
 {open&&<Dialog checkingItem={products.find(p=>p.id===candidateRef.current)?.name} loading={busy} restoreFocus={pickerTrigger} advisory today={today} dates={data?.dates??[]} options={data?(cartIds.length?cartPickupOptions(data):menuPickupOptions(data,ids)):[]} chosen={selection} initialDate={date??today} disabled={busy} onClose={()=>{controller.current?.abort();setBusy(false);setOpen(false);onChoosingChange?.(false);}} onConfirm={async value=>{
  const branch=getStoredBranchSnapshot(),pickup=getPickupSlotSnapshot(),cartSnapshot=getCartSnapshot();
  const cart=parseCart(cartSnapshot);
  if(cart.items.length&&cart.branchId!==branchId)throw new Error("Choose your cart’s branch before changing pickup.");
  // Keep actual cart validation together; batch unchosen catalogue items.
  const cartItems=requestedItems(cart);
  if(cartItems.length>100)throw new Error("Review this large cart at checkout before changing pickup. Your previous pickup is saved.");
  const amounts=new Map(cartItems.map(item=>[item.productId,item]));
  const menuItems=JSON.parse(request) as ReturnType<typeof availabilityItems>;
  const requested=cartItems.length?cartItems:menuItems;
  const c=new AbortController();controller.current=c;
  const fresh=await (cartItems.length?checkCartAvailability:checkMenuAvailability)(branchId,value.date,1,requested,AbortSignal.any([c.signal,AbortSignal.timeout(15000)]),!cartItems.length);
  if(c.signal.aborted||branch!==getStoredBranchSnapshot()||pickup!==getPickupSlotSnapshot()||cartSnapshot!==getCartSnapshot())return false;
  const slot=fresh.dates.find(day=>day.date===value.date)?.slots.find(slot=>slot.slot.id===value.slot.id);
  const conflict=slot?.issues?.find(issue=>!issue.available&&amounts.has(issue.productId));
  if(conflict)throw new Error(`${conflict.productName}: ${conflict.reason??"Not available for this pickup."} Adjust your cart or choose another time. Your previous pickup is saved.`);
  const checked=(cartItems.length?cartPickupOptions(fresh):menuPickupOptions(fresh,requested.map(item=>item.productId))).find(s=>s.slot.id===value.slot.id&&s.date===value.date&&s.pickupType===value.pickupType);
  if(!checked){setData(previous=>previous?{...previous,dates:previous.dates.map(d=>fresh.dates.find(f=>f.date===d.date)??d)}:fresh);return false;}
  clearPickupSlot();savePickupIntent(branchId,checked.date);savePickupSlot(checked);confirmWalkthroughPickup(branchId);setOpen(false);onChoosingChange?.(false);return true;
 }}/>}</section></>;
}
