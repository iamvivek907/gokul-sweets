"use client";
import {useEffect,useSyncExternalStore} from "react";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
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
export function translate(text:string|null|undefined,locale:Language=language):string{const value=text??"";return locale==="hi"?hindi[value]??value:value;}
export function T({text}:{text:string}){return <>{" "}{translate(text,useLanguage())}{" "}</>;}
export function LanguageRuntime(){const enabled=useStorefrontFeatures()?.bilingualStorefront===true;useEffect(()=>{const restore=()=>{let saved:Language="en";try{if(enabled&&localStorage.getItem(KEY)==="hi")saved="hi";}catch{}language=saved;document.documentElement.lang=saved;listeners.forEach(callback=>callback());};restore();window.addEventListener("storage",restore);return()=>window.removeEventListener("storage",restore);},[enabled]);return null;}
export function LanguagePicker(){const locale=useLanguage();return <label className="inline-flex min-h-11 shrink-0 items-center gap-1 rounded-full border border-[#c4d4c9] bg-[#fffaf2] px-2 text-sm font-semibold text-[#173c39]"><span aria-hidden="true">अ/A</span><select aria-label="Language / भाषा" value={locale} onChange={event=>setLanguage(event.target.value as Language)} className="min-h-11 max-w-[90px] bg-transparent"><option value="en">English</option><option value="hi">हिन्दी</option></select></label>;}
