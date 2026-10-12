"use client";
import {useEffect,useState} from "react";
import AppShell from "@/components/layout/AppShell";
import MobilePageBack from "@/components/customer/MobilePageBack";
import BrandLoading from "@/components/common/BrandLoading";
import BranchSelector from "@/components/branch/BranchSelector";
import {getActiveBranches} from "@/services/branchApi";
import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";
import {T} from "@/lib/language";
import type {Branch} from "@/types/branch";

export default function OccasionBranchesPage(){
 const {features,error:configurationError,retry}=useStorefrontConfiguration();
 const [branches,setBranches]=useState<Branch[]|null>(null),[error,setError]=useState(false),[revision,setRevision]=useState(0);
 useEffect(()=>{const controller=new AbortController();
  void getActiveBranches(AbortSignal.any([controller.signal,AbortSignal.timeout(15000)])).then(list=>{if(!controller.signal.aborted){setBranches(list.filter(branch=>branch.active!==false));setError(false);}}).catch(()=>{if(!controller.signal.aborted)setError(true);});
  return()=>controller.abort();
 },[revision]);
 return <AppShell showSocialPopup={false}><section className="occasion-branch-chooser">
  <MobilePageBack href="/" label="Home"/><h1><T text="Choose a branch for your occasion"/></h1><p><T text="Select the branch that will prepare your bulk order."/></p>
  {configurationError?<p role="alert">{configurationError}<button type="button" className="min-h-11 px-3 underline" onClick={retry}><T text="Try again"/></button></p>:!features?<BrandLoading/>:!features.occasionEnquiries?<p><T text="Occasion enquiries are not available yet."/></p>:error?<p role="alert"><T text="Branch details could not be loaded."/><button type="button" className="min-h-11 px-3 underline" onClick={()=>{setError(false);setBranches(null);setRevision(value=>value+1);}}><T text="Try again"/></button></p>:branches===null?<BrandLoading label="Loading branches…"/>:branches.length===0?<p><T text="No branches are available right now."/></p>:<div className="occasion-branch-list">{branches.map(branch=><article key={branch.id}><h2>{branch.name}</h2><p>{[branch.address,branch.city].filter(Boolean).join(", ")}</p><BranchSelector cardBranch={branch} destination="occasions" actionLabel="Plan at this branch"/></article>)}</div>}
 </section></AppShell>;
}
