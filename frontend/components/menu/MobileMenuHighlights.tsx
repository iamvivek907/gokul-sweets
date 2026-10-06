"use client";
import Image from "next/image";
import {useState} from "react";
import {T} from "@/lib/language";
import type {MenuProduct} from "@/types/menu";

/** Editorial discovery, using real catalogue imagery instead of duplicate Add cards. */
export default function MobileMenuHighlights({products}:{products:MenuProduct[]}){
 const [failed,setFailed]=useState<string|null>(null);
 const sweet=products.find(p=>p.available&&p.imageUrl&&/sweet|mithai|मिठाई/i.test(p.categoryName));
 const feature=sweet??products.find(p=>p.available&&p.imageUrl);
 if(!feature)return null;
 return <section className={`mobile-menu-highlights menu-editorial-feature${sweet?"":" menu-editorial-feature--mint"}`} aria-label="Recommended menu items">
  <div><h2><T text={sweet?"Made fresh.":"Little extras."}/><br/><T text={sweet?"Loved daily.":"Big smiles."}/></h2><button type="button" onClick={()=>{const section=document.getElementById(`menu-category-${feature.categoryId}`);const details=section?.querySelector("details");if(details)details.open=true;section?.scrollIntoView({block:"start",behavior:matchMedia("(prefers-reduced-motion: reduce)").matches?"instant":"smooth"});}}>{sweet?<T text="Explore sweets"/>:feature.categoryName} <span aria-hidden="true">→</span></button></div>
  <div className="menu-feature-photo">{failed!==feature.imageUrl?<Image src={feature.imageUrl!} alt={feature.name} fill sizes="(max-width: 640px) 45vw, 180px" onError={()=>setFailed(feature.imageUrl)}/>:<span aria-hidden="true">G</span>}</div>
 </section>;
}
