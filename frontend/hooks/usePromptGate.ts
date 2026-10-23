"use client";
import {useEffect,useState,useSyncExternalStore} from "react";
import {pwaInstall} from "@/lib/pwaInstall";
/** Optional invitations yield to real tasks and the first-visit ordering guide. */
export function usePromptGate(ownInstall=false){
 const busy=useSyncExternalStore(pwaInstall.subscribe,()=>{const state=pwaInstall.getSnapshot();return state.promotionBlocked||!ownInstall&&(state.isPromptInProgress||state.guideOpen);},()=>false);
 const [allowed,setAllowed]=useState(false);
 useEffect(()=>{
  const check=()=>setAllowed(!Array.from(document.querySelectorAll('dialog[open], [aria-modal="true"], .ordering-tour-invite, .ordering-tour-coach')).some(node=>!ownInstall||!node.matches(".pwa-install-guide")));
  const observer=new MutationObserver(check);observer.observe(document.body,{childList:true,subtree:true,attributes:true,attributeFilter:["open","aria-modal"]});check();
  return()=>observer.disconnect();
 },[ownInstall]);
 return allowed&&!busy;
}
