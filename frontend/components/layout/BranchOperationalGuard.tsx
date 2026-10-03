"use client";
import {useEffect,useState,type ReactNode} from "react";
import {usePathname} from "next/navigation";
import Link from "next/link";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {apiClient} from "@/services/apiClient";
import {useTranslation} from "@/lib/language";
import type {Branch} from "@/types/branch";
export default function BranchOperationalGuard({children}:{children:ReactNode}){
 const translate=useTranslation();
 const pathname=usePathname(),{branch}=useSelectedBranch();
 const routed=pathname.match(/^\/branches\/(\d+)(?:\/|$)/);
 const id=routed?Number(routed[1]):pathname==="/menu"||pathname==="/occasions"?branch?.id:null;
 const [status,setStatus]=useState<{id:number;branch:Branch|null;error:boolean}|null>(null);
 useEffect(()=>{
  if(!id)return;const controller=new AbortController();
  const refresh=async()=>{try{const result=await apiClient<Branch>(`/api/branches/${id}`,{signal:controller.signal,cache:"no-store"});if(!controller.signal.aborted)setStatus({id,branch:result,error:false});}catch{if(!controller.signal.aborted)setStatus(current=>current?.id===id&&current.branch?current:{id,branch:null,error:true});}};
  void refresh();const timer=setInterval(()=>{if(document.visibilityState==="visible")void refresh();},15000);
  const resume=()=>{if(document.visibilityState==="visible")void refresh();};document.addEventListener("visibilitychange",resume);window.addEventListener("online",resume);
  return()=>{controller.abort();clearInterval(timer);document.removeEventListener("visibilitychange",resume);window.removeEventListener("online",resume);};
 },[id]);
 if(!id)return children;
 if(status?.id!==id)return <p role="status" className="m-5 rounded-2xl border bg-[#fffaf2] p-6 text-sm">{translate("Checking branch availability…")}</p>;
 if(status.error||status.branch?.operational===false)return <section className="mx-auto my-6 max-w-xl rounded-3xl border bg-[#fffaf2] p-6 text-[#143936]" aria-label={translate("Branch unavailable")}><p className="text-sm font-bold">{status.branch?.name??translate("This branch")}</p><h1 className="mt-3 text-2xl font-bold">{translate(status.error?"Branch availability could not be checked":"Currently not operational")}</h1><p className="mt-3 text-sm leading-6">{translate(status.error?"Please reconnect and try again, or choose another branch.":"This branch is temporarily closed for customer visits and new orders. Please choose another branch. Your placed orders remain available in order history.")}</p><Link href="/branches" className="mt-5 inline-flex min-h-12 items-center rounded-xl bg-[#143936] px-5 font-bold text-[#fffaf2]">{translate("Choose another branch")}</Link></section>;
 return children;
}
