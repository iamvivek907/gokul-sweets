"use client";
import {T,useTranslation} from "@/lib/language";
import {rankAddOns} from "@/lib/addOnRanking";
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
export default function PickupAddOns({branchId,date,orderNumber,disabled,offers=[],onAdded,onBusy,onAdjust}:{branchId:number;date:string;orderNumber?:string;disabled:boolean;offers?:AvailableRebateResponse[];onAdded:()=>void|Promise<void>;onBusy?:(busy:boolean)=>void;onAdjust?:()=>void}) {
    const translate = useTranslation();
 const {items,addItem}=useCart();const locked=useRef(false);
 const [response,setResponse]=useState<{key:string;items:Suggestion[]}|null>(null);
 const [dismissed,setDismissed]=useState(false),[message,setMessage]=useState(""),[busy,setBusy]=useState(false);
 const request=JSON.stringify({serviceDate:date,items:items.map(i=>({productId:i.product.id,quantity:i.product.saleMode==="UNIT"?i.quantity:null,weightGrams:i.weightGrams}))});
 const path=`/api/menu/pickup-addons?branchId=${branchId}${orderNumber?`&orderNumber=${encodeURIComponent(orderNumber)}`:""}`;
 const key=`${path}:${request}`;
 useEffect(()=>{if(dismissed)return;const controller=new AbortController();apiClient<Suggestion[]>(path,{method:"POST",body:request,credentials:"include",signal:controller.signal}).then(items=>{if(!controller.signal.aborted)setResponse({key,items});}).catch(()=>{if(!controller.signal.aborted)setResponse({key,items:[]});});return()=>controller.abort();},[path,request,key,dismissed]);
 const target=usefulRebateTarget(offers);
 const suggestions=rankAddOns(response?.key===key ? response.items.filter(s=>!items.some(i=>i.product.id===s.product.id)) : [],target?.amountNeededForNextSlab ?? null);
 async function add(s:Suggestion) {
  if(disabled||locked.current)return;locked.current=true;setBusy(true);onBusy?.(true);setMessage("");let added=false;let addedSnapshot:string|null=null;
  const cart=getCartSnapshot(),branch=getStoredBranchSnapshot(),pickup=getPickupSlotSnapshot();
  try {const combined=JSON.parse(request);combined.items.push({productId:s.product.id,quantity:s.weightGrams==null?1:null,weightGrams:s.weightGrams});
   const check=await apiClient<{orderable:boolean}>(path.replace("pickup-addons?","pickup-addons/check?"),{method:"POST",body:JSON.stringify(combined),credentials:"include"});
   if(cart!==getCartSnapshot()||branch!==getStoredBranchSnapshot()||pickup!==getPickupSlotSnapshot()){setMessage("Your cart or pickup changed. Review it before adding.");return;}
   if(!check.orderable){setMessage("This addition no longer fits your pickup. Your cart is unchanged.");return;}
   if(addItem(s.product,branchId,s.weightGrams??undefined)!=="added"){setMessage("Choose the matching branch before adding.");return;}
   added=true;addedSnapshot=getCartSnapshot();await onAdded();setMessage(`${s.product.name} added. Review your updated price and offer before payment.`);
  } catch {if(added&&addedSnapshot===getCartSnapshot()){saveCart(parseCart(cart));added=false;}setMessage(added?"This item is in your cart. Review the cart to refresh its price before payment.":"We couldn’t complete this addition. Your existing cart and pickup are unchanged. Try again, or adjust quantities and pickup here.");}
  finally{locked.current=false;setBusy(false);onBusy?.(false);}
 }
 if(dismissed || (!suggestions.length&&!message&&!target))return null;
 return <section className={styles.panel} aria-label="Pickup add-ons"><div className={styles.heading}><div><p className={styles.eyebrow}><T text="A little extra for your pickup" /></p><h2><T text="Pairs well with your order" /></h2></div><button type="button" className={styles.skip} disabled={busy} onClick={()=>setDismissed(true)}><T text="No thanks" /></button></div>
 {target && <p className={styles.offer} role="status"><T text="Add" />{money(target.amountNeededForNextSlab!)} more to unlock this saving in eligible items — {target.name}’s next saving: {money(target.nextSlabRebateAmount!)} off. The convenience fee doesn’t count.</p>}
 <div className={styles.grid}>{suggestions.map(s=><article className={styles.card} key={s.product.id}>{s.product.imageUrl?<Image unoptimized src={s.product.imageUrl} width={160} height={112} alt={s.product.name} className={styles.photo}/>:<div className={styles.fallback} aria-hidden="true">G</div>}<div className={styles.copy}><h3>{s.product.name}</h3><p>{s.reason}</p><strong>{money(s.portionTotal)}</strong><small>{s.weightGrams==null?"1 piece":formatWeight(s.weightGrams)} <T text="· including item tax" /></small>{target&&s.portionTotal>=target.amountNeededForNextSlab!&&<p className={styles.reaches}>Reaches this offer’s spend threshold</p>}</div><button type="button" className={styles.add} disabled={disabled||busy} onClick={()=>void add(s)} aria-label={`Add ${s.product.name}`}>{busy?translate("Checking…"):"+ Add"}</button></article>)}</div>
 <p className={styles.note}><T text="Optional additions at the menu price. Price and pickup availability are checked again before payment." /></p>{message&&<div><p role="status" className={styles.message}>{translate(message)}</p>{onAdjust&&<button type="button" className={styles.add} onClick={onAdjust} disabled={busy}><T text="Adjust quantities or pickup here" /></button>}</div>}</section>;
}
