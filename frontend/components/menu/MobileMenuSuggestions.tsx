"use client";
import Image from "next/image";
import {useEffect,useMemo,useRef,useState} from "react";
import {useCart} from "@/hooks/useCart";
import {usePickupIntent} from "@/hooks/usePickupIntent";
import {apiClient} from "@/services/apiClient";
import {availabilityItems,checkCartAvailability} from "@/services/availabilityApi";
import {getCartSnapshot} from "@/lib/cartStorage";
import {getStoredBranchSnapshot} from "@/lib/branchStorage";
import {getPickupSlotSnapshot} from "@/lib/checkoutStorage";
import {usefulRebateTarget} from "@/lib/pickupAddOnRebate";
import {rankAddOns} from "@/lib/addOnRanking";
import {verifiedCheckoutContact} from "@/lib/checkoutIdentity";
import {menuPickupOptions} from "@/lib/menuPickupOptions";
import {pickupIsFresh} from "@/lib/pickupFreshness";
import {constrainedPhoneConnection} from "@/lib/mobileConnection";
import {formatWeight} from "@/lib/orderQuantity";
import {T} from "@/lib/language";
import type {CustomerSession} from "@/components/customer/CustomerIdentityPanel";
import type {PickupSelection} from "@/types/pickup";
import type {MenuProduct} from "@/types/menu";
import type {AvailableRebateResponse} from "@/types/rebate";
type Visit={branchId:number;orderStatus:string;paymentStatus:string;items:{productId:number}[]};
type Suggestion={includesTax?:boolean;product:MenuProduct;weightGrams:number|null;portionPrice:number;portionTotal:number;reason:string};
const money=(value:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR",maximumFractionDigits:2}).format(value);
export default function MobileMenuSuggestions({branchId,products,onTarget}:{branchId:number;products:MenuProduct[];onTarget?:(target:AvailableRebateResponse|null)=>void}){
 const cart=useCart(),intent=usePickupIntent(branchId),locked=useRef(false);
 const [visits,setVisits]=useState<Visit[]>([]);
 const [session,setSession]=useState<CustomerSession|null>(null),[result,setResult]=useState<{key:string;items:Suggestion[];offers:AvailableRebateResponse[]}|null>(null),[busy,setBusy]=useState(false),[message,setMessage]=useState("");
 const contact=useMemo(()=>session?verifiedCheckoutContact(session):null,[session]);
 const selectionKey=JSON.stringify(intent.selection);
 const selected=useMemo(()=>JSON.parse(selectionKey) as PickupSelection|null,[selectionKey]);
 const request=JSON.stringify({serviceDate:intent.date,items:availabilityItems(cart.items)});
 const key=JSON.stringify([branchId,request,selected?.slot.id,selected?.pickupType,contact?.phone]);
 useEffect(()=>{
  let controller:AbortController;
  async function load(){
   controller?.abort();const c=new AbortController();controller=c;let identified=false;
   try{const value=await apiClient<CustomerSession>("/api/customer/identity/me",{credentials:"include",signal:AbortSignal.any([c.signal,AbortSignal.timeout(5000)])});if(c.signal.aborted)return;identified=true;setSession(value);setVisits([]);
    if(!value.authenticated||constrainedPhoneConnection())return;
    const history=await apiClient<{orderNumber:string;branchId:number;orderStatus:string}[]>("/api/customer/identity/orders",{credentials:"include",signal:AbortSignal.any([c.signal,AbortSignal.timeout(5000)])});
    if(!Array.isArray(history)||c.signal.aborted)return;
    const completed=history.filter(o=>o.branchId===branchId&&["PICKED_UP","DELIVERED"].includes(o.orderStatus)).slice(0,3);
    const details=await Promise.allSettled(completed.map(o=>apiClient<Visit>(`/api/customer/identity/orders/${encodeURIComponent(o.orderNumber)}`,{credentials:"include",signal:AbortSignal.any([c.signal,AbortSignal.timeout(5000)])})));
    if(!c.signal.aborted)setVisits(details.flatMap(r=>r.status==="fulfilled"&&Array.isArray(r.value.items)?[r.value]:[]));
   }catch{if(!c.signal.aborted){if(!identified)setSession(null);setVisits([]);}}
  }
  void load();window.addEventListener("gokul-customer-identity-changed",load);
  return()=>{controller?.abort();window.removeEventListener("gokul-customer-identity-changed",load);};
 },[branchId]);
 useEffect(()=>{
  if(!intent.date||!cart.items.length||cart.branchId!==branchId)return;
  const c=new AbortController();
  const timer=setTimeout(()=>void (async()=>{
   let items=await apiClient<Suggestion[]>(`/api/menu/pickup-addons?branchId=${branchId}`,{method:"POST",body:request,credentials:"include",signal:AbortSignal.any([c.signal,AbortSignal.timeout(8000)])}).catch(()=>[]);
   const counts=new Map<number,number>();for(const visit of visits){if(visit.branchId!==branchId||visit.paymentStatus!=="PAID"||!["PICKED_UP","DELIVERED"].includes(visit.orderStatus))continue;for(const item of visit.items)counts.set(item.productId,(counts.get(item.productId)??0)+1);}
   const favourites=products.filter(p=>p.available&&counts.has(p.id)&&!cart.items.some(i=>i.product.id===p.id)&&!items.some(i=>i.product.id===p.id)).sort((a,b)=>counts.get(b.id)!-counts.get(a.id)!||a.id-b.id).slice(0,2).map(product=>{const weightGrams=product.saleMode==="WEIGHT"?product.minimumWeightGrams??250:null;const price=Math.round(product.price*(weightGrams===null?1:weightGrams/1000)*100)/100;return {product,weightGrams,portionPrice:price,portionTotal:price,includesTax:false,reason:"Your completed-order favourite"};});
   items=[...favourites,...items].filter(s=>products.some(p=>p.id===s.product.id&&p.available)&&!cart.items.some(i=>i.product.id===s.product.id)).slice(0,5);
   if(selected&&items.length){
    const combined=[...JSON.parse(request).items,...items.map(s=>({productId:s.product.id,quantity:s.weightGrams===null?1:null,weightGrams:s.weightGrams}))];
    const available=await checkCartAvailability(branchId,selected.date,1,combined,AbortSignal.any([c.signal,AbortSignal.timeout(8000)])).catch(()=>null);
    const option=available&&menuPickupOptions(available,combined.map(i=>i.productId)).find(s=>s.slot.id===selected.slot.id&&s.pickupType===selected.pickupType);
    const issues=available?.dates.find(d=>d.date===selected.date)?.slots.find(s=>s.slot.id===selected.slot.id)?.issues??[];
    items=option&&!issues.some(i=>!i.available&&cart.items.some(c=>c.product.id===i.productId))?items.filter(s=>!issues.some(i=>i.productId===s.product.id&&!i.available)):[];
   }
   let offers:AvailableRebateResponse[]=[];
   if(selected&&contact){const preview=await apiClient<{offers:AvailableRebateResponse[];spendTargets?:AvailableRebateResponse[]}>("/api/orders/mobile-preview",{method:"POST",credentials:"include",body:JSON.stringify({branchId,pickupSlotId:selected.slot.id,pickupType:selected.pickupType,customerName:contact.name,customerPhone:contact.phone,items:JSON.parse(request).items}),signal:AbortSignal.any([c.signal,AbortSignal.timeout(8000)])}).catch(()=>null);if(preview)offers=[...preview.offers,...(preview.spendTargets??[])];}
   if(!c.signal.aborted)setResult({key,items,offers});
  })(),450);
  return()=>{clearTimeout(timer);c.abort();};
 },[key,branchId,request,intent.date,cart.branchId,cart.items.length,cart.items,products,visits,selected,contact]);
 const current=result?.key===key?result:null;
 const target=useMemo(()=>usefulRebateTarget(current?.offers??[]),[current]);
 useEffect(()=>{onTarget?.(target);return()=>onTarget?.(null);},[target,onTarget]);
 const live=new Map(products.map(p=>[p.id,p]));
 const suggestions=rankAddOns((current?.items??[]).filter(s=>live.get(s.product.id)?.available&&!cart.items.some(i=>i.product.id===s.product.id)),target?.amountNeededForNextSlab??null);
 async function add(s:Suggestion){
  if(locked.current)return;locked.current=true;setBusy(true);setMessage("");
  const before=getCartSnapshot(),branch=getStoredBranchSnapshot(),pickup=getPickupSlotSnapshot();
  try{
   const combined=JSON.parse(request);combined.items.push({productId:s.product.id,quantity:s.weightGrams===null?1:null,weightGrams:s.weightGrams});
   const check=await apiClient<{orderable:boolean}>(`/api/menu/pickup-addons/check?branchId=${branchId}`,{method:"POST",credentials:"include",body:JSON.stringify(combined),signal:AbortSignal.timeout(8000)});
   if(!check.orderable)throw new Error("This addition no longer fits your pickup. Choose another time.");
   if(selected){const availability=await checkCartAvailability(branchId,selected.date,1,combined.items,AbortSignal.timeout(8000));const slot=availability.dates.find(d=>d.date===selected.date)?.slots.find(s=>s.slot.id===selected.slot.id);if(!pickupIsFresh(selected,new Date())||!slot||!(selected.pickupType==="PRIORITY"?slot.priorityAvailable:slot.normalAvailable))throw new Error("Change pickup time to add this item. Your cart is saved.");}
   if(before!==getCartSnapshot()||branch!==getStoredBranchSnapshot()||pickup!==getPickupSlotSnapshot())throw new Error("Your cart or pickup changed. Review it before adding.");
   const product=live.get(s.product.id);if(!product?.available||cart.addItem(product,branchId,s.weightGrams??undefined)!=="added")throw new Error("Review this item’s availability before adding.");
   setMessage(`${s.product.name} added. Your updated savings will be checked at checkout.`);
  }catch(error){setMessage(error instanceof Error?error.message:"We couldn’t check this addition. Your cart is saved. Try again.");}
  finally{locked.current=false;setBusy(false);}
 }
 if(!suggestions.length&&!target&&!message)return null;
 return <section className="mobile-menu-suggestions" aria-label="Pairs well with your selection"><header><div><span><T text="MAKE IT A LITTLE SWEETER"/></span><h3><T text="Pairs well with your selection"/></h3></div></header>
 {target&&<div id="mobile-menu-offer" className="mobile-menu-offer-progress" role="status"><span aria-hidden="true">%</span><div><strong><T text="Add"/> {money(target.amountNeededForNextSlab!)} <T text="in eligible items"/></strong><p><T text="Unlock"/> {money(target.nextSlabRebateAmount!)} <T text="off"/> · {target.name}</p><small><T text="Fees don’t count. Savings checked again at checkout."/></small></div></div>}
 <div className="mobile-menu-pairing-row">{suggestions.map(s=><article key={s.product.id}><div className="mobile-menu-pairing-photo">{s.product.imageUrl?<Image src={s.product.imageUrl} alt={s.product.name} fill sizes="144px"/>:<span aria-hidden="true">G</span>}<button type="button" disabled={busy} aria-label={`Add ${s.product.name}`} onClick={()=>void add(s)}><T text={busy?"Checking…":"Add"}/> +</button></div><h4>{s.product.name}</h4><strong>{money(s.portionTotal)}</strong><small>{s.weightGrams===null?"1 piece":formatWeight(s.weightGrams)} · <T text={s.includesTax===false?"tax checked at checkout":"incl. item tax"}/></small><p>{s.reason}</p></article>)}</div>
 {message&&<p role="status">{message}</p>}</section>;
}
