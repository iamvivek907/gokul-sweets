"use client";
import {useState} from 'react';
import ProductCard from './ProductCard';
import type {PortionGroup} from '@/lib/mobileMenu';
import type {MenuProduct} from '@/types/menu';
import type {ProductRatingSummary} from '@/types/review';
import type {ItemAvailability} from '@/services/availabilityApi';
export default function MobilePortionCard({group,products,quantities,onAdd,onIncrease,onDecrease,ratings,loading,pickupItems,dateAware}:{group:PortionGroup;products:MenuProduct[];quantities:Record<number,number>;onAdd:(p:MenuProduct)=>void;onIncrease:(id:number)=>void;onDecrease:(id:number)=>void;ratings:Record<number,ProductRatingSummary>;loading:boolean;pickupItems?:ItemAvailability[];dateAware?:boolean}){
 const [choice,setChoice]=useState<number|null>(null);
 const product=products.find(p=>p.id===choice)??products.find(p=>quantities[p.id]>0)??products.find(p=>p.available&&(!dateAware||pickupItems?.find(i=>i.productId===p.id)?.available!==false))??products[0];
 const blocked=!!dateAware&&pickupItems?.find(p=>p.productId===product.id)?.available===false;
 return <div className="mobile-portion-card"><ProductCard refined product={{...product,name:group.title}} quantity={quantities[product.id]??0} weightGrams={null} ratingSummary={ratings[product.id]??null} ratingLoading={loading} unavailableForPickup={blocked} onAdd={()=>onAdd(product)} onIncrease={onIncrease} onDecrease={onDecrease} portionOptions={<fieldset className="mobile-portion-choices"><legend className="sr-only">Portion for {group.title}</legend>{products.map(p=>{const unavailable=!p.available||(dateAware&&pickupItems?.find(i=>i.productId===p.id)?.available===false);return <label key={p.id} className={p.id===product.id?'selected':''}><input type="radio" name={`portion-${group.key}`} checked={p.id===product.id} disabled={unavailable} onChange={()=>setChoice(p.id)}/><span>{group.choices.find(c=>c.productId===p.id)?.label} · ₹{p.price.toFixed(2)}{quantities[p.id]>0?` (${quantities[p.id]})`:''}{unavailable?' · Unavailable':''}</span></label>;})}</fieldset>}/></div>;
}
