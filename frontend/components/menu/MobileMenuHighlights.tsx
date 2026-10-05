"use client";
import Image from "next/image";
import {useId} from "react";
import {T} from "@/lib/language";
import type {MenuProduct} from "@/types/menu";
import type {ProductRatingSummary} from "@/types/review";
import type {ItemAvailability} from "@/services/availabilityApi";

export default function MobileMenuHighlights({products,ratings,ratingsLoading=false,pickupItems,checking,availabilityError,onRetry,onAdd}:{products:MenuProduct[];ratings:Record<number,ProductRatingSummary>;ratingsLoading?:boolean;pickupItems?:ItemAvailability[];checking:boolean;availabilityError?:string|null;onRetry:()=>void;onAdd:(product:MenuProduct)=>void}){
 const statusId=useId();
 // Catalogue picks render immediately; real ratings refine their order independently.
 // Keep unavailable pickup candidates visible so late availability never shifts the menu.
 const picks=products.filter(p=>p.available).sort((a,b)=>(ratings[b.id]?.averageRating??0)-(ratings[a.id]?.averageRating??0)||(ratings[b.id]?.ratingCount??0)-(ratings[a.id]?.ratingCount??0)).slice(0,6);
 return <section className="mobile-menu-highlights" aria-label="Recommended menu items">
  <details open className="menu-category-disclosure">
   <summary className="gokul-menu-category-heading"><h2><T text="Recommended"/> <span>({picks.length})</span></h2><span className="menu-section-chevron" aria-hidden="true"/></summary>
   <p id={statusId} role="status" className="sr-only"><T text={availabilityError?"Retry availability":checking?"Checking favourites…":"From this branch’s menu"}/></p>
   {availabilityError&&<div role="alert"><p>{availabilityError}</p><button type="button" className="mobile-menu-suggestions-open" onClick={onRetry}><T text="Retry availability"/></button></div>}
   <div className="menu-recommended-grid">{picks.map(p=>{
    const rating=ratings[p.id],pickup=pickupItems?.find(i=>i.productId===p.id),unavailable=pickup?.available===false;
    const itemStatusId=`${statusId}-${p.id}`;
    return <article key={p.id}>
     <div className="mobile-menu-pairing-photo">{p.imageUrl?<Image src={p.imageUrl} alt={p.name} fill sizes="(max-width: 640px) 45vw, 180px"/>:<span aria-hidden="true">G</span>}{unavailable&&<p id={itemStatusId} className="menu-recommended-unavailable"><T text={pickup?.code==="QUANTITY_TOO_LARGE"?"Not enough for this portion":"Try another pickup date"}/></p>}</div>
     <h4>{p.name}</h4>
     <p className={`menu-recommended-rating${rating&&rating.ratingCount>0?" has-rating":""}`}>{rating&&rating.ratingCount>0?`★ ${rating.averageRating.toFixed(1)} (${rating.ratingCount})`:<T text={ratingsLoading?"Loading product rating":"New · No ratings yet"}/>}</p>
     <small>{p.saleMode==="WEIGHT"?`${p.minimumWeightGrams??250} g`:"1 piece"}</small>
     <div className="menu-recommended-purchase"><strong>₹{(p.saleMode==="WEIGHT"?p.price*(p.minimumWeightGrams??250)/1000:p.price).toLocaleString("en-IN",{maximumFractionDigits:2})}</strong><button type="button" disabled={checking||!!availabilityError||unavailable} aria-label={`Add ${p.name} from recommendations`} onClick={()=>onAdd(p)} aria-describedby={unavailable?`${statusId} ${itemStatusId}`:statusId}><T text="Add"/></button></div>
    </article>;
   })}</div>
   {!picks.length&&!availabilityError&&<p role="status"><T text="No items available right now"/></p>}
  </details>
 </section>;
}
