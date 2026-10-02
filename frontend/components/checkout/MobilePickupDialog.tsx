"use client";

import {useEffect,useRef,useState} from "react";
import {T} from "@/lib/language";
import type {CartAvailability} from "@/services/availabilityApi";
import type {PickupSelection} from "@/types/pickup";

const dateLabel=(date:string)=>new Intl.DateTimeFormat("en-IN",{weekday:"short",day:"numeric",month:"short",timeZone:"Asia/Kolkata"}).format(new Date(`${date}T00:00:00+05:30`));
const money=(amount:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(amount);

export default function MobilePickupDialog({dates,options,chosen,disabled,onClose,onConfirm}:{
 dates:CartAvailability["dates"];options:PickupSelection[];chosen:PickupSelection|null;disabled:boolean;
 onClose:()=>void;onConfirm:(selection:PickupSelection)=>boolean;
}){
 const dialog=useRef<HTMLDialogElement>(null);
 const [date,setDate]=useState(chosen?.date??dates[0]?.date??"");
 const [selection,setSelection]=useState(chosen);
 const [error,setError]=useState("");
 const day=dates.find(day=>day.date===date);
 const times=options.filter(option=>option.date===date);
 const selected=times.find(option=>option.slot.id===selection?.slot.id&&option.pickupType===selection.pickupType);
 useEffect(()=>{const surface=dialog.current;if(!surface)return;const previousFocus=document.activeElement instanceof HTMLElement?document.activeElement:null;const previous=document.body.style.overflow;document.body.style.overflow="hidden";surface.showModal();return()=>{surface.close();document.body.style.overflow=previous;previousFocus?.focus();};},[]);
 return <dialog ref={dialog} className="mobile-pickup-dialog" aria-labelledby="mobile-pickup-title" onClick={event=>{const box=event.currentTarget.getBoundingClientRect();if(event.target===event.currentTarget&&(event.clientX<box.left||event.clientX>box.right||event.clientY<box.top||event.clientY>box.bottom))onClose();}} onCancel={event=>{event.preventDefault();onClose();}}>
  <div className="mobile-pickup-dialog-header"><h2 id="mobile-pickup-title"><T text="Choose pickup date & time" /></h2><button type="button" onClick={onClose} aria-label="Close pickup selector"><T text="Close" /> <span aria-hidden="true">×</span></button></div>
  <div className="mobile-pickup-dialog-body">
   <p><T text="Choose a date to see its available pickup times." /></p>
   <div className="mobile-pickup-dates" role="group" aria-label="Pickup dates">{dates.map(day=><button type="button" key={day.date} disabled={disabled} aria-label={day.date} aria-pressed={date===day.date} onClick={()=>{setDate(day.date);setSelection(null);setError("");}}><strong>{dateLabel(day.date)}</strong><small><T text={options.some(option=>option.date===day.date)?"Times available":"No matching time"} /></small></button>)}</div>
   <h3>{date?dateLabel(date):""} · <T text="Pickup times" /></h3>
   <div className="mobile-pickup-times gokul-pickup-times" role="group" aria-label="Pickup times">{times.map(option=><button className="gokul-pickup-time" type="button" key={`${option.slot.id}:${option.pickupType}`} disabled={disabled} aria-pressed={selected?.slot.id===option.slot.id&&selected.pickupType===option.pickupType} onClick={()=>{setSelection(option);setError("");}}><strong>{option.slot.startTime.slice(0,5)}–{option.slot.endTime.slice(0,5)}</strong><span>{option.pickupType==="NORMAL"?<T text="Standard" />:<><T text="Priority" /> · +{money(option.slot.priorityCharge)}</>}</span></button>)}</div>
   {!times.length&&<p role="status">{day?.reason??<T text="No matching times on this date. Try another date above." />}</p>}
   {error&&<p role="alert">{error}</p>}
  </div>
  <div className="mobile-pickup-dialog-actions"><button type="button" onClick={onClose}><T text="Cancel" /></button><button type="button" disabled={disabled||!selected} onClick={()=>{if(selected&&!onConfirm(selected))setError("That pickup time is no longer available. Choose another time.");}}><T text="Use this pickup" /></button></div>
 </dialog>;
}
