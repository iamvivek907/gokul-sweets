"use client";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {REQUEST_MENU_PICKUP} from "@/lib/menuPickupRecovery";
import {confirmWalkthroughPickup,OPEN_ORDERING_TOUR} from "@/lib/orderingTour";
import Dialog from "@/components/checkout/MobilePickupDialog";
import {useCallback,useEffect,useLayoutEffect,useMemo,useRef,useState,useSyncExternalStore} from "react";
import {createPortal} from "react-dom";
import {savePickupSlot,getPickupSlotSnapshot,getServerPickupSlotSnapshot,subscribeToPickupSlot,clearPickupSlot} from "@/lib/checkoutStorage";
import {savePickupIntent} from "@/hooks/usePickupIntent";
import {getMatchingStoredBranchSnapshot} from "@/lib/branchStorage";
import {getCartSnapshot,getServerCartSnapshot,subscribeToCart,parseCart} from "@/lib/cartStorage";
import {availabilityItems,checkCartAvailability,discoverPickupDates,MENU_READ_RETRY_DEADLINE_MS,retryMenuRead,type CartAvailability} from "@/services/availabilityApi";
import {ApiError} from "@/services/apiClient";
import {cartPickupOptions,branchPickupOptions} from "@/lib/menuPickupOptions";
import {T,useLanguage,translate} from "@/lib/language";
import type {MenuProduct} from "@/types/menu";
import type {PickupSelection} from "@/types/pickup";
export default function MobileMenuPickup({compactHeader=false,showHelp=false,branchId,products,today,days,selection,date,expired,selectionUnavailable=false,noPickupMessage,availabilityError,onRetry,automatic=false,onChoosingChange}:{compactHeader?:boolean;showHelp?:boolean;branchId:number;products:MenuProduct[];today:string;days:number;selection:PickupSelection|null;date?:string|null;expired:boolean;selectionUnavailable?:boolean;noPickupMessage?:string|null;availabilityError?:string|null;onRetry?:()=>void;automatic?:boolean;onChoosingChange?:(value:boolean)=>void}){
 const locale=useLanguage();
 const {branch}=useSelectedBranch();
 const branchName=branch?.id===branchId?branch.name:undefined;
 const branchPhone=branch?.id===branchId?branch.phone:undefined;
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
 const [pickerError,setPickerError]=useState("");
 const [candidateId,setCandidateId]=useState<number|null>(null);
 const cartSnapshot=useSyncExternalStore(subscribeToCart,getCartSnapshot,getServerCartSnapshot);
 const pickupSnapshot=useSyncExternalStore(subscribeToPickupSlot,getPickupSlotSnapshot,getServerPickupSlotSnapshot);
 const cart=useMemo(()=>parseCart(cartSnapshot),[cartSnapshot]);
 const includesCandidate=!!cart.items.length&&candidateId!==null&&!cart.items.some(item=>item.product.id===candidateId);
 const candidate=candidateId===null ? undefined : products.find(product=>product.id===candidateId);
 const candidateAvailable=candidate?.available;
 const candidateMode=includesCandidate ? candidate?.saleMode : undefined;
 const candidateWeight=includesCandidate ? candidate?.minimumWeightGrams : undefined;
 const candidateMissing=includesCandidate&&!candidate;
 const requested=useMemo(()=>{
  const items=availabilityItems(cart.items,cart);
  if(candidateId!==null&&candidateMode){
   items.push({productId:candidateId,quantity:candidateMode==="UNIT"?1:null,
    weightGrams:candidateMode==="WEIGHT"?candidateWeight??250:null});
  }
  return items;
 },[cart,candidateId,candidateMode,candidateWeight]);
 const controller=useRef<AbortController|null>(null);
 const [pickerTrigger,setPickerTrigger]=useState<HTMLElement|null>(null);

 const readPickupOptions=useCallback((startDate:string,dayCount:number,signal:AbortSignal)=>
  retryMenuRead(attempt=>requested.length
   ? checkCartAvailability(branchId,startDate,dayCount,requested,attempt)
   : discoverPickupDates(branchId,startDate,dayCount,attempt),signal),[branchId,requested]);
 const isCurrentPickupRequest=(signal:AbortSignal)=>!signal.aborted
  &&getMatchingStoredBranchSnapshot(branchId)!==null
  &&pickupSnapshot===getPickupSlotSnapshot()&&cartSnapshot===getCartSnapshot();

 useLayoutEffect(()=>{
  if(!open)return;
  const c=new AbortController();
  controller.current=c;
  queueMicrotask(async()=>{
   if(c.signal.aborted)return;
   setBusy(true);setData(null);setError("");setPickerError("");
   try{
    if(cart.items.length&&cart.branchId!==branchId)throw new Error("Choose your cart’s branch before changing pickup.");
    if(candidateMissing){
     setPickerError("This item is no longer available.");
     return;
    }
    if(requested.length>100)throw new Error("Review this large cart at checkout before changing pickup.");
    const signal=AbortSignal.any([c.signal,AbortSignal.timeout(MENU_READ_RETRY_DEADLINE_MS)]);
    const value=await readPickupOptions(today,days+1,signal);
    if(c.signal.aborted || cartSnapshot!==getCartSnapshot() || pickupSnapshot!==getPickupSlotSnapshot())return;
    if(getMatchingStoredBranchSnapshot(branchId)===null)throw new Error("Your branch changed. Open pickup times again.");
    setData(value);
   }catch(error){
    if(c.signal.aborted||cartSnapshot!==getCartSnapshot()||pickupSnapshot!==getPickupSlotSnapshot())return;
    if(error instanceof ApiError&&error.status===400){
     setPickerError(error.message);
     return;
    }
    setOpen(false);onChoosingChange?.(false);
    setError(error instanceof Error?error.message:"We couldn’t load pickup times. Your cart is saved. Try again.");
   }finally{
    if(!c.signal.aborted)setBusy(false);
   }
  });
  return()=>{c.abort();controller.current?.abort();};
 },[open,branchId,today,days,cart,cartSnapshot,pickupSnapshot,requested,candidateMissing,candidateAvailable,onChoosingChange,readPickupOptions]);

 function choose(trigger:HTMLElement,id:number|null=null){
  if(busy||open)return;
  setCandidateId(id);
  setPickerTrigger(trigger);setData(null);setPickerError("");setBusy(true);setOpen(true);
  onChoosingChange?.(true);
 }
 useEffect(()=>{
  const recover=(event:Event)=>{
   const id=(event as CustomEvent<{productId:number}>).detail?.productId;
   if(!products.some(product=>product.id===id&&product.available))return;
   const target=pickupRef.current?.querySelector<HTMLElement>('[data-ordering-target="pickup"]');
   if(target)choose(target,id);
  };
  window.addEventListener(REQUEST_MENU_PICKUP,recover);
  return()=>window.removeEventListener(REQUEST_MENU_PICKUP,recover);
 });
 const searchMenu=(event:{currentTarget:HTMLButtonElement})=>{
  window.dispatchEvent(new CustomEvent('gokul-menu-search-open',{detail:event.currentTarget}));
 };
 const help=showHelp&&<button type="button" className="menu-pickup-help ordering-tour-replay" aria-label={translate("How to order",locale)} onClick={()=>window.dispatchEvent(new Event(OPEN_ORDERING_TOUR))}><svg aria-hidden="true" viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 1 1 3.7 2.2c-.8.5-1.2 1-1.2 2.3M12 17h.01" strokeLinecap="round"/></svg></button>;
 const compactLabel=expired||selectionUnavailable||!selection?translate('Choose pickup date & time',locale):`${namedDate}`;
 const compactError=error||availabilityError;
 const compactTime=selection&&!expired&&!selectionUnavailable?`${timeLabel(selection.slot.startTime)}–${timeLabel(selection.slot.endTime)} IST`:translate('Your cart is saved',locale);
 const notices = <>
 {expired&&<p className="menu-pickup-conflict" role="status"><T text="Your previous pickup has passed. Choose a new time; your cart is saved."/></p>}
 {!expired&&selectionUnavailable&&<p className="menu-pickup-conflict" role="status"><T text="Your saved pickup time no longer fits your cart. Adjust items or choose another time. Your cart is saved."/></p>}
 {noPickupMessage&&<p className="menu-pickup-conflict" role="status"><T text={noPickupMessage}/><span className="pickup-unavailable-help"><T text="Standard pickup is unavailable for the checked dates. Check other pickup options or call the branch; the shop may still be open."/></span>{branchPhone&&<> <a className="pickup-call-branch" href={`tel:${branchPhone}`}><T text="Call branch" /></a></>}</p>}
 {availabilityError&&<p role="alert" className="menu-pickup-refresh-error" title={availabilityError}><span><T text={compactHeader?"Availability check failed. Your cart is saved.":availabilityError}/></span> <button type="button" onClick={onRetry}><T text="Retry availability"/></button></p>}
 {error&&<p role="alert"><T text={error}/></p>}
</>;
 return <>{headerHost&&createPortal(<div className="menu-compact-header" aria-label="Menu pickup header" aria-hidden={!compact} inert={!compact}><button className="menu-compact-pickup" data-ordering-target={compact?"pickup":undefined} type="button" disabled={busy||open} onClick={event=>{if(availabilityError&&!error){onRetry?.();return;}void choose(event.currentTarget);}} aria-label={`${compactLabel} · ${busy?translate('Checking times…',locale):compactError?translate('Pickup times unavailable. Tap to retry.',locale):compactTime}`}><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><rect x="4" y="5" width="16" height="16" rx="2"/><path d="M8 2v6m8-6v6M4 10h16"/></svg><span><strong>{compactLabel}</strong><small role={compactError?"alert":undefined}>{busy?<T text="Checking times…"/>:compactError?<T text="Pickup times unavailable. Tap to retry."/>:compactTime}</small></span><span aria-hidden="true">⌄</span></button><button id="gokul-menu-header-search" className="menu-compact-search" type="button" aria-label="Search the menu" onClick={searchMenu}><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><circle cx="10.5" cy="10.5" r="7.5"/><path d="m16 16 5 5"/></svg></button></div>,headerHost)}<section ref={pickupRef} className="mobile-menu-pickup" data-menu-refined={compactHeader || undefined} data-pickup-state={expired||selectionUnavailable||noPickupMessage||availabilityError||error?"attention":selection?"selected":"choose"} aria-label="Menu pickup time"><svg className="reference-pickup-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><rect x="4" y="5" width="16" height="16" rx="2"/><path d="M8 2v6m8-6v6M4 10h16"/></svg><div><span><T text={automatic?"Soonest pickup":"PICKUP TIME"}/>{branchName&&<> · {branchName}</>}</span><strong>{selection&&!expired&&!selectionUnavailable?`${namedDate} · ${timeLabel(selection.slot.startTime)}–${timeLabel(selection.slot.endTime)} IST`:displayDate?`${namedDate} · ${translate("Time not selected",locale)}`:noPickupMessage?<T text="No normal pickup times found"/>:<T text="Choose pickup date & time"/> }</strong></div><button className="relative" data-ordering-target="pickup" type="button" aria-busy={busy} aria-label={busy?translate("Checking times…",locale):undefined} disabled={busy||open} onClick={event=>void choose(event.currentTarget)}><span className={busy?"invisible":undefined}><T text={selection?"Change time":"Choose time"}/></span>{busy&&<span className="absolute inset-0 flex items-center justify-center"><T text="Checking times…"/></span>}</button>{help}
 {compactHeader?<div className="menu-pickup-notices">{notices}</div>:notices}
 {open&&<Dialog branchPhone={branchPhone} availabilityError={pickerError} checkingItem={products.find(p=>p.id===candidateId)?.name} loading={busy} restoreFocus={pickerTrigger} advisory today={today} dates={data?.dates??[]} options={data?(requested.length?cartPickupOptions(data):branchPickupOptions(data)):[]} chosen={selection} initialDate={date??today} disabled={busy} onClose={()=>{controller.current?.abort();setBusy(false);setOpen(false);onChoosingChange?.(false);}} onConfirm={async value=>{
  if(getMatchingStoredBranchSnapshot(branchId)===null||cartSnapshot!==getCartSnapshot()||pickupSnapshot!==getPickupSlotSnapshot()||candidateMissing||pickerError)return null;
  const amounts=new Set(requested.map(item=>item.productId));
  const c=new AbortController();controller.current=c;
  const signal=AbortSignal.any([c.signal,AbortSignal.timeout(MENU_READ_RETRY_DEADLINE_MS)]);
  let fresh: CartAvailability;
  try{
   fresh=await readPickupOptions(value.date,1,signal);
  }catch(error){
   if(!isCurrentPickupRequest(c.signal))return null;
   if(!(error instanceof ApiError)||error.status!==400)throw error;
   // A rejection for one date does not establish that the entire booking window is closed.
   try{
    const recoverySignal=AbortSignal.any([c.signal,AbortSignal.timeout(MENU_READ_RETRY_DEADLINE_MS)]);
    const updated=await readPickupOptions(today,days+1,recoverySignal);
    if(!isCurrentPickupRequest(c.signal))return null;
    setData(updated);
    return false;
   }catch(refreshError){
    if(!isCurrentPickupRequest(c.signal))return null;
    if(!(refreshError instanceof ApiError)||refreshError.status!==400)throw refreshError;
    setData(null);
    setPickerError(refreshError.message);
    return null;
   }
  }
  if(!isCurrentPickupRequest(c.signal))return null;
  const slot=fresh.dates.find(day=>day.date===value.date)?.slots.find(slot=>slot.slot.id===value.slot.id);
  const conflict=slot?.issues?.find(issue=>!issue.available&&amounts.has(issue.productId));
  if(conflict)throw new Error(`${conflict.productName}: ${conflict.reason??"Not available for this pickup."} Adjust your cart or choose another time. Your previous pickup is saved.`);
  const checked=(requested.length?cartPickupOptions(fresh):branchPickupOptions(fresh)).find(s=>s.slot.id===value.slot.id&&s.date===value.date&&s.pickupType===value.pickupType);
  if(!checked){setData(previous=>previous?{...previous,dates:previous.dates.map(d=>fresh.dates.find(f=>f.date===d.date)??d)}:fresh);return false;}
  clearPickupSlot();savePickupIntent(branchId,checked.date);savePickupSlot(checked);confirmWalkthroughPickup(branchId);setOpen(false);onChoosingChange?.(false);return true;
 }}/>}</section></>;
}
