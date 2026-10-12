"use client";
import Image from "next/image";
import {T} from "@/lib/language";
import type {MenuProduct} from "@/types/menu";

/** Editorial discovery, using real catalogue imagery instead of duplicate Add cards. */
export default function MobileMenuHighlights({products,retail=false,onBrowse}:{products:MenuProduct[];retail?:boolean;onBrowse:(id:number)=>void}){
 const sweet=retail?undefined:products.find(p=>p.available&&p.imageUrl&&/sweet|mithai|मिठाई/i.test(p.categoryName));
 const feature=sweet??products.find(p=>p.available&&p.imageUrl&&(!retail||/snack|dairy|drink|beverage|biscuit|chips|namkeen|नमकीन|पेय/i.test(p.categoryName)));
 if(!feature)return null;
 return <section className={`mobile-menu-highlights menu-editorial-feature${sweet?"":" menu-editorial-feature--mint"}`} aria-label="Recommended menu items">
  <div><h2><T text={sweet?"Made fresh.":"Little extras."}/><br/><T text={sweet?"Loved daily.":"Big smiles."}/></h2><button type="button" onClick={()=>onBrowse(feature.categoryId)}>{sweet?<T text="Explore sweets"/>:<T text="Chips, cookies, namkeen and more."/>} <span aria-hidden="true">→</span></button></div>
  <div className="menu-feature-photo"><Image src={sweet?"/menu-sweets-banner.webp":"/menu-snacks-banner.webp"} alt="" fill sizes="(max-width: 640px) 100vw, 400px" priority/></div>
 </section>;
}
