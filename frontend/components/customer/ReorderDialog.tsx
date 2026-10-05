"use client";
import {useEffect,useRef,useState} from "react";
import {useRouter} from "next/navigation";
import {T} from "@/lib/language";
import {apiClient} from "@/services/apiClient";
import {getMenu} from "@/services/menuApi";
import {checkReorderAvailability} from "@/services/reorderAvailability";
import {getBranch} from "@/services/branchApi";
import {availabilityItems,type CartAvailability} from "@/services/availabilityApi";
import {mergeReorderAvailability,reorderPickupOptions,validReorderItems} from "@/lib/reorderPickup";
import {getCartSnapshot,parseCart,saveCart} from "@/lib/cartStorage";
import {getStoredBranchSnapshot,saveBranch} from "@/lib/branchStorage";
import {getPendingOrderSnapshot} from "@/lib/pendingOrderStorage";
import {getPickupSlotSnapshot,savePickupSlot} from "@/lib/checkoutStorage";
import {subscribeCustomerIdentityChanges} from "@/lib/customerIdentityEvents";
import {formatWeight} from "@/lib/orderQuantity";
import {formatOrderCurrency,formatOrderDate,formatOrderTime} from "@/lib/orderTracking";
import type {CustomerOrderResponse,CustomerOrderSummaryResponse} from "@/types/order";
import type {CustomerSession} from "./CustomerIdentityPanel";
import type {CartItem} from "@/types/cart";
import type {Branch} from "@/types/branch";
import type {StorefrontFeatures} from "@/hooks/useStorefrontFeatures";

const unfinished=()=>!!getPendingOrderSnapshot()||!!sessionStorage.getItem("gokul-mobile-order-attempt");
export default function ReorderDialog({order,phone,features,compact,onClose}:{order:CustomerOrderSummaryResponse;phone?:string;features:StorefrontFeatures;compact:boolean;onClose:()=>void}){
 const router=useRouter(),dialog=useRef<HTMLDialogElement>(null),lifetime=useRef(new AbortController());
 const [snapshot]=useState(()=>({cart:getCartSnapshot(),branch:getStoredBranchSnapshot(),pickup:getPickupSlotSnapshot()}));
 const [branch,setBranch]=useState<Branch|null>(null),[items,setItems]=useState<CartItem[]>([]),[missing,setMissing]=useState<string[]>([]);
 const [loading,setLoading]=useState(true),[error,setError]=useState(""),[revision,setRevision]=useState(0),[loadRevision,setLoadRevision]=useState(0),[checking,setChecking]=useState(false),[busy,setBusy]=useState(false);
 const [availability,setAvailability]=useState<{key:string;value:CartAvailability}|null>(null);
 const [date,setDate]=useState(""),[slotKey,setSlotKey]=useState(""),[replace,setReplace]=useState(false);
 const initialChoice=useRef(true),continuing=useRef(false);
 const itemsKey=JSON.stringify(availabilityItems(items));
 const legacyDate=features.smartAvailability?"":date||features.today;
 const availabilityKey=`${features.smartAvailability}:${legacyDate}:${itemsKey}`;
 const maximumDate=new Date(`${features.today}T00:00:00Z`);maximumDate.setUTCDate(maximumDate.getUTCDate()+(features.futureOrderingDays??30));
 useEffect(()=>{
  const controller=new AbortController();lifetime.current=controller;dialog.current?.showModal();
  const overflow=document.body.style.overflow;document.body.style.overflow="hidden";
  const unsubscribe=subscribeCustomerIdentityChanges(()=>{controller.abort();onClose();},{revalidateOnResume:false});
  return()=>{controller.abort();unsubscribe();document.body.style.overflow=overflow;};
 },[onClose]);
 useEffect(()=>{
  const controller=new AbortController(),signal=AbortSignal.any([controller.signal,lifetime.current.signal,AbortSignal.timeout(10000)]);
  Promise.all([apiClient<CustomerOrderResponse>(`/api/customer/identity/orders/${encodeURIComponent(order.orderNumber)}`,{credentials:"include",signal}),getMenu(order.branchId,signal),getBranch(order.branchId,signal)])
   .then(([detail,categories,currentBranch])=>{
    if(signal.aborted)return;if(!currentBranch.active||currentBranch.operational===false||currentBranch.pickupAvailable===false)throw new Error("This branch is not accepting pickup orders.");
    const menu=categories.flatMap(category=>category.products),unavailable:string[]=[];
    const lines=detail.items.flatMap(line=>{const product=menu.find(value=>value.id===line.productId&&value.saleMode===line.saleMode);
     if(!product){unavailable.push(line.productName);return [];}
     return [{product,quantity:product.saleMode==="WEIGHT"?1:line.quantity,weightGrams:product.saleMode==="WEIGHT"?line.weightGrams:null}];});
    setBranch(currentBranch);setItems(lines);setMissing(unavailable);setLoading(false);
   }).catch(reason=>{if(!controller.signal.aborted&&!lifetime.current.signal.aborted){setLoading(false);setError(reason instanceof Error?reason.message:"The order could not load. Please try again.");}});
  return()=>controller.abort();
 },[order.branchId,order.orderNumber,loadRevision]);
 useEffect(()=>{
  if(!branch||!validReorderItems(items)||missing.length)return;
  const controller=new AbortController();
  const timer=setTimeout(()=>{
   setChecking(true);setError("");
   void checkReorderAvailability(branch.id,legacyDate||features.today,Math.min(61,(features.futureOrderingDays??30)+1),items,features.smartAvailability,AbortSignal.any([controller.signal,lifetime.current.signal,AbortSignal.timeout(15000)]))
    .then(value=>{if(controller.signal.aborted||lifetime.current.signal.aborted)return;setAvailability({key:availabilityKey,value});
     const options=reorderPickupOptions(value);
     if(initialChoice.current){initialChoice.current=false;setDate(options[0]?.date??value.dates[0]?.date??"");setSlotKey(options[0]?`${options[0].slot.id}:${options[0].pickupType}`:"");}
     else if(!features.smartAvailability){setSlotKey(previous=>options.some(option=>`${option.slot.id}:${option.pickupType}`===previous)?previous:"");}
    }).catch(()=>{if(!controller.signal.aborted&&!lifetime.current.signal.aborted)setError("Pickup availability could not load. Please try again.");})
    .finally(()=>{if(!controller.signal.aborted&&!lifetime.current.signal.aborted)setChecking(false);});
  },300);
  return()=>{clearTimeout(timer);controller.abort();};
 },[branch,items,itemsKey,missing.length,features.today,features.futureOrderingDays,features.smartAvailability,legacyDate,availabilityKey,revision]);
 const current=availability?.key===availabilityKey?availability.value:null;
 const options=current?reorderPickupOptions(current):[],selected=options.find(value=>value.date===date&&`${value.slot.id}:${value.pickupType}`===slotKey);
 const existing=parseCart(snapshot.cart).items.length>0;
 function changeQuantity(index:number,value:number){setError("");setItems(lines=>lines.map((line,i)=>i!==index?line:line.product.saleMode==="WEIGHT"?{...line,weightGrams:value}:{...line,quantity:value}));}
 function close(){if(continuing.current)return;dialog.current?.close();onClose();}
 async function proceed(){
  if(continuing.current||!branch||!selected||!validReorderItems(items)||missing.length||existing&&!replace)return;
  continuing.current=true;setBusy(true);setError("");let navigated=false;
  try{
   if(unfinished())throw new Error("Resolve your unfinished checkout before reordering.");
   if(getCartSnapshot()!==snapshot.cart||getStoredBranchSnapshot()!==snapshot.branch||getPickupSlotSnapshot()!==snapshot.pickup)throw new Error("Your cart or pickup changed in another page. Close and reopen Reorder.");
   const signal=AbortSignal.any([lifetime.current.signal,AbortSignal.timeout(15000)]);
   const [fresh,categories,identity,currentBranch]=await Promise.all([
    checkReorderAvailability(branch.id,date,1,items,features.smartAvailability,signal),getMenu(branch.id,signal),
    apiClient<CustomerSession>("/api/customer/identity/me",{credentials:"include",signal}),getBranch(branch.id,signal)]);
   if(signal.aborted)return;
   if(!identity.authenticated||identity.phone!==phone)throw new Error("Your sign-in changed. Close Reorder and sign in again.");
   if(!currentBranch.active||currentBranch.operational===false||currentBranch.pickupAvailable===false)throw new Error("This branch is not accepting pickup orders.");
   const matching=reorderPickupOptions(fresh).find(value=>value.date===selected.date&&value.slot.id===selected.slot.id&&value.pickupType===selected.pickupType);
   if(!matching){setAvailability({key:availabilityKey,value:mergeReorderAvailability(current,fresh)});setSlotKey("");throw new Error("That pickup is no longer available. Choose another time or date.");}
   const menu=categories.flatMap(category=>category.products);
   const latest=items.map(line=>{const product=menu.find(value=>value.id===line.product.id&&value.saleMode===line.product.saleMode);if(!product)throw new Error(`${line.product.name} is no longer on this menu. Close and reopen Reorder.`);return {...line,product};});
   if(!validReorderItems(latest))throw new Error("The available portion sizes changed. Close and reopen Reorder.");
   if(latest.some((line,index)=>line.product.price!==items[index].product.price)||matching.slot.priorityCharge!==selected.slot.priorityCharge){setItems(latest);throw new Error("Prices changed. Review the current prices and press Continue again.");}
   if(unfinished()||getCartSnapshot()!==snapshot.cart||getStoredBranchSnapshot()!==snapshot.branch||getPickupSlotSnapshot()!==snapshot.pickup)throw new Error("Your checkout changed. Close and reopen Reorder.");
   // No cart or pickup writes happen before all checks and explicit replacement consent.
   const keys=["gokul-cart","gokul-selected-branch","gokul-selected-pickup-slot"],values=keys.map(key=>localStorage.getItem(key));
   try{saveCart({branchId:branch.id,items:latest});saveBranch(currentBranch);savePickupSlot(matching);}
   catch{keys.forEach((key,index)=>{try{if(values[index]===null)localStorage.removeItem(key);else localStorage.setItem(key,values[index]!);}catch{/* Storage failure is reported below. */}});window.dispatchEvent(new Event("storage"));throw new Error("Your selections could not be saved. Please try again.");}
   router.push(compact&&features.simplifiedCheckout&&features.acceptedCheckoutQuote?"/checkout/mobile":"/checkout/pickup");navigated=true;
  }catch(reason){if(!lifetime.current.signal.aborted)setError(reason instanceof Error?reason.message:"We could not check this pickup. Please try again.");}
  finally{continuing.current=navigated;if(!navigated&&!lifetime.current.signal.aborted)setBusy(false);}
 }
 return <dialog ref={dialog} className="reorder-dialog" aria-labelledby="reorder-title" onCancel={event=>{event.preventDefault();close();}} onClick={event=>{if(event.target===event.currentTarget){const box=event.currentTarget.getBoundingClientRect();if(event.clientX<box.left||event.clientX>box.right||event.clientY<box.top||event.clientY>box.bottom)close();}}}>
  <header><div><p><T text="Your favourites, again"/></p><h2 id="reorder-title"><T text="Reorder for pickup"/></h2></div><button type="button" aria-label="Close reorder" onClick={close} disabled={busy}>×</button></header>
  <div className="reorder-body">
   {loading?<p role="status"><T text="Loading your order…"/></p>:<>
    <p className="reorder-note">{branch?.name}<br/><T text="Current menu prices apply. The final price and offers are confirmed at checkout."/></p>
    {missing.length>0&&<div role="alert"><p>Not on the current menu: {missing.join(", ")}</p><button type="button" onClick={()=>setMissing([])}>Continue without these items</button></div>}
    <div className="reorder-items">{items.map((item,index)=><article key={item.product.id}><div><strong>{item.product.name}</strong><small>{item.product.saleMode==="WEIGHT"?formatWeight(item.weightGrams??0):`${item.quantity} pieces`} · {formatOrderCurrency(item.product.saleMode==="WEIGHT"?item.product.price*(item.weightGrams??0)/1000:item.product.price*item.quantity)}</small></div>
     <label><T text={item.product.saleMode==="WEIGHT"?"Weight (g)":"Quantity"}/><input type="number" aria-label={`${item.product.name} ${item.product.saleMode==="WEIGHT"?"weight in grams":"quantity"}`} min={item.product.saleMode==="WEIGHT"?Math.max(250,item.product.minimumWeightGrams??250):1} step={item.product.saleMode==="WEIGHT"?item.product.weightStepGrams??50:1} value={Number.isFinite(item.product.saleMode==="WEIGHT"?item.weightGrams:item.quantity)?(item.product.saleMode==="WEIGHT"?item.weightGrams??"":item.quantity):""} disabled={busy} onChange={event=>changeQuantity(index,event.target.valueAsNumber)}/></label>
     <button type="button" disabled={busy} onClick={()=>setItems(lines=>lines.filter((_,i)=>i!==index))} aria-label={`Remove ${item.product.name}`}><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7"/></svg></button></article>)}</div>
    {!validReorderItems(items)&&<p role="alert">Choose at least one item with valid quantities and portion sizes.</p>}
    {validReorderItems(items)&&!missing.length&&<><p role="status" className="reorder-note">{!current||checking?"Finding available pickup dates and times…":"Choose your pickup date and time."}</p>
     {current&&<><label className="reorder-date"><T text="Pickup date"/>{!features.smartAvailability?<input type="date" aria-label="Pickup date" min={features.today} max={maximumDate.toISOString().slice(0,10)} value={date||features.today} disabled={busy} onChange={event=>{if(event.target.value){initialChoice.current=true;setDate(event.target.value);setSlotKey("");}}}/>:<select value={date} disabled={busy} onChange={event=>{setDate(event.target.value);const option=options.find(value=>value.date===event.target.value);setSlotKey(option?`${option.slot.id}:${option.pickupType}`:"");}}>{current.dates.map(day=><option key={day.date} value={day.date}>{formatOrderDate(day.date)}{day.available?"":" · Unavailable"}</option>)}</select>}</label>
      <fieldset><legend><T text="Pickup time"/> · IST</legend><div className="reorder-slots">{options.filter(value=>value.date===date).map(value=><button type="button" key={`${value.slot.id}:${value.pickupType}`} aria-pressed={slotKey===`${value.slot.id}:${value.pickupType}`} disabled={busy} onClick={()=>setSlotKey(`${value.slot.id}:${value.pickupType}`)}>{formatOrderTime(value.slot.startTime)}–{formatOrderTime(value.slot.endTime)}{value.pickupType==="PRIORITY"&&<small>Priority +{formatOrderCurrency(value.slot.priorityCharge)}</small>}</button>)}</div></fieldset>
      {!options.some(value=>value.date===date)&&<p role="alert">{current.dates.find(day=>day.date===date)?.reason??"No pickup time can fulfil these quantities. Try another date or reduce quantities."}</p>}</>}
    </>}
    {existing&&<label className="reorder-replace"><input type="checkbox" checked={replace} disabled={busy} onChange={event=>setReplace(event.target.checked)}/>Replace my current cart with these items from {branch?.name}.</label>}
   </>}
   {error&&<div role="alert" className="reorder-error">{error}<button type="button" disabled={busy} onClick={()=>{setError("");if(!branch){setLoading(true);setLoadRevision(value=>value+1);}else setRevision(value=>value+1);}}><T text="Try again"/></button></div>}
  </div>
  <footer><button type="button" onClick={close} disabled={busy}><T text="Cancel"/></button><button type="button" onClick={()=>void proceed()} disabled={loading||busy||checking||!current||!selected||!validReorderItems(items)||missing.length>0||existing&&!replace}><T text={busy?"Checking pickup…":"Continue to checkout"}/></button></footer>
 </dialog>;
}
