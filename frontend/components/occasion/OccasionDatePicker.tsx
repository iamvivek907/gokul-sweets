"use client";
import {useId, useRef, useState} from "react";

export function addDays(value: string, days: number): string {
    const date=new Date(`${value}T12:00:00Z`);
    date.setUTCDate(date.getUTCDate()+days);
    return date.toISOString().slice(0,10);
}
export function prettyDate(value: string): string {
    return new Date(`${value}T12:00:00Z`).toLocaleDateString("en-IN",{weekday:"short",day:"numeric",month:"long",year:"numeric",timeZone:"Asia/Kolkata"});
}
export default function OccasionDatePicker({value,onChange,min,max,label="Date in India"}: {
    value:string;onChange:(value:string)=>void;min:string;max:string;label?:string;
}) {
    const id=useId(); const trigger=useRef<HTMLButtonElement>(null);
    const [open,setOpen]=useState(false); const [month,setMonth]=useState("");
    const shown=(month||value.slice(0,7)||min.slice(0,7));
    const first=new Date(`${shown}-01T12:00:00Z`);
    const days=new Date(Date.UTC(first.getUTCFullYear(),first.getUTCMonth()+1,0)).getUTCDate();
    const previous=new Date(Date.UTC(first.getUTCFullYear(),first.getUTCMonth()-1,1)).toISOString().slice(0,7);
    const next=new Date(Date.UTC(first.getUTCFullYear(),first.getUTCMonth()+1,1)).toISOString().slice(0,7);
    const valid=!!value && value>=min && value<=max;
    return <div className="relative"><p id={`${id}-label`} className="text-sm font-semibold">{label}</p>
        <button ref={trigger} type="button" aria-labelledby={`${id}-label ${id}-value`} aria-expanded={open} aria-controls={`${id}-calendar`}
            onClick={()=>{setMonth(valid?value.slice(0,7):min.slice(0,7));setOpen(!open);}}
            className="mt-2 flex min-h-12 w-full items-center justify-between gap-3 rounded-xl border bg-white p-3 text-left">
            <span id={`${id}-value`}>{valid?prettyDate(value):"Choose your celebration date"}</span><span aria-hidden="true">▦</span>
        </button><p className="mt-2 text-xs">Earliest available: {prettyDate(min)} · India time</p>
        {value&&!valid&&<p role="alert" className="mt-2 text-sm text-red-800">Choose a later date to allow preparation for your selection.</p>}
        {open&&<section id={`${id}-calendar`} aria-label="Choose occasion date" className="mt-2 rounded-2xl border bg-white p-4 shadow-lg"
            onKeyDown={event=>{if(event.key==="Escape"){setOpen(false);trigger.current?.focus();}}}>
            <div className="mb-3 flex items-center justify-between"><button type="button" aria-label="Previous month" disabled={previous<min.slice(0,7)} onClick={()=>setMonth(previous)} className="min-h-11 rounded-lg border px-3 disabled:opacity-30">←</button>
                <strong aria-live="polite">{first.toLocaleDateString("en-IN",{month:"long",year:"numeric",timeZone:"UTC"})}</strong>
                <button type="button" aria-label="Next month" disabled={next>max.slice(0,7)} onClick={()=>setMonth(next)} className="min-h-11 rounded-lg border px-3 disabled:opacity-30">→</button></div>
            <div className="grid grid-cols-7 gap-1 text-center text-xs">{["Su","Mo","Tu","We","Th","Fr","Sa"].map(day=><span key={day} className="py-2">{day}</span>)}</div>
            <div className="grid grid-cols-7 gap-1">{Array.from({length:first.getUTCDay()},(_,index)=><span key={`space-${index}`} />)}
                {Array.from({length:days},(_,index)=>{const date=`${shown}-${String(index+1).padStart(2,"0")}`;return <button key={date} type="button" disabled={date<min||date>max}
                    aria-label={prettyDate(date)} aria-pressed={date===value} onClick={()=>{onChange(date);setOpen(false);trigger.current?.focus();}}
                    className={`min-h-11 rounded-lg text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-25 ${date===value?"bg-[#143936] text-white":"hover:bg-[#fff0dc]"}`}>{index+1}</button>;})}
            </div><button type="button" onClick={()=>{setOpen(false);trigger.current?.focus();}} className="mt-3 min-h-11 w-full rounded-lg border">Close calendar</button>
        </section>}
    </div>;
}
