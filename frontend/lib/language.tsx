"use client";
import {useCallback,useEffect,useId,useRef,useState,useSyncExternalStore} from "react";
import {hindi} from "./hindi";
export type Language="en"|"hi";
const KEY="gokul-language";
let language:Language="en";
const listeners=new Set<()=>void>();
function subscribe(callback:()=>void){listeners.add(callback);return()=>{listeners.delete(callback);};}
function snapshot(){return language;}
function serverSnapshot():Language{return "en";}
export function setLanguage(value:Language){language=value;try{localStorage.setItem(KEY,value);}catch{/* Private browsing still supports a session choice. */}document.documentElement.lang=value;listeners.forEach(callback=>callback());}
export function useLanguage(){return useSyncExternalStore(subscribe,snapshot,serverSnapshot);}
export function translate(text:string|null|undefined,locale:Language="en"):string{
 const value=text??"";if(locale!=="hi")return value;
 if(hindi[value])return hindi[value];
 // Product names remain the shop's own names; localize the surrounding action copy.
 if(value.startsWith("Often ordered with "))return `${value.slice("Often ordered with ".length)} के साथ खूब पसंद किया जाता है`;
 const added=value.match(/^(.+) added\. Review your updated price and offer before payment\.$/);
 if(added)return `${added[1]} जोड़ दिया गया है। भुगतान से पहले नई राशि और छूट देख लें।`;
 const batch=value.match(/^(\d+) started · (\d+) skipped\.([\s\S]*)$/);
 if(batch)return `${batch[1]} ऑर्डर की तैयारी शुरू हुई · ${batch[2]} ऑर्डर छोड़ दिए गए।${batch[3]}`;
 return value;
}
export function useTranslation(){const locale=useLanguage();return useCallback((text:string|null|undefined)=>translate(text,locale),[locale]);}
export function T({text}:{text:string}){return <>{translate(text,useLanguage())}</>;}
export function LanguageRuntime(){useEffect(()=>{const restore=()=>{let saved:Language="en";try{if(localStorage.getItem(KEY)==="hi")saved="hi";}catch{}language=saved;document.documentElement.lang=saved;listeners.forEach(callback=>callback());};restore();window.addEventListener("storage",restore);return()=>window.removeEventListener("storage",restore);},[]);return null;}
export function LanguagePicker(){
 const locale=useLanguage(),[open,setOpen]=useState(false),optionsId=useId();const container=useRef<HTMLDivElement>(null),trigger=useRef<HTMLButtonElement>(null);
 useEffect(()=>{if(!open)return;container.current?.querySelector<HTMLButtonElement>('.language-popover button[aria-pressed="true"]')?.focus();const close=(event:PointerEvent)=>{if(!container.current?.contains(event.target as Node))setOpen(false);};const escape=(event:KeyboardEvent)=>{if(event.key==="Escape"){setOpen(false);trigger.current?.focus();}};document.addEventListener("pointerdown",close);document.addEventListener("keydown",escape);return()=>{document.removeEventListener("pointerdown",close);document.removeEventListener("keydown",escape);};},[open]);
 return <div ref={container} className="language-control"><button ref={trigger} type="button" aria-label="Language / भाषा" aria-expanded={open} aria-controls={open?optionsId:undefined} onClick={()=>setOpen(!open)} className="language-trigger"><span aria-hidden="true">अ/A</span><span>{locale==="hi"?"हिन्दी":"EN"}</span></button>{open&&<div id={optionsId} className="language-popover" role="group" aria-label="Language / भाषा"><strong>Choose language · भाषा चुनें</strong>{([['en','English','Order with ease'],['hi','हिन्दी','अपनी भाषा में ऑर्डर करें']] as const).map(([code,label,hint])=><button key={code} type="button" aria-pressed={locale===code} onClick={()=>{setLanguage(code);setOpen(false);trigger.current?.focus();}}><span><b>{label}</b><small>{hint}</small></span><span aria-hidden="true">{locale===code?'✓':'→'}</span></button>)}</div>}</div>;
}
