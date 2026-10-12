"use client";
import {T} from "@/lib/language";

/** One cart-aware control for recommendations, retail cards and SKU choices. */
export default function MenuPurchaseControl({name,quantity,label,addLabel,blocked=false,busy=false,onAdd,onDecrease,onManage}:{name:string;quantity:number;label?:string;addLabel?:string;blocked?:boolean;busy?:boolean;onAdd:()=>void;onDecrease:()=>void;onManage?:()=>void}){
 return <div className="menu-purchase-control" aria-live="polite">
  {quantity>0?<div role="group" aria-label={`${name} quantity: ${label??quantity}`}>
   <button type="button" aria-label={`Remove one ${name}`} onClick={onDecrease}>−</button>
   {onManage?<button type="button" className="menu-purchase-value" aria-label={`Manage ${name}. Currently ${label??quantity}`} onClick={onManage}>{label??quantity}</button>:<span>{label??quantity}</span>}
   <button type="button" disabled={blocked||busy} aria-label={`Add one more ${name}`} onClick={onAdd}>{busy?"…":"+"}</button>
  </div>:<button type="button" disabled={blocked||busy} aria-label={addLabel??`Add ${name} to cart`} onClick={onAdd}><T text={busy?"Checking…":"Add"}/></button>}
 </div>;
}
