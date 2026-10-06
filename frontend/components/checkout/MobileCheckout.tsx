"use client";
import EmptyMealIllustration from "@/components/cart/EmptyMealIllustration";

import Link from "next/link";
import OfferArrival from "./OfferArrival";
import {CheckoutSavingsUncertainError} from "@/lib/checkoutRefresh";
import dynamic from "next/dynamic";
import MobilePageBack from "@/components/customer/MobilePageBack";
import {useCallback,useEffect,useMemo,useRef,useState,useSyncExternalStore} from "react";
import {useRouter} from "next/navigation";
import {useCart} from "@/hooks/useCart";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {usePickupIntent,savePickupIntent} from "@/hooks/usePickupIntent";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import CustomerIdentityPanel,{type PhoneVerificationAction,type CustomerSession} from "@/components/customer/CustomerIdentityPanel";
import PickupAddOns from "./PickupAddOns";
import {verifiedCheckoutContact} from "@/lib/checkoutIdentity";
import {availabilityItems,checkCartAvailability,type CartAvailability} from "@/services/availabilityApi";
import {getCartSnapshot,parseCart,saveCart,clearStoredCart} from "@/lib/cartStorage";
import {getStoredBranchSnapshot} from "@/lib/branchStorage";
import {getPickupSlotSnapshot,getServerPickupSlotSnapshot,subscribeToPickupSlot,parsePickupSlot,savePickupSlot,saveCustomerDetails} from "@/lib/checkoutStorage";
import {getPendingOrderSnapshot,getServerPendingOrderSnapshot,subscribeToPendingOrder,parsePendingOrder,savePendingOrder} from "@/lib/pendingOrderStorage";
import {savePendingPayment} from "@/lib/pendingPaymentStorage";
import {createCartFingerprint} from "@/lib/cartFingerprint";
import {markPaymentGatewayOpened} from "@/lib/paymentGatewayVisit";
import {openPaymentCheckout} from "@/lib/paymentCheckout";
import {createOrder} from "@/services/orderApi";
import {createPayment} from "@/services/paymentApi";
import RewardPicker from "./RewardPicker";
import type {RewardWallet} from "@/services/loyaltyApi";
import {applyRebate,applyBestRebate} from "@/services/rebateApi";
import {apiClient,ApiError} from "@/services/apiClient";
import {formatWeight} from "@/lib/orderQuantity";
import {T,translate,useLanguage} from "@/lib/language";
import {indiaToday,pickupIsFresh} from "@/lib/pickupFreshness";
import {usePickupClock} from "@/hooks/usePickupClock";
import type {CheckoutQuote,CreateOrderRequest} from "@/types/order";
import type {AvailableRebateResponse} from "@/types/rebate";
import type {PickupSelection} from "@/types/pickup";

const quoteExpired=(expiresAt:string)=>Date.parse(expiresAt)<=Date.now();
const ATTEMPT="gokul-mobile-order-attempt";
const MobileBillSummary=dynamic(()=>import("./MobileBillSummary"));
const MobilePickupDialog=dynamic(()=>import("./MobilePickupDialog"));
const OfferChoiceDialog=dynamic(()=>import("./OfferChoiceDialog"));
const money=(n:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(n);
type Attempt={offerCode?:string|null;request:CreateOrderRequest;key:string;cart:string;branch:string;pickup:string;expected:number};
type Preview={quote:CheckoutQuote;offers:AvailableRebateResponse[];spendTargets?:AvailableRebateResponse[];paymentFee?:number;paymentFeeTax?:number;rewards?:RewardWallet|null;rewardDiscount?:number;totalBeforeOffer?:number;selectedOffer?:AvailableRebateResponse|null};

function pickupOptions(value:CartAvailability):PickupSelection[]{
 return value.dates.flatMap(d=>d.slots.filter(s=>s.slot.active&&Date.parse(`${d.date}T${s.slot.startTime}+05:30`)>Date.now()).flatMap(s=>[...(s.normalAvailable?[{date:d.date,slot:s.slot,pickupType:"NORMAL" as const}]:[]),...(s.priorityAvailable&&s.slot.priorityEnabled?[{date:d.date,slot:s.slot,pickupType:"PRIORITY" as const}]:[])]));
}
const pickupDateLabel=(date:string,locale:"en"|"hi",today:string)=>{const named=new Intl.DateTimeFormat(locale==="hi"?"hi-IN":"en-IN",{weekday:"short",day:"numeric",month:"short",year:"numeric",timeZone:"Asia/Kolkata"}).format(new Date(`${date}T12:00:00+05:30`));const tomorrow=indiaToday(new Date(Date.parse(`${today}T12:00:00+05:30`)+86400000));return `${date===today?`${translate("Today",locale)} · `:date===tomorrow?`${translate("Tomorrow",locale)} · `:""}${named}`;};
const pickupLabel=(s:PickupSelection,locale:"en"|"hi",today:string)=>`${pickupDateLabel(s.date,locale,today)} · ${s.slot.startTime.slice(0,5)}–${s.slot.endTime.slice(0,5)} · ${s.pickupType==="PRIORITY"?`${translate("Priority",locale)} (+${money(s.slot.priorityCharge)})`:translate("Standard",locale)}`;

export default function MobileCheckout(){
 const locale=useLanguage();
 const router=useRouter(),cart=useCart(),{branch}=useSelectedBranch(),features=useStorefrontFeatures();
 const pendingValue=useSyncExternalStore(subscribeToPendingOrder,getPendingOrderSnapshot,getServerPendingOrderSnapshot);
 const pending=parsePendingOrder(pendingValue);
 const [session,setSession]=useState<CustomerSession>({authenticated:false});
 const [identityResolved,setIdentityResolved]=useState(false);
 const verificationAction=useRef<PhoneVerificationAction>(null);
 const [verificationBusy,setVerificationBusy]=useState(false);
 const [billOpen,setBillOpen]=useState(false);
 const [rewardCode,setRewardCode]=useState<string|null>(null),[offerCode,setOfferCode]=useState<string|null>(null),[offerPopup,setOfferPopup]=useState(false),[savingsBusy,setSavingsBusy]=useState(false);
 const [identityRevision,setIdentityRevision]=useState(0);
 const contact=useMemo(()=>verifiedCheckoutContact(session),[session]);
 const onSession=useCallback((value:CustomerSession)=>{setSession(value);setIdentityResolved(true);const c=verifiedCheckoutContact(value);if(c)saveCustomerDetails(c);},[]);
 const itemsKey=JSON.stringify(availabilityItems(cart.items));
 const today=new Intl.DateTimeFormat("en-CA",{timeZone:"Asia/Kolkata",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date());
 const startDate=features?.today&&features.today>today?features.today:today;
 const availabilityKey=`${branch?.id}:${itemsKey}:${startDate}:${features?.futureOrderingDays}`;
 const [availability,setAvailability]=useState<{key:string;value:CartAvailability}|null>(null);
 const [availabilityError,setAvailabilityError]=useState<{key:string;message:string}|null>(null);
 const [revision,setRevision]=useState(0);
 const [pickupValidating,setPickupValidating]=useState(false);
 const pickupCheck=useRef<AbortController|null>(null);
 const mounted=useRef(true);
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;pickupCheck.current?.abort();};},[]);
 useEffect(()=>()=>{pickupCheck.current?.abort();},[availabilityKey]);
 const [pickupPopup,setPickupPopup]=useState<string|null>(null);
 const pickupValue=useSyncExternalStore(subscribeToPickupSlot,getPickupSlotSnapshot,getServerPickupSlotSnapshot);
 const [confirmedPickup,setConfirmedPickup]=useState("");
 const storedPickup=useMemo(()=>parsePickupSlot(pickupValue),[pickupValue]);
 const pickupIntent=usePickupIntent(branch?.id);
 const pickupNow=usePickupClock();
 const pickupExpired=!!storedPickup&&!!pickupNow&&!pickupIsFresh(storedPickup,new Date(pickupNow));
 const [price,setPrice]=useState<{key:string;value:Preview}|null>(null);
 const [priceError,setPriceError]=useState<{key:string;message:string}|null>(null);
 const [error,setError]=useState("");const [busy,setBusy]=useState(false),[handoff,setHandoff]=useState(false),[addOnBusy,setAddOnBusy]=useState(false);
 const [confirmedBranch,setConfirmedBranch]=useState<string|null>(null);
 const identitySection=useRef<HTMLDivElement>(null),branchSection=useRef<HTMLElement>(null);
 const [guidance,setGuidance]=useState<"identity"|"branch"|null>(null);

 const branchReviewKey=JSON.stringify([branch?.id,branch?.name,branch?.address,branch?.city,branch?.pincode]);
 const branchReviewed=confirmedBranch===branchReviewKey;
 const locked=useRef(false);
 const [attempt,setAttempt]=useState<Attempt|null>(null);
 const [attemptLoaded,setAttemptLoaded]=useState(false);
 useEffect(()=>{let alive=true;queueMicrotask(()=>{if(!alive)return;try{const value=JSON.parse(sessionStorage.getItem(ATTEMPT)??"null") as Attempt|null;if(value?.key&&value.request&&typeof value.expected==="number")setAttempt(value);}catch{/* No saved attempt. */}setAttemptLoaded(true);});return()=>{alive=false;};},[]);
 useEffect(()=>{
  const resume=()=>{if(document.visibilityState!=="visible"||!handoff)return;setHandoff(false);window.scrollTo({left:0,top:0,behavior:"instant"});};
  window.addEventListener("pageshow",resume);document.addEventListener("visibilitychange",resume);
  return()=>{window.removeEventListener("pageshow",resume);document.removeEventListener("visibilitychange",resume);};
 },[handoff]);
 useEffect(()=>{if(attemptLoaded&&pending&&!busy&&!handoff&&localStorage.getItem(`gokul-payment-gateway-opened:${pending.orderNumber}`))router.replace(`/checkout/payment/${encodeURIComponent(pending.orderNumber)}`);},[attemptLoaded,pendingValue,pending,busy,handoff,router]);
 const [recovery,setRecovery]=useState<string|null>(null);
 useEffect(()=>{let alive=true;queueMicrotask(()=>{if(alive)setRecovery(new URLSearchParams(window.location.search).get("paymentRecovery"));});return()=>{alive=false;};},[]);
 const frozen=!attemptLoaded||busy||!!attempt||!!pending;
 const [selecting,setSelecting]=useState(false);
 const [selection,setSelection]=useState<{branchId:number|null;ids:number[]}>({branchId:null,ids:[]});
 const selected=selection.branchId===cart.branchId?selection.ids.filter(id=>cart.items.some(item=>item.product.id===id)):[];
 const editingLocked=frozen||addOnBusy||pickupValidating||savingsBusy;
 const hadItems=useRef(false);
 useEffect(()=>{
  if(!attemptLoaded||busy||addOnBusy||attempt||pending)return;
  if(cart.items.length){hadItems.current=true;return;}
  if(hadItems.current)router.replace("/menu");
 },[attemptLoaded,busy,addOnBusy,attempt,pendingValue,pending,cart.items.length,router]);
 function removeItems(ids:number[]){
  if(editingLocked)return;
  const current=parseCart(getCartSnapshot());
  if(current.branchId!==cart.branchId)return;
  const remove=new Set(ids),items=current.items.filter(item=>!remove.has(item.product.id));
  if(items.length===current.items.length)return;
  if(items.length)saveCart({branchId:current.branchId,items});else clearStoredCart();
  setSelection({branchId:cart.branchId,ids:selected.filter(id=>!remove.has(id))});
 }

 async function confirmPickup(selection:PickupSelection):Promise<boolean>{
  if(editingLocked||pickupCheck.current||!branch)return false;
  const savedCart=getCartSnapshot(),savedBranch=getStoredBranchSnapshot();
  const controller=new AbortController();pickupCheck.current=controller;setPickupValidating(true);
  try{
   const fresh=await checkCartAvailability(branch.id,selection.date,1,availabilityItems(parseCart(savedCart).items),AbortSignal.any([controller.signal,AbortSignal.timeout(15000)]));
   if(!mounted.current||controller.signal.aborted||getCartSnapshot()!==savedCart||getStoredBranchSnapshot()!==savedBranch||getPendingOrderSnapshot()||locked.current||sessionStorage.getItem(ATTEMPT))return false;
   if(validAvailability)setAvailability({key:availabilityKey,value:{...validAvailability,dates:validAvailability.dates.map(day=>fresh.dates.find(updated=>updated.date===day.date)??day)}});
   const available=pickupOptions(fresh).find(option=>option.date===selection.date&&option.slot.id===selection.slot.id&&option.pickupType===selection.pickupType);
   if(!available)return false;
   savePickupIntent(branch.id,available.date);savePickupSlot(available);setConfirmedPickup(getPickupSlotSnapshot());setPickupPopup(null);return true;
  }catch{throw new Error("We couldn’t confirm this pickup time. Your previous pickup is saved. Please try again.");}
  finally{if(pickupCheck.current===controller)pickupCheck.current=null;if(mounted.current)setPickupValidating(false);}
 }
 function closePickup(){pickupCheck.current?.abort();setPickupPopup(null);}

 useEffect(()=>{
  if(!attemptLoaded||!branch||cart.branchId!==branch.id||!cart.items.length||pending||attempt)return;
  const controller=new AbortController();
  void checkCartAvailability(branch.id,startDate,(features?.futureOrderingDays??30)+1,JSON.parse(itemsKey),AbortSignal.any([controller.signal,AbortSignal.timeout(15000)]))
   .then(value=>{if(controller.signal.aborted)return;setAvailability({key:availabilityKey,value});
    // Discovery never commits a pickup, including when only a menu date is saved.
   }).catch(()=>{if(!controller.signal.aborted)setAvailabilityError({key:availabilityKey,message:"We couldn’t check pickup availability. Your cart is saved. Try again."});});
  return()=>controller.abort();
 },[branch,cart.branchId,cart.items.length,itemsKey,startDate,features?.futureOrderingDays,availabilityKey,revision,pendingValue,attempt,pending,attemptLoaded]);
 const validAvailability=availability?.key===availabilityKey?availability.value:null;
 const chosen=useMemo(()=>validAvailability&&!pickupExpired?pickupOptions(validAvailability).find(option=>option.date===storedPickup?.date&&option.slot.id===storedPickup.slot.id&&option.pickupType===storedPickup.pickupType)??null:null,[validAvailability,storedPickup,pickupExpired]);
 const pickupConfirmed=!!chosen&&confirmedPickup===pickupValue;
 const pickupNeedsChoice=!!validAvailability&&!chosen;
 const pickupNeedsConfirmation=!!chosen&&!pickupConfirmed;
 const request=useMemo<CreateOrderRequest|null>(()=>branch&&contact&&chosen&&validAvailability?{
  branchId:branch.id,pickupSlotId:chosen.slot.id,pickupType:chosen.pickupType,customerName:contact.name,customerPhone:contact.phone,items:JSON.parse(itemsKey),...(rewardCode?{rewardCode}:{}),...(offerCode?{offerCode}:{})
 }:null,[branch,contact,chosen,validAvailability,itemsKey,rewardCode,offerCode]);
 const priceKey=JSON.stringify(request);
 useEffect(()=>{if(!request||pending||attempt)return;const controller=new AbortController();
  void apiClient<Preview>("/api/orders/mobile-preview",{method:"POST",body:priceKey,credentials:"include",signal:AbortSignal.any([controller.signal,AbortSignal.timeout(15000)])})
   .then(value=>{if(!controller.signal.aborted)setPrice({key:priceKey,value});}).catch(failure=>{if(!controller.signal.aborted){if(failure instanceof ApiError&&failure.status===401){setSession({authenticated:false});setIdentityRevision(v=>v+1);}setPriceError({key:priceKey,message:"We couldn’t confirm your price or offers. Review pickup and verification, then try again."});}});
  return()=>controller.abort();
 },[priceKey,request,pendingValue,pending,attempt,revision]);
 const preview=price?.key===priceKey?price.value:null;
 const best=preview?.selectedOffer??preview?.offers.reduce<AvailableRebateResponse|null>((best,offer)=>!best||offer.payableAfterRebate<best.payableAfterRebate?offer:best,null);
 const total=best?best.payableAfterRebate:preview?Number(preview.totalBeforeOffer??preview.quote.totalAmount):null;
 useEffect(()=>{if(!preview||attempt||pending)return;const timeout=setTimeout(()=>setRevision(v=>v+1),Math.max(100,Date.parse(preview.quote.expiresAt)-Date.now()));return()=>clearTimeout(timeout);},[preview,attempt,pendingValue,pending]);
 async function changeSavings(next:{rewardCode?:string|null;offerCode?:string|null}){
  if(!request||editingLocked)throw new Error("Wait for your current checkout to finish checking.");
  const cartSnapshot=getCartSnapshot(),branchSnapshot=getStoredBranchSnapshot(),pickupSnapshot=getPickupSlotSnapshot();
  setSavingsBusy(true);setError("");
  const updated={...request,...next};
  try{
   const check=()=>apiClient<Preview>("/api/orders/mobile-preview",{method:"POST",credentials:"include",body:JSON.stringify(updated),signal:AbortSignal.timeout(15000)});
   let value:Preview;
   try{value=await check();}catch(failure){
    if(!("rewardCode" in next)||!updated.offerCode||!(failure instanceof ApiError)||failure.code!=="OFFER_INELIGIBLE")throw failure;
    updated.offerCode=null;value=await check();
   }
   if(cartSnapshot!==getCartSnapshot()||branchSnapshot!==getStoredBranchSnapshot()||pickupSnapshot!==getPickupSlotSnapshot())throw new Error("Your checkout changed elsewhere. Review it before applying savings.");
   const canonical={...updated};if(!canonical.rewardCode)delete canonical.rewardCode;if(!canonical.offerCode)delete canonical.offerCode;
   setRewardCode(updated.rewardCode??null);setOfferCode(updated.offerCode??null);setPrice({key:JSON.stringify(canonical),value});
  }finally{setSavingsBusy(false);}
 }
 async function pay(){
  if(locked.current||addOnBusy||savingsBusy||pending)return;
  const saved=attempt;
  if(!saved&&(!pickupConfirmed||!chosen||!pickupIsFresh(chosen,new Date())||!validAvailability||getPickupSlotSnapshot()!==pickupValue)){setError("Your saved pickup cannot be used for this cart. Choose and confirm a pickup date and time before payment.");return;}
  const pickupSnapshot=getPickupSlotSnapshot(),cartSnapshot=getCartSnapshot(),branchSnapshot=getStoredBranchSnapshot();
  if(!saved&&!branchReviewed){setError("Confirm the pickup branch before payment. Collect only from the branch shown here.");return;}
  if(!saved&&(!request||!preview||total===null||quoteExpired(preview.quote.expiresAt))){setRevision(v=>v+1);setError("Checking your current price before payment. Please review the refreshed total.");return;}
  locked.current=true;setBusy(true);setError("");
  let orderCreated=false;let postingOrder=false;let createdOrderNumber:string|null=null;
  try{
   const current=verifiedCheckoutContact(await apiClient<CustomerSession>("/api/customer/identity/me",{credentials:"include",signal:AbortSignal.timeout(5000)}));
   const expectedPhone=saved?.request.customerPhone??request!.customerPhone;
   if(!current||current.phone!==expectedPhone){setSession({authenticated:false});setIdentityRevision(v=>v+1);throw new Error("Verify the order’s phone number before payment. Your cart is saved.");}
   if(!saved&&!pickupIsFresh(chosen!,new Date()))throw new Error("Your pickup time has passed. Choose another time. Your pickup hasn’t changed.");
   if(!saved&&(pickupSnapshot!==getPickupSlotSnapshot()||cartSnapshot!==getCartSnapshot()||branchSnapshot!==getStoredBranchSnapshot()))throw new Error("Your cart, branch or pickup changed elsewhere. Review checkout before payment.");
   const value=saved??{request:{...request!,quoteToken:preview!.quote.token},key:crypto.randomUUID(),cart:getCartSnapshot(),branch:getStoredBranchSnapshot(),pickup:getPickupSlotSnapshot(),expected:total!,offerCode};
   if(!saved&&(value.cart!==getCartSnapshot()||value.branch!==getStoredBranchSnapshot()||value.pickup!==getPickupSlotSnapshot()))throw new Error("Your cart, branch or pickup changed elsewhere. Review the saved checkout before payment.");
   sessionStorage.setItem(ATTEMPT,JSON.stringify(value));setAttempt(value);
   postingOrder=true;const order=await createOrder(value.request,value.key,AbortSignal.timeout(20000));orderCreated=true;createdOrderNumber=order.orderNumber;
   const fingerprint=createCartFingerprint(parseCart(value.cart).items);
   savePendingOrder({orderId:order.id,orderNumber:order.orderNumber,orderStatus:order.orderStatus,branchId:order.branchId,pickupSlotId:order.pickupSlotId,totalAmount:order.totalAmount,reservationExpiresAt:order.reservationExpiresAt,createdAt:order.createdAt,cartFingerprint:fingerprint});
   sessionStorage.removeItem(ATTEMPT);setAttempt(null);
   localStorage.setItem(`gokul-mobile-checkout:${order.orderNumber}`,"1");
   if(order.orderStatus!=="PENDING_PAYMENT") {router.replace(`/orders/${encodeURIComponent(order.orderNumber)}`);return;}
   if(value.cart!==getCartSnapshot()||value.branch!==getStoredBranchSnapshot()||value.pickup!==getPickupSlotSnapshot())throw new Error("Your checkout changed elsewhere. Review the existing order before payment.");
   const applied=value.offerCode?await applyRebate(order.orderNumber,value.offerCode):await applyBestRebate(order.orderNumber);
   savePendingOrder({orderId:order.id,orderNumber:order.orderNumber,orderStatus:order.orderStatus,branchId:order.branchId,pickupSlotId:order.pickupSlotId,totalAmount:applied.totalAmount,reservationExpiresAt:order.reservationExpiresAt,createdAt:order.createdAt,cartFingerprint:fingerprint});
   if(value.cart!==getCartSnapshot()||value.branch!==getStoredBranchSnapshot()||value.pickup!==getPickupSlotSnapshot())throw new Error("Your checkout changed elsewhere. Review the existing order before payment.");
   if(Math.abs(applied.totalAmount-value.expected)>0.009)throw new Error("Your total changed after the final offer check. Review the existing order’s updated total before paying.");
   const payment=await createPayment({orderNumber:order.orderNumber},AbortSignal.timeout(20000));
   savePendingPayment({...payment,cartFingerprint:fingerprint});
   if(value.cart!==getCartSnapshot()||value.branch!==getStoredBranchSnapshot()||value.pickup!==getPickupSlotSnapshot()||Math.abs(payment.amount-value.expected)>0.009)throw new Error("Review your existing payment’s total and saved items before opening the gateway.");
   if(payment.paymentStatus!=="PENDING"){router.replace(`/checkout/payment/${encodeURIComponent(order.orderNumber)}`);return;}
   markPaymentGatewayOpened(payment.orderNumber,payment.paymentId);
   const outcome=await openPaymentCheckout(payment);
   if(payment.provider==="PHONEPE"&&payment.paymentUrl){setHandoff(true);return;}
   if(outcome.kind==="updated")savePendingPayment({...outcome.payment,cartFingerprint:fingerprint});
   router.replace(`/checkout/payment/${encodeURIComponent(order.orderNumber)}`);
  }catch(failure){setError(failure instanceof Error?failure.message:"Payment could not be started. Your cart is saved.");
   if(failure instanceof CheckoutSavingsUncertainError){const reserved=parsePendingOrder(getPendingOrderSnapshot());if(reserved?.orderNumber===createdOrderNumber)savePendingOrder({...reserved,offerRecheckRequired:true,priceReviewRequired:true});}
   if(failure instanceof ApiError&&failure.status===401){setSession({authenticated:false});setIdentityRevision(v=>v+1);}
   if(postingOrder&&!orderCreated&&failure instanceof ApiError&&(failure.status===400||failure.status===409)){sessionStorage.removeItem(ATTEMPT);setAttempt(null);setRevision(v=>v+1);}
   // An uncertain POST must be retried with its original request and key.
   if(!orderCreated&&sessionStorage.getItem(ATTEMPT))setError("We couldn’t confirm whether your order was created. Retry this exact checkout to safely recover it. Your cart is saved.");
  }finally{locked.current=false;setBusy(false);}
 }
 function guidePayment(){
  if(!contact&&verificationAction.current?.available){verificationAction.current.start();return;}
  const target=!contact?"identity":!attempt&&!branchReviewed?"branch":null;
  if(target){setGuidance(target);const section=target==="identity"?identitySection.current:branchSection.current;section?.scrollIntoView({behavior:window.matchMedia("(prefers-reduced-motion: reduce)").matches?"instant":"smooth",block:"center"});section?.querySelector<HTMLInputElement|HTMLButtonElement>(target==="branch"?'input[type="checkbox"]':'input:not(:disabled),button:not(:disabled)')?.focus({preventScroll:true});return;}
  if(!attempt&&!pickupConfirmed&&validAvailability){setPickupPopup(availabilityKey);return;}
  void pay();
 }
 useEffect(()=>{if(!handoff)return;const timer=setTimeout(()=>{setHandoff(false);setError("The payment page is taking longer to open. Continue your existing payment to try again.");},20000);return()=>clearTimeout(timer);},[handoff]);
 if(busy||handoff)return <div className="mobile-checkout-loading" role="status" aria-live="polite"><div><span className="mobile-checkout-loading-brand">Gokul Sweets</span><div className="mobile-checkout-loading-progress" aria-hidden="true"><span/></div><p><T text="Opening secure payment…" /></p><small><T text="Please keep this page open." /></small></div></div>;
 if(attemptLoaded&&cart.isEmpty&&!pending&&!attempt)return <div className="mobile-checkout mobile-empty-cart"><nav className="mobile-checkout-nav" aria-label="Checkout navigation"><MobilePageBack href="/menu" label="Back to menu"/>{features?.branchExperience&&branch&&<Link href={`/branches/${branch.id}`}><T text="Branch home" /></Link>}</nav><section><EmptyMealIllustration/><h1><T text="Your cart is empty" /></h1><p><T text="Choose your favourites from the menu to start your order." /></p><Link className="mobile-empty-cart-action" href="/menu"><T text="Browse menu" /></Link></section></div>;
 if(attemptLoaded&&(!branch||cart.branchId!==branch.id)&&!attempt&&!pending)return <div className="mobile-checkout"><h1><T text="Your order" /></h1><p><T text="Choose the matching branch before checking out." /></p><Link href="/menu"><T text="Back to menu" /></Link></div>;
 return <div className="mobile-checkout">
  <OfferArrival ready={!!preview} amount={Number(best?.rebateAmount??0)} name={best?.name} code={best?.code} failed={!!(priceError?.key===priceKey||availabilityError?.key===availabilityKey)} disabled={!!pending||!!attempt}/>
  <nav className="mobile-checkout-nav" aria-label="Checkout navigation"><MobilePageBack href="/menu" label="Back to menu" className="mobile-checkout-back"/>{features?.branchExperience&&branch&&<Link href={`/branches/${branch.id}`}><T text="Branch home" /></Link>}</nav>
  <div className="mobile-checkout-heading"><div><p>{branch?.name??"Saved checkout"}</p><h1><T text="Your order" /></h1></div><Link href="/menu"><T text="Add more" /></Link></div>
  {recovery&&<p role="status"><T text="Payment wasn’t completed. Your cart is saved; review it and try again." /></p>}
  <div ref={identitySection} className={guidance==="identity"&&!contact?"mobile-checkout-guidance":""}><CustomerIdentityPanel mode="mobileCheckout" sessionRevision={identityRevision} onSessionChange={onSession} verificationRef={verificationAction} onVerificationBusyChange={setVerificationBusy}/>{guidance==="identity"&&!contact&&<p role="status"><T text="Sign in here to continue to payment. Your cart is saved."/></p>}</div>
  {!pending&&<>
  <section className="mobile-checkout-section" aria-label="Checkout pickup"><h2><T text="Pickup" /></h2>{validAvailability&&chosen?<><p><strong>{pickupLabel(chosen,locale,startDate)} IST</strong></p><button className="mobile-change-pickup" type="button" disabled={editingLocked} onClick={()=>setPickupPopup(availabilityKey)}><T text={pickupConfirmed?"Change pickup":"Confirm pickup"} /></button>{!pickupConfirmed&&<p role="status"><T text="Saved pickup — confirm this date and time before payment." /></p>}</>:<>
   {storedPickup&&!attempt&&<p><T text="Your selected pickup" />: <strong>{pickupLabel(storedPickup,locale,startDate)} IST</strong></p>}
   {!storedPickup&&pickupIntent.date&&!attempt&&<p><T text="Your selected pickup date" />: <strong>{pickupDateLabel(pickupIntent.date,locale,startDate)}</strong></p>}
   <p role={pickupNeedsChoice?"alert":"status"}>{availabilityError?.key===availabilityKey?availabilityError.message:pickupNeedsChoice?<T text={storedPickup?(pickupExpired?"Your pickup time has passed. Choose another time. Your pickup hasn’t changed.":"Some items aren’t available at your selected pickup time. Choose another time or remove those items. Your pickup hasn’t changed."):"Choose when you’ll collect your order. Check the date and time before continuing."} />:attempt?`${attempt.request.pickupType} pickup saved for retry. Your original pickup will be recovered with the order.`:<T text="Checking your selected pickup…" />}</p>
   {pickupNeedsChoice&&<button className="mobile-change-pickup" type="button" disabled={editingLocked} onClick={()=>setPickupPopup(availabilityKey)}><T text="Choose pickup" /></button>}
  </>}{availabilityError?.key===availabilityKey&&<button onClick={()=>setRevision(v=>v+1)}><T text="Try again" /></button>}</section>
  </>}
  <section aria-label="Cart items" className="mobile-checkout-section mobile-cart-section">
   <div className="mobile-cart-toolbar"><h2><T text="Items" /></h2><button type="button" disabled={editingLocked} aria-pressed={selecting} onClick={()=>{setSelecting(v=>!v);setSelection({branchId:cart.branchId,ids:[]});}}><T text={selecting?"Cancel selection":"Select items"} /></button><button type="button" className="mobile-clear-cart" disabled={editingLocked||cart.isEmpty} onClick={()=>removeItems(cart.items.map(item=>item.product.id))}><T text="Clear cart" /></button></div>
   {selecting&&<div className="mobile-cart-selection"><label><input type="checkbox" disabled={editingLocked} checked={!cart.isEmpty&&selected.length===cart.items.length} onChange={e=>setSelection({branchId:cart.branchId,ids:e.target.checked?cart.items.map(item=>item.product.id):[]})}/><T text="Select all" /></label><button type="button" disabled={editingLocked||!selected.length} onClick={()=>removeItems(selected)}><T text="Remove selected" /> ({selected.length})</button></div>}
   {cart.items.map(item=><article className="mobile-cart-row" key={item.product.id}>
    <div className="mobile-cart-item"><div className="mobile-cart-item-title">{selecting&&<label className="mobile-cart-item-select"><input type="checkbox" aria-label={`Select ${item.product.name}`} disabled={editingLocked} checked={selected.includes(item.product.id)} onChange={e=>setSelection({branchId:cart.branchId,ids:e.target.checked?[...selected,item.product.id]:selected.filter(id=>id!==item.product.id)})}/></label>}<strong>{item.product.name}</strong></div><div className="mobile-cart-item-meta"><p>{item.product.saleMode==="WEIGHT"?`${formatWeight(item.weightGrams??0)} · ${money(item.product.price*(item.weightGrams??0)/1000)}`:money(item.product.price*item.quantity)}</p><button className="mobile-remove-item" type="button" disabled={editingLocked} aria-label={`Remove ${item.product.name} from cart`} onClick={()=>removeItems([item.product.id])}><svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7"/></svg><T text="Remove" /></button></div></div>
    <div className="mobile-quantity"><button type="button" disabled={editingLocked} aria-label={`Remove one ${item.product.name}`} onClick={()=>cart.decreaseQuantity(item.product.id)}>−</button><span>{item.product.saleMode==="WEIGHT"?formatWeight(item.weightGrams??0):item.quantity}</span><button type="button" disabled={editingLocked} aria-label={`Add one ${item.product.name}`} onClick={()=>cart.increaseQuantity(item.product.id)}>+</button></div>
   </article>)}
  </section>
  {features?.pickupAddOns&&branch&&!pending&&!attempt&&<PickupAddOns compact branchId={branch.id} date={chosen?.date??startDate} disabled={busy||addOnBusy||pickupValidating||!chosen||!validAvailability} offers={[...(preview?.offers??[]),...(preview?.spendTargets??[])]} onAdded={()=>{}} onBusy={setAddOnBusy}/>}
  {pending?<section className="mobile-checkout-section"><h2><T text="Continue your existing order" /></h2><p><T text="Your order is already reserved. Check its payment status before starting another checkout." /></p><Link href={`/checkout/${pending.offerRecheckRequired||pending.priceReviewRequired?"offers":"payment"}/${encodeURIComponent(pending.orderNumber)}`}><T text={pending.offerRecheckRequired||pending.priceReviewRequired?"Review reserved total":"Continue payment"} /></Link></section>:<>


  {features?.gokulRewards&&preview?.rewards&&<RewardPicker compact wallet={preview.rewards} selected={rewardCode} discount={Number(preview.rewardDiscount??0)} busy={editingLocked} onSelect={code=>void changeSavings({rewardCode:code}).catch(failure=>setError(failure instanceof Error?failure.message:"Rewards could not be checked. Try again."))}/>}
  {features?.gokulRewards&&rewardCode&&!preview&&priceError?.key===priceKey&&<section className="mobile-checkout-section" aria-label="Reward recovery"><p role="alert"><T text="Your selected reward could not be verified for this cart. Remove it to check the current price without coins." /></p><button type="button" disabled={editingLocked} onClick={()=>{setRewardCode(null);setError("");}}><T text="Remove reward" /></button></section>}
  <section className="mobile-checkout-section mobile-checkout-offers" aria-busy={savingsBusy}><h2><span className="mobile-offers-mark" aria-hidden="true">%</span><T text="Offers & total" /></h2>{preview&&(Number(preview.rewardDiscount??0)+(best?.rebateAmount??0))>0&&<p className="mobile-checkout-savings" role="status"><span aria-hidden="true">✓</span><T text="You’re saving"/> {money(Number(preview.rewardDiscount??0)+(best?.rebateAmount??0))} <T text="on this order"/></p>}{preview?<><p>{best?<><strong>{best.name}</strong> · <T text={offerCode?"Your selected offer":"Best available offer applied automatically"} /> · −{money(best.rebateAmount)}</>:<T text="Your current menu price" />}</p>{<button className="checkout-change-offer" type="button" disabled={editingLocked} onClick={()=>setOfferPopup(true)}><T text={preview.offers.length?"Change offer":"Add offer code"} /></button>}{savingsBusy&&<p role="status"><T text="Verifying savings…" /></p>}<button className="mobile-bill-summary-trigger" type="button" onClick={()=>setBillOpen(true)}><span><T text="Price details"/><small><T text="Including taxes & charges"/></small></span><strong>{money(total!)}</strong><svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="m9 5 7 7-7 7"/></svg></button></>:<p role="status">{priceError?.key===priceKey?priceError.message:pickupNeedsChoice||pickupNeedsConfirmation?<T text="Choose a pickup time to see your final total." />:contact?"Checking prices and your best offer…":"Verify your phone here to see your final total and eligible offers."}</p>}{priceError?.key===priceKey&&<button onClick={()=>setRevision(v=>v+1)}><T text="Try again" /></button>}</section>
  </>}
  {billOpen&&preview&&<MobileBillSummary quote={preview.quote} best={best} rewardDiscount={Number(preview.rewardDiscount??0)} paymentFee={preview.paymentFee} paymentFeeTax={preview.paymentFeeTax} total={total!} onClose={()=>setBillOpen(false)}/>}
  {offerPopup&&preview&&<OfferChoiceDialog offers={preview.offers} selected={best?.code??null} onApply={code=>changeSavings({offerCode:code})} onClose={()=>setOfferPopup(false)}/> }
  {pickupPopup===availabilityKey&&validAvailability&&!pending&&!attempt&&<MobilePickupDialog dates={validAvailability.dates} options={pickupOptions(validAvailability)} chosen={chosen??storedPickup} initialDate={pickupIntent.date??startDate} today={startDate} disabled={editingLocked} onClose={closePickup} onConfirm={confirmPickup}/>}
  {error&&<p role="alert" className="mobile-checkout-error">{error}</p>}
  {attempt&&<p role="status"><T text="This checkout is saved for a safe retry. Its items and pickup are locked until the order is recovered." /></p>}
  {!pending&&!attempt&&branch&&<section ref={branchSection} className={`mobile-branch-review ${guidance==="branch"&&!branchReviewed?"mobile-checkout-guidance":""}`} aria-label="Confirm pickup branch"><span><T text="YOUR PICKUP BRANCH"/></span><h2>{branch.name}</h2><p>{[branch.address,branch.city,branch.pincode].filter(Boolean).join(", ")||"Contact the branch to confirm its location before travelling."}</p>{guidance==="branch"&&!branchReviewed&&<p role="status"><T text="Confirm this collection branch below to enable payment."/></p>}<label><input type="checkbox" checked={branchReviewed} onChange={event=>setConfirmedBranch(event.target.checked?branchReviewKey:null)}/><span><T text="I will collect my order at this branch."/></span></label><p><T text="Check the branch before paying. Customer cancellation is available for 10 minutes after payment confirmation. Only the food amount is refundable; additional charges and their taxes are retained."/></p><Link href="/cancellation-policy?from=/checkout/mobile"><T text="Cancellation & refunds"/></Link></section>}
  {!pending&&<p id="mobile-payment-requirements" className="sr-only">{!contact?<T text="Sign in to enable payment. Select Pay now to go to sign-in."/>:!branchReviewed&&!attempt?<T text="Confirm your pickup branch to enable payment. Select Pay now to go to confirmation."/>:pickupNeedsChoice||pickupNeedsConfirmation?<T text="Choose and confirm a pickup date and time to enable payment."/>:<T text="Your price and pickup are verified before payment."/>}</p>}
  {!pending&&<div className="mobile-checkout-pay"><div><small><T text={total===null?"Items subtotal":!attempt&&!pickupConfirmed?"Estimated total":"Total to pay"}/></small><strong>{money(attempt?.expected??total??cart.subtotal)}</strong></div><button type="button" disabled={verificationBusy||!identityResolved||!attemptLoaded||busy||addOnBusy||pickupValidating||savingsBusy||(!attempt&&!!contact&&branchReviewed&&(!validAvailability||(pickupConfirmed&&!preview)))} data-payment-ready={!!contact&&(!!attempt||(branchReviewed&&pickupConfirmed&&!!validAvailability&&!!preview))} aria-describedby="mobile-payment-requirements" onClick={guidePayment}>{busy?<T text="Opening payment…"/>:verificationBusy?<T text="Verifying phone…"/>:!identityResolved?<T text="Checking…"/>:!contact?<T text="Verify phone to continue"/>:attempt?<T text="Retry checkout"/>:branchReviewed&&(pickupNeedsChoice||pickupNeedsConfirmation)?<T text={pickupNeedsConfirmation?"Confirm pickup time":"Choose pickup time"}/>:<T text="Pay now"/>}</button></div>}
 </div>;
}
