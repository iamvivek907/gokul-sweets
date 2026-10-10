"use client";

import {useEffect,useRef,useState} from "react";
import {T,translate,useLanguage} from "@/lib/language";
import {indiaToday} from "@/lib/pickupFreshness";
import type {CartAvailability} from "@/services/availabilityApi";
import type {PickupSelection} from "@/types/pickup";

const dateLabel=(date:string,locale:"en"|"hi",today:string)=>{const named=new Intl.DateTimeFormat(locale==="hi"?"hi-IN":"en-IN",{weekday:"short",day:"numeric",month:"short",year:"numeric",timeZone:"Asia/Kolkata"}).format(new Date(`${date}T12:00:00+05:30`));const tomorrow=indiaToday(new Date(Date.parse(`${today}T12:00:00+05:30`)+86400000));return `${date===today?`${translate("Today",locale)} · `:date===tomorrow?`${translate("Tomorrow",locale)} · `:""}${named}`;};
const money=(amount:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(amount);

export default function MobilePickupDialog({dates,options,chosen,disabled,onClose,onConfirm,advisory=false,initialDate,restoreScroll,restoreFocus,today=indiaToday()}:{
 restoreFocus?:HTMLElement|null;restoreScroll?:{left:number;top:number};today?:string;initialDate?:string;advisory?:boolean;dates:CartAvailability["dates"];options:PickupSelection[];chosen:PickupSelection|null;disabled:boolean;
 onClose:()=>void;onConfirm:(selection:PickupSelection)=>Promise<boolean>;
}){
 const locale=useLanguage();
 const initialScroll=useRef(restoreScroll);
 const initialFocus=useRef(restoreFocus);
 const dialog=useRef<HTMLDialogElement>(null);
 const [date,setDate]=useState(()=>{
  const preferred=chosen?.date??initialDate;
  return advisory&&!options.some(option=>option.date===preferred)?options[0]?.date??preferred??dates[0]?.date??"":preferred??dates[0]?.date??"";
 });
 const [selection,setSelection]=useState(chosen);
 const mounted=useRef(true);
 const [checking,setChecking]=useState(false);
 const [error,setError]=useState("");
 const day=dates.find(day=>day.date===date);
 const times=options.filter(option=>option.date===date);
 const selected=times.find(option=>option.slot.id===selection?.slot.id&&option.pickupType===selection.pickupType);
 useEffect(()=>{
  mounted.current=true;
  const surface=dialog.current;if(!surface)return;
  // Capture the actual opener before its asynchronous load disables it and loses focus.
  const previousFocus=initialFocus.current??(document.activeElement instanceof HTMLElement?document.activeElement:null);
  const pageUrl=location.href;
  const scroll=initialScroll.current??{left:window.scrollX,top:window.scrollY};
  const lock=document.documentElement,previous=lock.style.overflow;
  lock.style.overflow="hidden";surface.showModal();window.scrollTo({...scroll,behavior:"instant"});
  return()=>{
   mounted.current=false;surface.close();lock.style.overflow=previous;
   if(location.href!==pageUrl)return;
   window.scrollTo({...scroll,behavior:"instant"});
   const openerAvailable=previousFocus?.isConnected&&!previousFocus.closest("[inert]")&&getComputedStyle(previousFocus).visibility!=="hidden";
   const target=openerAvailable?previousFocus:document.querySelector<HTMLElement>(".mobile-menu-pickup > button");
   target?.focus({preventScroll:true});
  };
 },[]);
 return <dialog ref={dialog} className="mobile-pickup-dialog" aria-labelledby="mobile-pickup-title" onClick={event=>{const box=event.currentTarget.getBoundingClientRect();if(event.target===event.currentTarget&&(event.clientX<box.left||event.clientX>box.right||event.clientY<box.top||event.clientY>box.bottom))onClose();}} onCancel={event=>{event.preventDefault();onClose();}}>
  <div className="mobile-pickup-dialog-header"><h2 id="mobile-pickup-title"><T text="Choose pickup date & time" /></h2><button type="button" onClick={onClose} aria-label="Close pickup selector"><T text="Close" /> <span aria-hidden="true">×</span></button></div>
  <div className="mobile-pickup-dialog-body">
   <p><T text={advisory?"Choose a time, then confirm. Your cart is kept.":"Choose a date, then tap a pickup time. Nothing changes until you confirm."} /></p>
   <div className="mobile-pickup-dates" role="group" aria-label="Pickup dates">{dates.map(day=><button type="button" key={day.date} disabled={disabled||checking} aria-label={day.date} aria-pressed={date===day.date} onClick={()=>{setDate(day.date);setSelection(null);setError("");}}><strong>{dateLabel(day.date,locale,today)}</strong><small><T text={options.some(option=>option.date===day.date)?"Times available":"No matching time"} /></small></button>)}</div>
   <h3>{date?dateLabel(date,locale,today):""} · <T text="Pickup times" /></h3>
   <div className="mobile-pickup-times gokul-pickup-times" role="group" aria-label="Pickup times">{times.map(option=><button className="gokul-pickup-time" type="button" key={`${option.slot.id}:${option.pickupType}`} disabled={disabled||checking} aria-pressed={selected?.slot.id===option.slot.id&&selected.pickupType===option.pickupType} onClick={()=>{setSelection(option);setError("");}}><strong>{option.slot.startTime.slice(0,5)}–{option.slot.endTime.slice(0,5)}</strong><span>{option.pickupType==="NORMAL"?<T text="Standard" />:<><T text="Priority" /> · +{money(option.slot.priorityCharge)}</>}</span></button>)}</div>
   {!times.length&&<p role="status">{day?.reason??<T text="No pickup times available for these items on this date. Try another date or remove items." />}</p>}
   {error&&<p role="alert">{error}</p>}
  </div>
  <div className="mobile-pickup-dialog-actions"><button type="button" onClick={onClose}><T text="Cancel" /></button><button type="button" disabled={disabled||checking||!selected} onClick={async()=>{if(!selected||checking)return;setChecking(true);setError("");try{const confirmed=await onConfirm(selected);if(!confirmed&&mounted.current)setError("That pickup time is no longer available. Choose another time.");}catch(error){if(mounted.current)setError(error instanceof Error?error.message:"We couldn’t confirm pickup. Please try again.");}finally{if(mounted.current)setChecking(false);}}}><T text={checking?"Checking pickup…":"Use this pickup"} /></button></div>
 </dialog>;
}
