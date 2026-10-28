"use client";
import {useSyncExternalStore,useMemo,useCallback} from "react";
import {favouriteKey,favouriteIds,toggleFavourites,clearFavourites,MENU_PREFERENCE_EVENT} from "@/lib/menuPreferences";
const read=()=>{try{return localStorage.getItem(favouriteKey)??"";}catch{return "";}};
const server=()=>"";
const subscribe=(changed:()=>void)=>{window.addEventListener("storage",changed);window.addEventListener(MENU_PREFERENCE_EVENT,changed);return()=>{window.removeEventListener("storage",changed);window.removeEventListener(MENU_PREFERENCE_EVENT,changed);};};
export function useMenuFavourites(branchId?:number){
 const raw=useSyncExternalStore(subscribe,read,server),ids=useMemo(()=>favouriteIds(raw,branchId),[raw,branchId]);
 const toggle=useCallback((ids:number[])=>{try{const result=toggleFavourites(localStorage,branchId??0,ids);if(result==="saved")window.dispatchEvent(new Event(MENU_PREFERENCE_EVENT));return result;}catch{return "unavailable" as const;}},[branchId]);
 const clear=useCallback(()=>{try{const result=clearFavourites(localStorage,branchId??0);if(result)window.dispatchEvent(new Event(MENU_PREFERENCE_EVENT));return result;}catch{return false;}},[branchId]);
 return {ids,toggle,clear};
}
