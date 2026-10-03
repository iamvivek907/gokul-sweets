"use client";
import {useEffect,useId,useRef,useState} from "react";
import {createPortal} from "react-dom";
import {useTranslation} from "@/lib/language";
import {adminFetch} from "@/services/adminApi";
import SwipeOrderAction from "./SwipeOrderAction";

export default function PickupHandoverAction({orderNumber,customerOrderNumber,authorization,disabled=false,late=false,onCompleted}:{orderNumber:string;customerOrderNumber?:number|null;authorization:string;disabled?:boolean;late?:boolean;onCompleted:()=>void|Promise<void>}) {
 const t=useTranslation(),id=useId(),dialog=useRef<HTMLDialogElement>(null),input=useRef<HTMLInputElement>(null),sending=useRef(false);
 const [open,setOpen]=useState(false),[code,setCode]=useState(""),[busy,setBusy]=useState(false),[error,setError]=useState("");
 useEffect(()=>{if(open){dialog.current?.showModal();input.current?.focus();}},[open]);
 async function confirm(){
  if(sending.current||disabled||!/^\d{4}$/.test(code))return;
  sending.current=true;setBusy(true);setError("");
  try {
   const response=await adminFetch(`/api/admin/orders/${encodeURIComponent(orderNumber)}/${late?"collect-late":"status"}`,authorization,{method:late?"POST":"PATCH",headers:{"Content-Type":"application/json"},body:JSON.stringify({status:"PICKED_UP",pickupCode:code})});
   if(!response.ok){const body=await response.json().catch(()=>null);throw new Error(body?.message??"Could not verify pickup. Try again.");}
  }catch(e){setError(e instanceof Error?e.message:"Could not verify pickup.");sending.current=false;setBusy(false);return;}
  setCode("");setOpen(false);sending.current=false;setBusy(false);void onCompleted();
 }
 return <><SwipeOrderAction action="pickup" label="Swipe to hand over" busy={busy} disabled={disabled} onComplete={()=>{setCode("");setError("");setOpen(true);}}/>{open&&createPortal(<dialog ref={dialog} aria-labelledby={id} onCancel={event=>{event.preventDefault();if(!sending.current)setOpen(false);}} className="fixed inset-0 m-auto w-[calc(100%_-_2rem)] max-w-sm rounded-3xl border border-[#c4d4c9] bg-[#fffaf2] p-6 text-[#143936] shadow-xl backdrop:bg-black/50"><h2 id={id} className="text-xl font-bold">{t("Verify pickup code")}</h2><p className="mt-3 break-all text-sm">{customerOrderNumber != null ? `Order #${customerOrderNumber}` : orderNumber}</p><p className="mt-3 text-sm">{t("Ask the customer for the four-digit code shown on their order. Confirm before handing over the food.")}</p><form onSubmit={event=>{event.preventDefault();void confirm();}}><label className="mt-4 block text-sm font-bold">{t("Pickup code")}<input ref={input} value={code} onChange={event=>{setCode(event.target.value.replace(/[^0-9]/g,"").slice(0,4));setError("");}} inputMode="numeric" autoComplete="off" pattern="[0-9]{4}" maxLength={4} disabled={busy} className="mt-2 block min-h-14 w-full rounded-xl border bg-white p-3 text-center text-2xl tracking-[.4em]" /></label>{error&&<p role="alert" className="mt-3 text-sm text-red-800">{t(error)}</p>}<div className="mt-5 flex gap-3"><button type="button" disabled={busy} onClick={()=>setOpen(false)} className="min-h-11 flex-1 rounded-xl border px-3">{t("Cancel")}</button><button type="submit" disabled={busy||disabled||!/^\d{4}$/.test(code)} className="min-h-11 flex-1 rounded-xl bg-[#143936] px-3 font-bold text-white disabled:opacity-50">{t(busy?"Verifying…":"Confirm pickup")}</button></div></form></dialog>,document.body)}</>;
}
