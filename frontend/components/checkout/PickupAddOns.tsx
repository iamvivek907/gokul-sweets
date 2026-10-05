"use client";
import {T,useTranslation} from "@/lib/language";
import {CheckoutUpdateUncertainError} from "@/lib/checkoutRefresh";
import Image from "next/image";
import {useEffect,useRef,useState} from "react";
import {useCart} from "@/hooks/useCart";
import {apiClient} from "@/services/apiClient";
import {getCartSnapshot,parseCart,saveCart} from "@/lib/cartStorage";
import {getStoredBranchSnapshot} from "@/lib/branchStorage";
import {getPickupSlotSnapshot} from "@/lib/checkoutStorage";
import {usefulRebateTarget} from "@/lib/pickupAddOnRebate";
import {formatWeight} from "@/lib/orderQuantity";
import type {MenuProduct} from "@/types/menu";
import type {AvailableRebateResponse} from "@/types/rebate";
import styles from "./PickupAddOns.module.css";
type Suggestion={product:MenuProduct;weightGrams:number|null;portionPrice:number;portionTotal:number;reason:string};
const money=(n:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR",maximumFractionDigits:2}).format(n);
export default function PickupAddOns({branchId,date,orderNumber,disabled,offers=[],onAdded,onBusy,onAdjust,compact=false}:{branchId:number;date:string;orderNumber?:string;disabled:boolean;offers?:AvailableRebateResponse[];onAdded:(signal:AbortSignal)=>void|Promise<void>;onBusy?:(busy:boolean)=>void;onAdjust?:()=>void;compact?:boolean}) {
    const translate = useTranslation();
 const {items,addItem}=useCart();const locked=useRef(false);
 const mounted=useRef(false),addition=useRef<AbortController|null>(null),busyCallback=useRef(onBusy);
 useEffect(()=>{busyCallback.current=onBusy;},[onBusy]);
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;addition.current?.abort();busyCallback.current?.(false);};},[]);
 const [response,setResponse]=useState<{key:string;items:Suggestion[]}|null>(null);
 const [tab,setTab]=useState("Popular");
 const [dismissed,setDismissed]=useState(false),[message,setMessage]=useState(""),[busy,setBusy]=useState(false);
 const request=JSON.stringify({serviceDate:date,items:items.map(i=>({productId:i.product.id,quantity:i.product.saleMode==="UNIT"?i.quantity:null,weightGrams:i.weightGrams}))});
 const path=`/api/menu/pickup-addons?branchId=${branchId}${compact?"&browse=true":""}${orderNumber?`&orderNumber=${encodeURIComponent(orderNumber)}`:""}`;
 const key=`${path}:${request}`;
 useEffect(()=>()=>{addition.current?.abort();},[key]);
 useEffect(()=>{if(dismissed)return;const controller=new AbortController();apiClient<Suggestion[]>(path,{method:"POST",body:request,credentials:"include",signal:AbortSignal.any([controller.signal,AbortSignal.timeout(8000)])}).then(items=>{if(!controller.signal.aborted)setResponse({key,items});}).catch(()=>{if(!controller.signal.aborted)setResponse({key,items:[]});});return()=>controller.abort();},[path,request,key,dismissed]);
 const target=usefulRebateTarget(offers);
 const suggestions=response?.key===key ? response.items.filter(s=>(!compact||s.product.available)&&!items.some(i=>i.product.id===s.product.id)) : [];
 const group=(s:Suggestion)=>/^(beverages?|drinks?|cold drinks|juices?)$/i.test(s.product.categoryName.trim())?"Beverages":/^(sides?|snacks?|starters?|accompaniments?)$/i.test(s.product.categoryName.trim())?"Sides":"Popular";
 const visible=compact?(tab==="Popular"?suggestions.slice(0,4):suggestions.filter(s=>group(s)===tab).slice(0,4)):suggestions;
 const loading=response?.key!==key;
 async function add(s:Suggestion) {
  if(disabled||locked.current)return;locked.current=true;setBusy(true);onBusy?.(true);setMessage("");let added=false;let addedSnapshot:string|null=null;
  const cart=getCartSnapshot(),branch=getStoredBranchSnapshot(),pickup=getPickupSlotSnapshot();
  const controller=new AbortController();addition.current=controller;
  const signal=AbortSignal.any([controller.signal,AbortSignal.timeout(15000)]);
  try {const combined=JSON.parse(request);combined.items.push({productId:s.product.id,quantity:s.weightGrams==null?1:null,weightGrams:s.weightGrams});
   const check=await apiClient<{orderable:boolean}>(path.replace("pickup-addons?","pickup-addons/check?"),{method:"POST",body:JSON.stringify(combined),credentials:"include",signal});
   if(signal.aborted||!mounted.current)return;
   if(cart!==getCartSnapshot()||branch!==getStoredBranchSnapshot()||pickup!==getPickupSlotSnapshot()){setMessage("Your cart or pickup changed. Review it before adding.");return;}
   if(!check.orderable){setMessage("This addition no longer fits your pickup. Your cart is unchanged.");return;}
   // Validation is complete; our own cart write must not abort the subsequent quote refresh.
   addition.current=null;
   if(addItem(s.product,branchId,s.weightGrams??undefined)!=="added"){setMessage("Choose the matching branch before adding.");return;}
   added=true;addedSnapshot=getCartSnapshot();await onAdded(AbortSignal.timeout(15000));if(!mounted.current||controller.signal.aborted)return;setMessage(`${s.product.name} added. Review your updated price and offer before payment.`);
  } catch(error) {if(!mounted.current||controller.signal.aborted)return;if(error instanceof CheckoutUpdateUncertainError){setMessage(error.message);return;}if(added&&addedSnapshot===getCartSnapshot()){saveCart(parseCart(cart));added=false;}setMessage(added?"This item is in your cart. Review the cart to refresh its price before payment.":"We couldn’t complete this addition. Your existing cart and pickup are unchanged. Try again, or adjust quantities and pickup here.");}
  finally{if(addition.current===controller)addition.current=null;locked.current=false;if(mounted.current){setBusy(false);onBusy?.(false);}}
 }
 if(dismissed || (!compact&&!suggestions.length&&!message&&!target))return null;
 return <section className={`${styles.panel} ${compact?styles.compact:""}`} aria-label="Pickup add-ons"><div className={styles.heading}><div><p className={styles.eyebrow}><T text="A little extra for your pickup" /></p><h2><T text={compact?"Complete your meal":"Pairs well with your order"} /></h2></div><button type="button" className={styles.skip} disabled={busy} onClick={()=>setDismissed(true)}><T text="No thanks" /></button></div>
 {target && <p className={styles.offer} role="status"><T text="Add" />{" "}{money(target.amountNeededForNextSlab!)}{" "}<T text="more to unlock this saving in eligible items —" />{" "}{target.name}<T text="’s next saving:" />{" "}{money(target.nextSlabRebateAmount!)}{" "}<T text="off. The convenience fee doesn’t count." /></p>}
 {compact&&<div className={styles.tabs} role="tablist" aria-label={translate("Complete your meal categories")}>{["Popular","Beverages","Sides"].map(name=><button id={`meal-tab-${name}`} role="tab" aria-selected={tab===name} aria-controls="meal-suggestions" type="button" key={name} onClick={()=>setTab(name)}>{translate(name)}</button>)}</div>}
 {compact&&loading&&<p className={styles.note} role="status"><T text="Finding additions for your pickup…"/></p>}
 {compact&&!loading&&!visible.length&&<p className={styles.note} role="status"><T text="No additions in this category fit your current pickup."/></p>}
 <div id={compact?"meal-suggestions":undefined} role={compact?"tabpanel":"region"} aria-labelledby={compact?`meal-tab-${tab}`:undefined} className={styles.grid} aria-label={translate("Suggested add-ons")} tabIndex={0}>{visible.map(s=><article className={styles.card} key={s.product.id}>{s.product.imageUrl?<Image unoptimized src={s.product.imageUrl} width={160} height={112} alt={s.product.name} className={styles.photo}/>:<div className={styles.fallback} aria-hidden="true">G</div>}<div className={styles.copy}><h3>{s.product.name}</h3><p>{translate(s.reason)}</p><strong>{money(s.portionTotal)}</strong><small>{s.weightGrams==null?translate("1 piece"):formatWeight(s.weightGrams)} <T text="· including item tax" /></small>{target&&s.portionTotal>=target.amountNeededForNextSlab!&&<p className={styles.reaches}><T text="Reaches this offer’s spend threshold" /></p>}</div><button type="button" className={styles.add} disabled={disabled||busy} onClick={()=>void add(s)} aria-label={`Add ${s.product.name}`}>{busy?translate("Checking…"):translate("+ Add")}</button></article>)}</div>
 <p className={styles.note}><T text="Optional additions at the menu price. Price and pickup availability are checked again before payment." /></p>{message&&<div><p role="status" className={styles.message}>{translate(message)}</p>{onAdjust&&<button type="button" className={styles.add} onClick={onAdjust} disabled={busy}><T text="Adjust quantities or pickup here" /></button>}</div>}</section>;
}
