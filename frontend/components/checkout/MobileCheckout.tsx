"use client";

import Link from "next/link";
import {useCallback,useEffect,useMemo,useRef,useState,useSyncExternalStore} from "react";
import {useRouter} from "next/navigation";
import {useCart} from "@/hooks/useCart";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import CustomerIdentityPanel,{type CustomerSession} from "@/components/customer/CustomerIdentityPanel";
import PickupAddOns from "./PickupAddOns";
import {verifiedCheckoutContact} from "@/lib/checkoutIdentity";
import {availabilityItems,checkCartAvailability,type CartAvailability} from "@/services/availabilityApi";
import {getCartSnapshot,parseCart} from "@/lib/cartStorage";
import {getStoredBranchSnapshot} from "@/lib/branchStorage";
import {getPickupSlotSnapshot,parsePickupSlot,savePickupSlot,saveCustomerDetails} from "@/lib/checkoutStorage";
import {getPendingOrderSnapshot,getServerPendingOrderSnapshot,subscribeToPendingOrder,parsePendingOrder,savePendingOrder} from "@/lib/pendingOrderStorage";
import {savePendingPayment} from "@/lib/pendingPaymentStorage";
import {createCartFingerprint} from "@/lib/cartFingerprint";
import {markPaymentGatewayOpened} from "@/lib/paymentGatewayVisit";
import {openPaymentCheckout} from "@/lib/paymentCheckout";
import {createOrder} from "@/services/orderApi";
import {createPayment} from "@/services/paymentApi";
import {applyBestRebate} from "@/services/rebateApi";
import {apiClient,ApiError} from "@/services/apiClient";
import {formatWeight} from "@/lib/orderQuantity";
import {T} from "@/lib/language";
import type {CheckoutQuote,CreateOrderRequest} from "@/types/order";
import type {AvailableRebateResponse} from "@/types/rebate";
import type {PickupSelection} from "@/types/pickup";

const quoteExpired=(expiresAt:string)=>Date.parse(expiresAt)<=Date.now();
const ATTEMPT="gokul-mobile-order-attempt";
const money=(n:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(n);
type Attempt={request:CreateOrderRequest;key:string;cart:string;branch:string;pickup:string;expected:number};
type Preview={quote:CheckoutQuote;offers:AvailableRebateResponse[];spendTargets?:AvailableRebateResponse[]};

function pickupOptions(value:CartAvailability):PickupSelection[]{
 return value.dates.flatMap(d=>d.slots.filter(s=>s.slot.active&&Date.parse(`${d.date}T${s.slot.startTime}+05:30`)>Date.now()).flatMap(s=>[...(s.normalAvailable?[{date:d.date,slot:s.slot,pickupType:"NORMAL" as const}]:[]),...(s.priorityAvailable&&s.slot.priorityEnabled?[{date:d.date,slot:s.slot,pickupType:"PRIORITY" as const}]:[])]));
}
const pickupLabel=(s:PickupSelection)=>`${s.date} · ${s.slot.startTime.slice(0,5)}–${s.slot.endTime.slice(0,5)} · ${s.pickupType==="PRIORITY"?`Priority (+${money(s.slot.priorityCharge)})`:"Standard"}`;

export default function MobileCheckout(){
 const router=useRouter(),cart=useCart(),{branch}=useSelectedBranch(),features=useStorefrontFeatures();
 const pendingValue=useSyncExternalStore(subscribeToPendingOrder,getPendingOrderSnapshot,getServerPendingOrderSnapshot);
 const pending=parsePendingOrder(pendingValue);
 const [session,setSession]=useState<CustomerSession>({authenticated:false});
 const [identityRevision,setIdentityRevision]=useState(0);
 const contact=useMemo(()=>verifiedCheckoutContact(session),[session]);
 const onSession=useCallback((value:CustomerSession)=>{setSession(value);const c=verifiedCheckoutContact(value);if(c)saveCustomerDetails(c);},[]);
 const itemsKey=JSON.stringify(availabilityItems(cart.items));
 const today=new Intl.DateTimeFormat("en-CA",{timeZone:"Asia/Kolkata",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date());
 const startDate=features?.today&&features.today>today?features.today:today;
 const availabilityKey=`${branch?.id}:${itemsKey}:${startDate}:${features?.futureOrderingDays}`;
 const [availability,setAvailability]=useState<{key:string;value:CartAvailability}|null>(null);
 const [availabilityError,setAvailabilityError]=useState<{key:string;message:string}|null>(null);
 const [revision,setRevision]=useState(0);
 const [chosen,setChosen]=useState<PickupSelection|null>(null);
 const [price,setPrice]=useState<{key:string;value:Preview}|null>(null);
 const [priceError,setPriceError]=useState<{key:string;message:string}|null>(null);
 const [error,setError]=useState("");const [busy,setBusy]=useState(false),[addOnBusy,setAddOnBusy]=useState(false);
 const locked=useRef(false);
 const [attempt,setAttempt]=useState<Attempt|null>(null);
 const [attemptLoaded,setAttemptLoaded]=useState(false);
 useEffect(()=>{let alive=true;queueMicrotask(()=>{if(!alive)return;try{const value=JSON.parse(sessionStorage.getItem(ATTEMPT)??"null") as Attempt|null;if(value?.key&&value.request&&typeof value.expected==="number")setAttempt(value);}catch{/* No saved attempt. */}setAttemptLoaded(true);});return()=>{alive=false;};},[]);
 const [recovery,setRecovery]=useState<string|null>(null);
 useEffect(()=>{let alive=true;queueMicrotask(()=>{if(alive)setRecovery(new URLSearchParams(window.location.search).get("paymentRecovery"));});return()=>{alive=false;};},[]);
 const frozen=!attemptLoaded||busy||!!attempt||!!pending;
 useEffect(()=>{
  if(!attemptLoaded||!branch||cart.branchId!==branch.id||!cart.items.length||pending||attempt)return;
  const controller=new AbortController();
  void checkCartAvailability(branch.id,startDate,(features?.futureOrderingDays??30)+1,JSON.parse(itemsKey),AbortSignal.any([controller.signal,AbortSignal.timeout(15000)]))
   .then(value=>{if(controller.signal.aborted)return;setAvailability({key:availabilityKey,value});
    const options=pickupOptions(value);
    const stored=parsePickupSlot(getPickupSlotSnapshot());const preferred=options.find(s=>s.slot.id===stored?.slot.id&&s.pickupType===stored.pickupType)??options[0]??null;
    setChosen(preferred);if(preferred)savePickupSlot(preferred);
   }).catch(()=>{if(!controller.signal.aborted)setAvailabilityError({key:availabilityKey,message:"We couldn’t check pickup availability. Your cart is saved. Try again."});});
  return()=>controller.abort();
 },[branch,cart.branchId,cart.items.length,itemsKey,startDate,features?.futureOrderingDays,availabilityKey,revision,pendingValue,attempt,pending,attemptLoaded]);
 const validAvailability=availability?.key===availabilityKey?availability.value:null;
 const request=useMemo<CreateOrderRequest|null>(()=>branch&&contact&&chosen&&validAvailability?{
  branchId:branch.id,pickupSlotId:chosen.slot.id,pickupType:chosen.pickupType,customerName:contact.name,customerPhone:contact.phone,items:JSON.parse(itemsKey)
 }:null,[branch,contact,chosen,validAvailability,itemsKey]);
 const priceKey=JSON.stringify(request);
 useEffect(()=>{if(!request||pending||attempt)return;const controller=new AbortController();
  void apiClient<Preview>("/api/orders/mobile-preview",{method:"POST",body:priceKey,credentials:"include",signal:AbortSignal.any([controller.signal,AbortSignal.timeout(15000)])})
   .then(value=>{if(!controller.signal.aborted)setPrice({key:priceKey,value});}).catch(failure=>{if(!controller.signal.aborted){if(failure instanceof ApiError&&failure.status===401){setSession({authenticated:false});setIdentityRevision(v=>v+1);}setPriceError({key:priceKey,message:"We couldn’t confirm your price or offers. Review pickup and verification, then try again."});}});
  return()=>controller.abort();
 },[priceKey,request,pendingValue,pending,attempt,revision]);
 const preview=price?.key===priceKey?price.value:null;
 const best=preview?.offers.reduce<AvailableRebateResponse|null>((best,offer)=>!best||offer.payableAfterRebate<best.payableAfterRebate?offer:best,null);
 const total=best?best.payableAfterRebate:preview?Number(preview.quote.totalAmount):null;
 useEffect(()=>{if(!preview||attempt||pending)return;const timeout=setTimeout(()=>setRevision(v=>v+1),Math.max(100,Date.parse(preview.quote.expiresAt)-Date.now()));return()=>clearTimeout(timeout);},[preview,attempt,pendingValue,pending]);
 async function pay(){
  if(locked.current||addOnBusy||pending)return;
  const saved=attempt;
  if(!saved&&(!request||!preview||total===null||quoteExpired(preview.quote.expiresAt))){setRevision(v=>v+1);setError("Checking your current price before payment. Please review the refreshed total.");return;}
  locked.current=true;setBusy(true);setError("");
  let orderCreated=false;let postingOrder=false;
  try{
   const current=verifiedCheckoutContact(await apiClient<CustomerSession>("/api/customer/identity/me",{credentials:"include",signal:AbortSignal.timeout(5000)}));
   const expectedPhone=saved?.request.customerPhone??request!.customerPhone;
   if(!current||current.phone!==expectedPhone){setSession({authenticated:false});setIdentityRevision(v=>v+1);throw new Error("Verify the order’s phone number before payment. Your cart is saved.");}
   const value=saved??{request:{...request!,quoteToken:preview!.quote.token},key:crypto.randomUUID(),cart:getCartSnapshot(),branch:getStoredBranchSnapshot(),pickup:getPickupSlotSnapshot(),expected:total!};
   if(!saved&&(value.cart!==getCartSnapshot()||value.branch!==getStoredBranchSnapshot()||value.pickup!==getPickupSlotSnapshot()))throw new Error("Your cart, branch or pickup changed elsewhere. Review the saved checkout before payment.");
   sessionStorage.setItem(ATTEMPT,JSON.stringify(value));setAttempt(value);
   postingOrder=true;const order=await createOrder(value.request,value.key,AbortSignal.timeout(20000));orderCreated=true;
   const fingerprint=createCartFingerprint(parseCart(value.cart).items);
   savePendingOrder({orderId:order.id,orderNumber:order.orderNumber,orderStatus:order.orderStatus,branchId:order.branchId,pickupSlotId:order.pickupSlotId,totalAmount:order.totalAmount,reservationExpiresAt:order.reservationExpiresAt,createdAt:order.createdAt,cartFingerprint:fingerprint});
   sessionStorage.removeItem(ATTEMPT);setAttempt(null);
   localStorage.setItem(`gokul-mobile-checkout:${order.orderNumber}`,"1");
   if(order.orderStatus!=="PENDING_PAYMENT") {router.replace(`/orders/${encodeURIComponent(order.orderNumber)}`);return;}
   if(value.cart!==getCartSnapshot()||value.branch!==getStoredBranchSnapshot()||value.pickup!==getPickupSlotSnapshot())throw new Error("Your checkout changed elsewhere. Review the existing order before payment.");
   const applied=await applyBestRebate(order.orderNumber);
   savePendingOrder({orderId:order.id,orderNumber:order.orderNumber,orderStatus:order.orderStatus,branchId:order.branchId,pickupSlotId:order.pickupSlotId,totalAmount:applied.totalAmount,reservationExpiresAt:order.reservationExpiresAt,createdAt:order.createdAt,cartFingerprint:fingerprint});
   if(value.cart!==getCartSnapshot()||value.branch!==getStoredBranchSnapshot()||value.pickup!==getPickupSlotSnapshot())throw new Error("Your checkout changed elsewhere. Review the existing order before payment.");
   if(Math.abs(applied.totalAmount-value.expected)>0.009)throw new Error("Your total changed after the final offer check. Review the existing order’s updated total before paying.");
   const payment=await createPayment({orderNumber:order.orderNumber},AbortSignal.timeout(20000));
   savePendingPayment({...payment,cartFingerprint:fingerprint});
   if(value.cart!==getCartSnapshot()||value.branch!==getStoredBranchSnapshot()||value.pickup!==getPickupSlotSnapshot()||Math.abs(payment.amount-value.expected)>0.009)throw new Error("Review your existing payment’s total and saved items before opening the gateway.");
   if(payment.paymentStatus!=="PENDING"){router.replace(`/checkout/payment/${encodeURIComponent(order.orderNumber)}`);return;}
   markPaymentGatewayOpened(payment.orderNumber,payment.paymentId);
   const outcome=await openPaymentCheckout(payment);
   if(payment.provider==="PHONEPE"&&payment.paymentUrl)return;
   if(outcome.kind==="updated")savePendingPayment({...outcome.payment,cartFingerprint:fingerprint});
   router.replace(`/checkout/payment/${encodeURIComponent(order.orderNumber)}`);
  }catch(failure){setError(failure instanceof Error?failure.message:"Payment could not be started. Your cart is saved.");
   if(failure instanceof ApiError&&failure.status===401){setSession({authenticated:false});setIdentityRevision(v=>v+1);}
   if(postingOrder&&!orderCreated&&failure instanceof ApiError&&(failure.status===400||failure.status===409)){sessionStorage.removeItem(ATTEMPT);setAttempt(null);setRevision(v=>v+1);}
   // An uncertain POST must be retried with its original request and key.
   if(!orderCreated&&sessionStorage.getItem(ATTEMPT))setError("We couldn’t confirm whether your order was created. Retry this exact checkout to safely recover it. Your cart is saved.");
  }finally{locked.current=false;setBusy(false);}
 }
 if(attemptLoaded&&cart.isEmpty&&!pending&&!attempt)return <div className="mobile-checkout"><h1><T text="Your cart is empty" /></h1><Link href="/menu"><T text="Browse menu" /></Link></div>;
 if(attemptLoaded&&(!branch||cart.branchId!==branch.id)&&!attempt&&!pending)return <div className="mobile-checkout"><h1><T text="Your order" /></h1><p><T text="Choose the matching branch before checking out." /></p><Link href="/menu"><T text="Back to menu" /></Link></div>;
 return <div className="mobile-checkout">
  <div className="mobile-checkout-heading"><div><p>{branch?.name??"Saved checkout"}</p><h1><T text="Your order" /></h1></div><Link href="/menu"><T text="Add more" /></Link></div>
  {recovery&&<p role="status"><T text="Payment wasn’t completed. Your cart is saved; review it and try again." /></p>}
  <section aria-label="Cart items" className="mobile-checkout-section">{cart.items.map(item=><article className="mobile-cart-row" key={item.product.id}><div><strong>{item.product.name}</strong><p>{item.product.saleMode==="WEIGHT"?formatWeight(item.weightGrams??0):money(item.product.price)}</p></div><div className="mobile-quantity"><button type="button" disabled={frozen} aria-label={`Remove one ${item.product.name}`} onClick={()=>cart.decreaseQuantity(item.product.id)}>−</button><span>{item.product.saleMode==="WEIGHT"?formatWeight(item.weightGrams??0):item.quantity}</span><button type="button" disabled={frozen} aria-label={`Add one ${item.product.name}`} onClick={()=>cart.increaseQuantity(item.product.id)}>+</button></div></article>)}</section>
  {pending?<section className="mobile-checkout-section"><h2><T text="Continue your existing order" /></h2><p><T text="Your order is already reserved. Check its payment status before starting another checkout." /></p><Link href={`/checkout/payment/${encodeURIComponent(pending.orderNumber)}`}><T text="Continue payment" /></Link></section>:<>
  <section className="mobile-checkout-section"><h2><T text="Pickup" /></h2>{validAvailability&&chosen?<><p><strong>{pickupLabel(chosen)}</strong></p><details><summary><T text="Change pickup" /></summary><div className="mobile-pickup-options">{pickupOptions(validAvailability).map(next=><label key={`${next.slot.id}:${next.pickupType}`}><input type="radio" name="mobile-pickup" disabled={frozen} checked={chosen.slot.id===next.slot.id&&chosen.pickupType===next.pickupType} onChange={()=>{setChosen(next);savePickupSlot(next);}}/>{pickupLabel(next)}</label>)}</div></details></>:<p role="status">{availabilityError?.key===availabilityKey?availabilityError.message:validAvailability?"No pickup slots fit this cart. Change quantities or try another date from the pickup selector.":attempt?`${attempt.request.pickupType} pickup saved for retry. Your original pickup will be recovered with the order.`:"Checking the earliest available pickup…"}</p>}{validAvailability&&!chosen&&<Link href="/checkout/pickup"><T text="Choose pickup" /></Link>}{availabilityError?.key===availabilityKey&&<button onClick={()=>setRevision(v=>v+1)}><T text="Try again" /></button>}</section>
  <CustomerIdentityPanel mode="mobileCheckout" sessionRevision={identityRevision} onSessionChange={onSession}/>
  <section className="mobile-checkout-section"><h2><T text="Offers & total" /></h2>{preview?<><p>{best?<><strong>{best.name}</strong> · <T text="Best available offer applied automatically" /> · −{money(best.rebateAmount)}</>:<T text="Your current menu price" />}</p><details><summary><T text="Price details" /></summary><dl><div><dt><T text="Items" /></dt><dd>{money(Number(preview.quote.subtotal))}</dd></div><div><dt><T text="Tax" /></dt><dd>{money(Number(preview.quote.taxAmount))}</dd></div><div><dt><T text="Priority pickup" /></dt><dd>{money(Number(preview.quote.priorityCharge??0))}</dd></div><div><dt><T text="Convenience fee" /></dt><dd>{money(Number(preview.quote.convenienceFee??0))}</dd></div><div><dt><T text="Online payment fee" /></dt><dd>{preview.quote.paymentFeeRate??0}%</dd></div>{best&&<div><dt>{best.name}</dt><dd>−{money(best.rebateAmount)}</dd></div>}<div><dt><T text="Total" /></dt><dd>{money(total!)}</dd></div></dl></details></>:<p role="status">{priceError?.key===priceKey?priceError.message:contact?"Checking prices and your best offer…":"Verify your phone here to see your final total and eligible offers."}</p>}{priceError?.key===priceKey&&<button onClick={()=>setRevision(v=>v+1)}><T text="Try again" /></button>}</section>
  {features?.pickupAddOns&&branch&&chosen&&validAvailability&&!attempt&&<PickupAddOns branchId={branch.id} date={chosen.date} disabled={busy||addOnBusy} offers={[...(preview?.offers??[]),...(preview?.spendTargets??[])]} onAdded={()=>{}} onBusy={setAddOnBusy}/>}
  </>}
  {error&&<p role="alert" className="mobile-checkout-error">{error}</p>}
  {attempt&&<p role="status"><T text="This checkout is saved for a safe retry. Its items and pickup are locked until the order is recovered." /></p>}
  {!pending&&<div className="mobile-checkout-pay"><div><small><T text={total===null?"Items subtotal":"Total to pay"}/></small><strong>{money(attempt?.expected??total??cart.subtotal)}</strong></div><button type="button" disabled={!attemptLoaded||busy||addOnBusy||!contact||(!attempt&&(!preview||!contact||!chosen||!validAvailability))} onClick={()=>void pay()}>{busy?<T text="Opening payment…"/>:attempt?<T text="Retry checkout"/>:<T text="Pay now"/>}</button></div>}
 </div>;
}
