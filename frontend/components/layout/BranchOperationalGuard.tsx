"use client";
import {useEffect,useRef,useState,type ReactNode} from "react";
import {usePathname} from "next/navigation";
import Link from "next/link";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {cachedOperationalBranch,checkOperationalBranch} from "@/lib/branchOperationalCache";
import {useTranslation} from "@/lib/language";
import type {Branch} from "@/types/branch";

function BranchRefreshNotice({retrying,onRetry}:{retrying:boolean;onRetry:()=>void}) {
 const translate=useTranslation(),dialog=useRef<HTMLDialogElement>(null);
 useEffect(()=>{
  const element=dialog.current,previous=document.activeElement as HTMLElement|null;
  element?.showModal();
  return()=>{element?.close();previous?.focus({preventScroll:true});};
 },[]);
 return <dialog ref={dialog} className="customer-branch-refresh-dialog" aria-label={translate("Branch availability could not be checked")} onCancel={event=>event.preventDefault()}>
  <section className="customer-page-state" aria-busy={retrying}>
   <div>{retrying?<><span className="customer-page-state-spinner" aria-hidden="true"/><p role="status">{translate("Loading your page…")}</p></>:<>
    <h1>{translate("Branch availability could not be checked")}</h1>
    <p>{translate("Please reconnect and try again, or choose another branch.")}</p>
    <button type="button" onClick={onRetry}>{translate("Try again")}</button>
    <Link href="/branches">{translate("Choose another branch")}</Link>
   </>}</div>
  </section>
 </dialog>;
}

export default function BranchOperationalGuard({children}:{children:ReactNode}){
 const translate=useTranslation();
 const pathname=usePathname(),{branch}=useSelectedBranch();
 const routed=pathname.match(/^\/branches\/(\d+)(?:\/|$)/);
 const id=routed?Number(routed[1]):pathname==="/"||pathname==="/menu"||pathname==="/occasions"?branch?.id:null;
 const [status,setStatus]=useState<{id:number;branch:Branch|null;error:boolean}|null>(null);
 const [revision,setRevision]=useState(0);
 const [retrying,setRetrying]=useState(false);
 useEffect(()=>{
  if(!id)return;
  let active=true,refreshing=false;
  const refresh=async(force=false)=>{
   if(refreshing)return;refreshing=true;
   const previousBranch=cachedOperationalBranch(id);
   try{const result=await checkOperationalBranch(id,force);if(active)setStatus({id,branch:result,error:false});}
   catch{if(active)setStatus(previous=>({id,branch:previous?.id===id?previous.branch:previousBranch,error:true}));}
   finally{refreshing=false;if(active)setRetrying(false);}
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
 const retry=()=>{setRetrying(true);if(!checked)setStatus(null);setRevision(value=>value+1);};
 if(!current&&!checked)return <main className="customer-page-state" aria-busy="true"><div role="status"><span className="customer-page-state-brand" aria-hidden="true">G</span><span className="customer-page-state-spinner" aria-hidden="true"/><p>{translate("Loading your page…")}</p></div></main>;
 if(current?.error&&!checked||checked?.operational===false)return <main className="customer-page-state"><section aria-label={translate("Branch unavailable")}><h1>{translate(current?.error?"Branch availability could not be checked":"Currently not operational")}</h1><p>{translate(current?.error?"Please reconnect and try again, or choose another branch.":"This branch is temporarily closed for customer visits and new orders. Please choose another branch. Your placed orders remain available in order history.")}</p>{current?.error&&<button type="button" onClick={retry}>{translate("Try again")}</button>}<Link href="/branches">{translate("Choose another branch")}</Link></section></main>;
 return <><div style={{display:"contents"}} inert={current?.error===true}>{children}</div>{current?.error&&<BranchRefreshNotice retrying={retrying} onRetry={retry}/>}</>;
}
