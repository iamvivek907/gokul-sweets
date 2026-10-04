"use client";
import dynamic from "next/dynamic";
import {useEffect,useRef,useState} from "react";
import {savePickupSlot,getPickupSlotSnapshot,clearPickupSlot} from "@/lib/checkoutStorage";
import {savePickupIntent} from "@/hooks/usePickupIntent";
import {getStoredBranchSnapshot} from "@/lib/branchStorage";
import {checkCartAvailability,type CartAvailability} from "@/services/availabilityApi";
import {menuPickupOptions} from "@/lib/menuPickupOptions";
import {T} from "@/lib/language";
import type {MenuProduct} from "@/types/menu";
import type {PickupSelection} from "@/types/pickup";
const Dialog=dynamic(()=>import("@/components/checkout/MobilePickupDialog"));
export default function MobileMenuPickup({branchId,products,today,days,selection,expired}:{branchId:number;products:MenuProduct[];today:string;days:number;selection:PickupSelection|null;expired:boolean}){
 const [data,setData]=useState<CartAvailability|null>(null),[open,setOpen]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState("");
 const request=JSON.stringify(products.filter(p=>p.available).slice(0,100).map(p=>({productId:p.id,quantity:p.saleMode==="UNIT"?1:null,weightGrams:p.saleMode==="WEIGHT"?p.minimumWeightGrams??250:null})));
 const controller=useRef<AbortController|null>(null);
 useEffect(()=>{
  let active=true;
  queueMicrotask(()=>{if(active){setBusy(false);setOpen(false);setData(null);setError("");}});
  return()=>{active=false;controller.current?.abort();};
 },[branchId,request]);
 async function choose(){
  if(busy)return;
  setBusy(true);setError("");const c=new AbortController();controller.current=c;
  try{const value=await checkCartAvailability(branchId,today,days+1,JSON.parse(request),AbortSignal.any([c.signal,AbortSignal.timeout(15000)]));if(!c.signal.aborted){setData(value);setOpen(true);}}
  catch{if(!c.signal.aborted)setError("We couldn’t load pickup times. Your cart is saved. Try again.");}
  finally{if(!c.signal.aborted)setBusy(false);}
 }
 const ids=JSON.parse(request).map((item:{productId:number})=>item.productId);
 return <section className="mobile-menu-pickup" aria-label="Menu pickup time"><div><span><T text="PICKUP TIME"/></span><strong>{selection?`${selection.slot.startTime.slice(0,5)}–${selection.slot.endTime.slice(0,5)} IST · ${selection.date}`:<T text="Find a time for your favourites"/>}</strong></div><button type="button" disabled={busy||!ids.length} onClick={()=>void choose()}><T text={busy?"Checking times…":selection?"Change time":"Choose time"}/></button>
 {expired&&<p role="status"><T text="Your previous pickup has passed. Choose a new time; your cart is saved."/></p>}
 {!expired&&selection&&<p><T text="Some items may need another time. Change time to explore availability."/></p>}
 {error&&<p role="alert">{error}</p>}
 {open&&data&&<Dialog dates={data.dates} options={menuPickupOptions(data,ids)} chosen={selection??menuPickupOptions(data,ids)[0]??null} disabled={false} onClose={()=>{controller.current?.abort();setOpen(false);}} onConfirm={async value=>{
  const branch=getStoredBranchSnapshot(),pickup=getPickupSlotSnapshot();const c=new AbortController();controller.current=c;
  const fresh=await checkCartAvailability(branchId,value.date,1,JSON.parse(request),AbortSignal.any([c.signal,AbortSignal.timeout(15000)]));
  if(c.signal.aborted||branch!==getStoredBranchSnapshot()||pickup!==getPickupSlotSnapshot())return false;
  const checked=menuPickupOptions(fresh,ids).find(s=>s.slot.id===value.slot.id&&s.date===value.date&&s.pickupType===value.pickupType);
  if(!checked){setData(previous=>previous?{...previous,dates:previous.dates.map(d=>fresh.dates.find(f=>f.date===d.date)??d)}:fresh);return false;}
  clearPickupSlot();savePickupIntent(branchId,checked.date);savePickupSlot(checked);setOpen(false);return true;
 }}/>}</section>;
}
