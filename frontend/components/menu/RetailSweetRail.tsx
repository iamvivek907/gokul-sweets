"use client";
import DietaryLabel from "./DietaryLabel";
import Image from "next/image";
import {T} from "@/lib/language";
import type {MenuProduct} from "@/types/menu";

/** A catalogue discovery rail; selecting a card opens its category before ordering. */
export default function RetailSweetRail({products,onBrowse}:{products:MenuProduct[];onBrowse:(id:number)=>void}){
 const picks=products.filter(p=>p.available&&/bakery|cake|pastr|sweet|mithai/i.test(p.categoryName)).slice(0,6);
 if(!picks.length)return null;
 const money=(p:MenuProduct)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR",minimumFractionDigits:0,maximumFractionDigits:2}).format(p.saleMode==="WEIGHT"?p.price*(p.minimumWeightGrams??250)/1000:p.price);
 return <section className="menu-sweet-rail"><header><h3><T text="For your sweet tooth"/></h3><button type="button" onClick={()=>onBrowse(picks[0].categoryId)}><T text="View all"/> ›</button></header><div>{picks.map(p=><button type="button" key={p.id} onClick={()=>onBrowse(p.categoryId)} aria-label={`Browse ${p.name}`}><span>{p.imageUrl&&<Image src={p.imageUrl} alt="" fill sizes="90px"/>}</span><span><DietaryLabel vegetarian={p.vegetarian}/><strong>{p.name}</strong><small>{p.saleMode==="WEIGHT"&&<T text="From"/>} {money(p)}</small></span></button>)}</div></section>;
}
