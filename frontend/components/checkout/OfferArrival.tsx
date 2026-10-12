"use client";
import {useEffect,useRef,useState} from "react";
import {offerArrivalContext,takeOfferArrival} from "@/lib/offerArrival";
import {T} from "@/lib/language";
export default function OfferArrival({ready,amount,name,code,failed,disabled}:{ready:boolean;amount:number;name?:string;code?:string;failed:boolean;disabled:boolean}){
 const [phase,setPhase]=useState<"waiting"|"celebrate"|"closed">("waiting");
 const [celebratedContext,setCelebratedContext]=useState<string|null>(null);
 const request=useRef<ReturnType<typeof takeOfferArrival>|undefined>(undefined),dialog=useRef<HTMLDialogElement>(null);
 const context=offerArrivalContext();
 const valid=ready&&Number.isFinite(amount)&&amount>0&&!failed&&!disabled;
 const visible=phase==="celebrate"&&valid&&celebratedContext===context;
 useEffect(()=>{
  let active=true;
  if(request.current===undefined)request.current=takeOfferArrival();
  if(!request.current)queueMicrotask(()=>{if(active)setPhase("closed");});
  const timeout=setTimeout(()=>setPhase("closed"),30000);
  return()=>{active=false;clearTimeout(timeout);};
 },[]);
 useEffect(()=>{
  if(phase==="closed")return;
  let active=true;
  const invalid=disabled||failed||request.current?.context!==context;
  if(invalid||phase==="celebrate"&&!valid||phase==="waiting"&&ready)queueMicrotask(()=>{if(active){setCelebratedContext(!invalid&&valid?context:null);setPhase(!invalid&&valid?"celebrate":"closed");}});
  return()=>{active=false;};
 },[phase,disabled,failed,ready,valid,context]);
 useEffect(()=>{if(phase!=="celebrate")return;const timer=setTimeout(()=>setPhase("closed"),1400);return()=>clearTimeout(timer);},[phase]);
 useEffect(()=>{
  const d=dialog.current;if(!d||!visible)return;
  const previous=document.activeElement as HTMLElement|null;
  d.showModal();
  return()=>{d.close();previous?.focus({preventScroll:true});};
 },[visible]);
 return <dialog ref={dialog} className="offer-arrival offer-arrival-celebrate" aria-labelledby="offer-arrival-title" onCancel={e=>{e.preventDefault();setPhase("closed");}}>
  {visible&&<div className="offer-confetti" aria-hidden="true">{Array.from({length:24},(_,i)=><i key={i} style={{"--piece":i,"--origin":i%2?"100%":"0%","--travel-x":`${(i%2?-1:1)*(70+(i*37)%240)}px`,"--travel-y":`${-100-(i*23)%170}px`} as React.CSSProperties}/>)}</div>}
  <span className="offer-arrival-symbol" aria-hidden="true">✓</span><h2 id="offer-arrival-title"><T text={`${code??name??"Offer"} applied`}/></h2>
  <strong><T text="You saved"/> {new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(amount)}</strong><p>{name}</p><p><T text="Your offer is included in the checkout total."/></p>
  <button type="button" autoFocus onClick={()=>setPhase("closed")}><T text="Woohoo! Thanks"/></button>
 </dialog>;
}
