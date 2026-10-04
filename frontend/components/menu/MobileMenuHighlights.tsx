"use client";
import Image from "next/image";
import {T} from "@/lib/language";
import type {MenuProduct} from "@/types/menu";
import type {ProductRatingSummary} from "@/types/review";
import type {ItemAvailability} from "@/services/availabilityApi";
export default function MobileMenuHighlights({products,ratings,pickupItems,checking,availabilityError,onRetry,onAdd}:{products:MenuProduct[];ratings:Record<number,ProductRatingSummary>;pickupItems?:ItemAvailability[];checking:boolean;availabilityError?:string|null;onRetry:()=>void;onAdd:(product:MenuProduct)=>void}){
 const picks=products.filter(p=>p.available&&(ratings[p.id]?.ratingCount??0)>0&&pickupItems?.find(i=>i.productId===p.id)?.available!==false).sort((a,b)=>ratings[b.id].averageRating-ratings[a.id].averageRating||ratings[b.id].ratingCount-ratings[a.id].ratingCount).slice(0,5);
 if(!picks.length)return null;
 return <section className="mobile-menu-highlights" aria-label="Customer-rated favourites"><header><span><T text="DISCOVER SOMETHING GOOD"/></span><h2><T text="Customer-rated favourites"/></h2></header>{availabilityError&&<div role="alert"><p>{availabilityError}</p><button type="button" onClick={onRetry}><T text="Retry availability"/></button></div>}<div className="mobile-menu-pairing-row">{picks.map(p=><article key={p.id}><div className="mobile-menu-pairing-photo">{p.imageUrl?<Image src={p.imageUrl} alt={p.name} fill sizes="144px"/>:<span aria-hidden="true">G</span>}<button type="button" disabled={checking||!!availabilityError} aria-label={`Add ${p.name} from favourites`} onClick={()=>onAdd(p)}><T text="Add"/> +</button></div><h4>{p.name}</h4><strong>₹{(p.saleMode==="WEIGHT"?p.price*(p.minimumWeightGrams??250)/1000:p.price).toLocaleString("en-IN",{maximumFractionDigits:2})}</strong><small>{p.saleMode==="WEIGHT"?`${p.minimumWeightGrams??250} g`:"1 piece"}</small><p>★ {ratings[p.id].averageRating.toFixed(1)} · {ratings[p.id].ratingCount} <T text="reviews"/></p></article>)}</div></section>;
}
