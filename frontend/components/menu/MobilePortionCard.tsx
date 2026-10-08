"use client";
import {useState} from "react";
import ProductCard from "./ProductCard";
import MenuVariantPicker from "./MenuVariantPicker";
import {T} from "@/lib/language";
import type {PortionGroup} from "@/lib/mobileMenu";
import type {MenuProduct} from "@/types/menu";
import type {ProductRatingSummary} from "@/types/review";
import type {ItemAvailability} from "@/services/availabilityApi";
const money=(value:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR",minimumFractionDigits:0,maximumFractionDigits:2}).format(value);
export default function MobilePortionCard({group,products,quantities,onAdd,onIncrease,onDecrease,ratings,loading,pickupItems,dateAware,pickupChecking,pickupStatusMessage}:{group:PortionGroup;products:MenuProduct[];quantities:Record<number,number>;onAdd:(p:MenuProduct)=>void;onIncrease:(id:number)=>void;onDecrease:(id:number)=>void;ratings:Record<number,ProductRatingSummary>;loading:boolean;pickupItems?:ItemAvailability[];dateAware?:boolean;pickupChecking?:boolean;pickupStatusMessage?:string}){
 const [open,setOpen]=useState(false);
 const product=products.find(p=>p.imageUrl)??products[0];
 const count=products.reduce((sum,p)=>sum+(quantities[p.id]??0),0);
 const total=products.reduce((sum,p)=>sum+p.price*(quantities[p.id]??0),0);
 const labels=products.map(p=>group.choices.find(c=>c.productId===p.id)?.label??p.name);
 const catalogueAvailable=products.some(p=>p.available);
 const eligibleProducts=products.filter(p=>p.available&&(!dateAware||!pickupChecking&&pickupItems?.find(i=>i.productId===p.id)?.available===true));
 const availableSizes=eligibleProducts.length;
 const minimum=Math.min(...(eligibleProducts.length?eligibleProducts:products).map(p=>p.price));
 const unavailableForPickup=catalogueAvailable&&availableSizes===0;
 const status=!catalogueAvailable?"Currently unavailable":unavailableForPickup?pickupChecking?pickupStatusMessage??"Checking pickup":"Not at this time":`${availableSizes} ${availableSizes===1?"size":"sizes"} available`;
 const portion=labels.every(label=>/half|full|portion|हाफ|फुल/i.test(label));
 const rawNotes=dateAware&&!pickupChecking?products.flatMap(p=>{
  const item=pickupItems?.find(i=>i.productId===p.id);
  return p.available&&item?.available===false&&item.reason
   ? [{id:p.id,label:group.choices.find(c=>c.productId===p.id)?.label??p.name,reason:item.reason}] : [];
 }):[];
 const serviceNotes=Array.from(new Set(rawNotes.map(note=>note.reason))).map(reason=>({reason,labels:rawNotes.filter(note=>note.reason===reason).map(note=>note.label).join(" / ")}));
 return <div className="mobile-portion-card">
  <ProductCard refined premium priceFrom product={{...product,name:group.title,price:minimum,available:catalogueAvailable,description:product.description}} unavailableForPickup={unavailableForPickup} pickupMessage={pickupChecking?"Checking pickup":"Not at this time"} quantity={0} weightGrams={null} ratingSummary={ratings[product.id]??null} ratingLoading={loading} purchaseControl={<button data-ordering-target="add" type="button" className="product-card-controls menu-group-control" onClick={()=>setOpen(true)} disabled={availableSizes===0&&count===0} aria-label={`Choose options for ${group.title}`}><T text={count>0?"Manage":portion?"Add":"Choose size"}/>{count>0&&<span> · {count}</span>}</button>} onAdd={()=>setOpen(true)} onIncrease={onIncrease} onDecrease={onDecrease}/>
  <div className="menu-group-caption"><span>{count>0?`${count} · ${money(total)}`:<><T text="From"/> {money(minimum)}</>}</span><button type="button" disabled={availableSizes===0&&count===0} onClick={()=>setOpen(true)}><T text={count>0?"Manage sizes":labels.join(" / ")}/><span aria-hidden="true"> ›</span></button></div>
  {serviceNotes.map(note=><p key={note.reason} role="status" className="menu-availability-note"><strong>{note.labels}:</strong>{" "}<T text={note.reason}/></p>)}
  {!serviceNotes.length&&availableSizes===0&&<p role="status" className="menu-availability-note"><T text={status}/></p>}
  {open&&<MenuVariantPicker group={group} products={products} quantities={quantities} pickupItems={pickupItems} dateAware={dateAware} pickupChecking={pickupChecking} onAdd={onAdd} onIncrease={onIncrease} onDecrease={onDecrease} onClose={()=>setOpen(false)}/>}
 </div>;
}
