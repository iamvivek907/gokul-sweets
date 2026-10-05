"use client";
import {useEffect,useState,type ReactNode} from "react";
import {usePathname} from "next/navigation";
import Link from "next/link";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {cachedOperationalBranch,checkOperationalBranch} from "@/lib/branchOperationalCache";
import {useTranslation} from "@/lib/language";
import type {Branch} from "@/types/branch";
export default function BranchOperationalGuard({children}:{children:ReactNode}){
 const translate=useTranslation();
 const pathname=usePathname(),{branch}=useSelectedBranch();
 const routed=pathname.match(/^\/branches\/(\d+)(?:\/|$)/);
 const id=routed?Number(routed[1]):pathname==="/"||pathname==="/menu"||pathname==="/occasions"?branch?.id:null;
 const [status,setStatus]=useState<{id:number;branch:Branch|null;error:boolean}|null>(null);
 const [revision,setRevision]=useState(0);
 useEffect(()=>{
  if(!id)return;
  let active=true,refreshing=false;
  const refresh=async(force=false)=>{
   if(refreshing)return;refreshing=true;
   try{const result=await checkOperationalBranch(id,force);if(active)setStatus({id,branch:result,error:false});}
   catch{if(active)setStatus({id,branch:null,error:true});}
   finally{refreshing=false;}
  };
  void refresh(revision > 0);
  const timer=setInterval(()=>{if(document.visibilityState==="visible"&&navigator.onLine)void refresh(true);},15000);
  const resume=()=>{if(document.visibilityState==="visible"&&navigator.onLine)void refresh(true);};
  document.addEventListener("visibilitychange",resume);window.addEventListener("online",resume);
  return()=>{active=false;clearInterval(timer);document.removeEventListener("visibilitychange",resume);window.removeEventListener("online",resume);};
 },[id,revision]);
 if(!id)return children;
 const current=status?.id===id?status:null;
 const checked=current?.branch??(!current?cachedOperationalBranch(id):null);
 if(!current&&!checked)return <main className="customer-page-state" aria-busy="true"><div role="status"><span className="customer-page-state-brand" aria-hidden="true">G</span><span className="customer-page-state-spinner" aria-hidden="true"/><p>{translate("Loading your page…")}</p></div></main>;
 if(current?.error||checked?.operational===false)return <main className="customer-page-state"><section aria-label={translate("Branch unavailable")}><h1>{translate(current?.error?"Branch availability could not be checked":"Currently not operational")}</h1><p>{translate(current?.error?"Please reconnect and try again, or choose another branch.":"This branch is temporarily closed for customer visits and new orders. Please choose another branch. Your placed orders remain available in order history.")}</p>{current?.error&&<button type="button" onClick={()=>{setStatus(null);setRevision(value=>value+1);}}>{translate("Try again")}</button>}<Link href="/branches">{translate("Choose another branch")}</Link></section></main>;
 return children;
}
