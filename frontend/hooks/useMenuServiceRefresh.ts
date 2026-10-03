"use client";
import {useEffect, type Dispatch, type SetStateAction} from "react";
import {stampMenuServiceAvailability} from "@/services/menuApi";
import {apiClient} from "@/services/apiClient";
import type {MenuCategory} from "@/types/menu";

/** Refresh server decisions at the next IST service boundary; never trust the device's hour. */
export function useMenuServiceRefresh(branchId:number|undefined,categories:MenuCategory[],setCategories:Dispatch<SetStateAction<MenuCategory[]>>) {
 const boundaries=categories.flatMap(c=>c.products).flatMap(p=>p.serviceAvailability?.nextChangeAt?[Date.parse(p.serviceAvailability.nextChangeAt)]:[]);
 const boundary=boundaries.length?Math.min(...boundaries):null;
 const metadata=categories.flatMap(c=>c.products).find(p=>p.serviceAvailability?.evaluatedAt)?.serviceAvailability;
 const evaluated=metadata?.evaluatedAt??null,received=metadata?.receivedMonotonic??null;
 useEffect(()=>{
  if(!branchId)return;
  const controller=new AbortController();let busy=false;
  const serverStart=evaluated?Date.parse(evaluated):Date.now(),started=received??performance.now();
  const elapsed=()=>performance.now()-started;
  const expired=()=>boundary!==null&&boundary<=serverStart+elapsed();
  async function refresh(expired=false){
   if(controller.signal.aborted)return;
   if(expired)setCategories(current=>current.map(c=>({...c,products:c.products.map(p=>p.serviceAvailability?.nextChangeAt&&Date.parse(p.serviceAvailability.nextChangeAt)<=serverStart+elapsed()?{...p,available:false,serviceAvailability:{...p.serviceAvailability,code:"CHECKING",message:"Checking current availability…"}}:p)})));
   if(busy||!navigator.onLine||document.visibilityState!=="visible")return;
   busy=true;
   try{const fresh=await apiClient<MenuCategory[]>(`/api/menu?branchId=${branchId}`,{signal:controller.signal,cache:"no-store"});if(!controller.signal.aborted)setCategories(stampMenuServiceAvailability(fresh));}catch{/* A failed refresh keeps cart and last menu; expired items remain blocked. */}finally{busy=false;}
  }
  const timer=window.setInterval(()=>void refresh(expired()),30000);
  const transition=boundary===null?null:window.setTimeout(()=>void refresh(true),Math.max(0,boundary-serverStart-elapsed()+100));
  const resume=()=>void refresh(expired());
  window.addEventListener("online",resume);document.addEventListener("visibilitychange",resume);
  return()=>{controller.abort();clearInterval(timer);if(transition!==null)clearTimeout(transition);window.removeEventListener("online",resume);document.removeEventListener("visibilitychange",resume);};
 },[branchId,boundary,evaluated,received,setCategories]);
}
