"use client";
import MenuDiscoverySheet from "./MenuDiscoverySheet";
import MenuPurchaseControl from "./MenuPurchaseControl";
import {T} from "@/lib/language";
import type {PortionGroup} from "@/lib/mobileMenu";
import type {MenuProduct} from "@/types/menu";
import type {ItemAvailability} from "@/services/availabilityApi";
const money=(value:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR",maximumFractionDigits:2}).format(value);
export default function MenuVariantPicker({group,products,quantities,pickupItems,dateAware,pickupChecking,busyId,error,onAdd,onIncrease,onDecrease,onClose}:{group:PortionGroup;products:MenuProduct[];quantities:Record<number,number>;pickupItems?:ItemAvailability[];dateAware?:boolean;pickupChecking?:boolean;busyId?:number|null;error?:string;onAdd:(p:MenuProduct)=>void;onIncrease:(id:number)=>void;onDecrease:(id:number)=>void;onClose:()=>void}){
 const count=products.reduce((sum,p)=>sum+(quantities[p.id]??0),0),total=products.reduce((sum,p)=>sum+p.price*(quantities[p.id]??0),0);
 return <MenuDiscoverySheet title={group.title} className="menu-variant-sheet" onClose={onClose}>
  <p><T text="You can add more than one size or portion."/></p>
  <div className="menu-variant-list">{products.map(p=>{const unavailable=!p.available||(!!dateAware&&(pickupChecking||pickupItems?.find(i=>i.productId===p.id)?.available!==true)),label=group.choices.find(c=>c.productId===p.id)?.label??p.name;return <article key={p.id}><div><h3>{label}</h3><strong>{money(p.price)}</strong>{unavailable&&<p role="status"><T text={!p.available?"Currently unavailable":"Unavailable for selected pickup"}/></p>}</div><MenuPurchaseControl name={`${group.title} ${label}`} quantity={quantities[p.id]??0} blocked={unavailable||(busyId!=null&&busyId!==p.id)} busy={busyId===p.id} onAdd={()=>quantities[p.id]>0?onIncrease(p.id):onAdd(p)} onDecrease={()=>onDecrease(p.id)}/></article>;})}</div>
  {error&&<p role="alert">{error}</p>}
  <footer><strong>{count} <T text="selected"/> · {money(total)}</strong><button type="button" onClick={onClose}><T text="Done"/></button></footer>
 </MenuDiscoverySheet>;
}
