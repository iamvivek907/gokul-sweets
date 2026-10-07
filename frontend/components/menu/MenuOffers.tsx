"use client";
import {useEffect,useMemo,useState} from "react";
import {usePublicMenuOffers} from "@/hooks/usePublicMenuOffers";
import type {PublicOffer} from "@/hooks/usePublicMenuOffers";
import {useCart} from "@/hooks/useCart";
import {usePickupIntent} from "@/hooks/usePickupIntent";
import {availabilityItems} from "@/services/availabilityApi";
import {apiClient} from "@/services/apiClient";
import {verifiedCheckoutContact} from "@/lib/checkoutIdentity";
import {subscribeCustomerIdentityChanges} from "@/lib/customerIdentityEvents";
import {usefulRebateTarget} from "@/lib/pickupAddOnRebate";
import type {CustomerSession} from "@/components/customer/CustomerIdentityPanel";
import type {AvailableRebateResponse} from "@/types/rebate";
import MenuDiscoverySheet from "./MenuDiscoverySheet";
import OfferLabelIcon from "./OfferLabelIcon";
import {T} from "@/lib/language";
type Offers={offers:AvailableRebateResponse[];spendTargets?:AvailableRebateResponse[]};
const money=(amount:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR",maximumFractionDigits:2}).format(amount);
const publicLabel=(offer:PublicOffer)=>{
 const tier=offer.tiers[0],minimum=tier?.minimumOrderAmount??offer.minimumOrderAmount;
 const discount=tier?money(tier.rebateAmount):offer.rebateType==="PERCENTAGE"?`${offer.rebateValue}%`:money(offer.rebateValue??0);
 return `${discount} off${minimum>0?` above ${money(minimum)}`:""}${offer.rebateType==="PERCENTAGE"&&offer.maximumDiscountAmount?` · up to ${money(offer.maximumDiscountAmount)}`:""}`;
};
export default function MenuOffers({branchId,onTarget}:{branchId:number;onTarget:(target:AvailableRebateResponse|null)=>void}){
 const cart=useCart(),pickup=usePickupIntent(branchId);
 const [session,setSession]=useState<CustomerSession|null|undefined>(undefined),[identity,setIdentity]=useState(0),[open,setOpen]=useState(false),[retry,setRetry]=useState(0);
 const catalog=usePublicMenuOffers(branchId,retry);
 const publicOffers=catalog.offers;
 const [result,setResult]=useState<{key:string;value:Offers|null}|null>(null);
 useEffect(()=>{let controller:AbortController;const load=()=>{controller?.abort();controller=new AbortController();const signal=controller.signal;void apiClient<CustomerSession>("/api/customer/identity/me",{credentials:"include",signal:AbortSignal.any([signal,AbortSignal.timeout(5000)])}).then(v=>{if(!signal.aborted)setSession(v);}).catch(()=>{if(!signal.aborted)setSession(null);});};load();const off=subscribeCustomerIdentityChanges(()=>{setIdentity(v=>v+1);setOpen(false);setSession(undefined);setResult(null);load();});return()=>{controller.abort();off();};},[branchId,retry]);
 const contact=session?verifiedCheckoutContact(session):null;
 const request=contact&&pickup.selection&&cart.branchId===branchId&&cart.items.length?JSON.stringify({branchId,pickupSlotId:pickup.selection.slot.id,pickupType:pickup.selection.pickupType,customerName:contact.name,customerPhone:contact.phone,items:availabilityItems(cart.items)}):null;
 const key=JSON.stringify([request,identity,retry,catalog.revision]);
 useEffect(()=>{if(!request)return;const c=new AbortController();void apiClient<Offers>("/api/orders/mobile-preview",{method:"POST",body:request,credentials:"include",signal:AbortSignal.any([c.signal,AbortSignal.timeout(8000)])}).then(value=>{if(!c.signal.aborted)setResult({key,value});}).catch(()=>{if(!c.signal.aborted)setResult({key,value:null});});return()=>c.abort();},[request,key]);
 const current=result?.key===key?result:null;
 const targetValues=JSON.stringify(current?.value?.spendTargets??[]);
 const target=useMemo(()=>usefulRebateTarget(JSON.parse(targetValues)),[targetValues]);
 useEffect(()=>{onTarget(target);return()=>onTarget(null);},[onTarget,target]);
 const offers=current?.value?.offers??[],targets=current?.value?.spendTargets??[];
 const best=offers.reduce<AvailableRebateResponse|null>((best,o)=>!best||o.payableAfterRebate<best.payableAfterRebate?o:best,null);
 const headline=best&&best.rebateAmount>0?`Save ${money(best.rebateAmount)} on this order`:target?`${money(target.nextSlabRebateAmount!)} off above ${money(target.nextSlabMinimumOrderAmount??0)}`:!current?.value&&publicOffers.length?publicLabel(publicOffers[0]):"Offers & savings";
 const summary=catalog.failed?"Offers could not be checked. Try again.":!cart.items.length?(publicOffers.length?"Standard pickup · eligibility checked at checkout":"Add items to discover eligible savings"):!pickup.selection?"Choose pickup to check eligible offers":session===undefined?"Checking your offers…":session===null?"Offers could not be checked. Try again.":!contact?"Verify your phone at checkout to check your offers":!current?"Checking your offers…":!current.value?"Offers could not be checked. Try again.":offers.length?`${offers.length} eligible offer${offers.length===1?"":"s"} · best selected at checkout`:target?`Add ${money(target.amountNeededForNextSlab!)} in eligible items to unlock ${money(target.nextSlabRebateAmount!)} off`:"No eligible offers for this selection";
 return <section className="menu-offers" aria-label="Menu offers"><button id="menu-offers-open" type="button" onClick={()=>setOpen(true)} aria-haspopup="dialog"><span aria-hidden="true"><OfferLabelIcon /></span><span><strong><T text={headline}/></strong><small role="status"><T text={summary}/></small></span><span>{new Set([...publicOffers,...offers,...targets].map(o=>o.rebateId)).size||"View"} <T text="offers"/></span></button>
 {open&&<MenuDiscoverySheet title="Offers & savings" onClose={()=>setOpen(false)}><p role="status"><T text={summary}/></p>{(catalog.failed||session===null||current&&!current.value&&request)&&<button type="button" onClick={()=>{setSession(undefined);setResult(null);setRetry(v=>v+1);}}><T text="Retry offers"/></button>}{offers.map(o=><article className="menu-offer-card" key={o.rebateId}><strong className="menu-offer-name"><OfferLabelIcon />{o.name}</strong><p><T text="Save"/> {money(o.rebateAmount)} · {o.code}</p>{o.description&&<p>{o.description}</p>}<small><T text="Eligible now · best offer selected at checkout"/></small></article>)}{targets.map(o=><article className="menu-offer-card" key={`target-${o.rebateId}`}><strong className="menu-offer-name"><OfferLabelIcon />{o.name}</strong><p><T text="Add"/> {money(o.amountNeededForNextSlab??0)} <T text="in eligible items"/></p><p><T text="Unlock"/> {money(o.nextSlabRebateAmount??0)} <T text="off"/></p>{o.description&&<p>{o.description}</p>}<small><T text="Fees don’t count. Savings checked again at checkout."/></small></article>)}{publicOffers.filter(o=>![...offers,...targets].some(known=>known.rebateId===o.rebateId)).map(o=><article className="menu-offer-card" key={`public-${o.rebateId}`}><strong className="menu-offer-name"><OfferLabelIcon />{o.name}</strong><p>{publicLabel(o)}</p>{o.description&&<p>{o.description}</p>}<small>{o.code} · <T text="Standard pickup. Eligibility and usage limits checked at checkout."/></small>{o.tiers.length>1&&<ul>{o.tiers.slice(1).map(t=><li key={t.minimumOrderAmount}>{money(t.rebateAmount)} <T text="off above"/> {money(t.minimumOrderAmount)}</li>)}</ul>}</article>)}{targets.length>0&&<button type="button" className="menu-offer-browse" onClick={()=>{setOpen(false);requestAnimationFrame(()=>document.getElementById("gokul-menu-items")?.scrollIntoView({behavior:matchMedia("(prefers-reduced-motion: reduce)").matches?"instant":"smooth",block:"start"}));}}><T text="Browse items to reach an offer"/></button>}</MenuDiscoverySheet>}
 </section>;
}
