"use client";
import {T,useTranslation} from "@/lib/language";
import {useEffect, useState} from "react";
import {apiClient} from "@/services/apiClient";
import styles from "./BranchRatings.module.css";
type Page = {reviews:{comment:string;overallRating:number;displayName:string}[];nextBefore:number|null};
export default function BranchReviewList({branchId,stars=null,onClear}:{branchId:number;stars?:number|null;onClear?:()=>void}) {
 const translate=useTranslation();
 const [before,setBefore]=useState<number|null>(null),[page,setPage]=useState<Page|null>(null),[busy,setBusy]=useState(true),[error,setError]=useState(false),[retry,setRetry]=useState(0);
 useEffect(()=>{
  const controller=new AbortController();
  const params=new URLSearchParams();if(before!==null)params.set("before",String(before));if(stars!==null)params.set("stars",String(stars));const query=params.size?`?${params}`:"";
  apiClient<Page>(`/api/branches/${branchId}/discovery/reviews${query}`,{signal:AbortSignal.any([controller.signal,AbortSignal.timeout(10000)])})
   .then(data=>{if(!Array.isArray(data.reviews))throw new Error("Invalid reviews");if(!controller.signal.aborted)setPage(data);})
   .catch(()=>{if(!controller.signal.aborted)setError(true);})
   .finally(()=>{if(!controller.signal.aborted)setBusy(false);});
  return()=>controller.abort();
 },[branchId,before,retry,stars]);
 const nextBefore=page?.nextBefore ?? null;
 return <section id="branch-customer-reviews" className={styles.reviews} aria-label={translate("Customer reviews")}><p className="gokul-overline"><T text="From our customers"/></p><h2><T text="What guests are saying"/></h2>{stars!==null&&<div className={styles.filter}><span>{stars} ★ · <T text="Customer reviews"/></span><button type="button" onClick={onClear}><T text="All reviews"/></button></div>}
 {busy?<p role="status"><T text="Loading customer reviews…"/></p>:error?<div role="alert"><p><T text="Reviews could not be loaded."/></p><button type="button" onClick={()=>{setBusy(true);setError(false);setRetry(n=>n+1);}}><T text="Try again"/></button></div>:<>
 {page?.reviews.length ? <div>{page.reviews.map((review,index)=><figure key={index}><div aria-label={`${review.overallRating} ${translate("out of 5 stars")}`}>{"★".repeat(review.overallRating)}{"☆".repeat(5-review.overallRating)}</div><blockquote>{review.comment}</blockquote><figcaption><span className={styles.avatar} aria-hidden="true">{review.displayName==="Customer"?"G":review.displayName?.slice(0,1).toLocaleUpperCase()}</span><span><strong>{review.displayName==="Customer"?<T text="Gokul guest"/>:review.displayName}</strong><small><T text="Completed order"/></small></span></figcaption></figure>)}</div>:<p>{translate(before===null&&!page?.nextBefore?"No published comments yet.":"No comments to show on this page.")}</p>}
 <nav aria-label={translate("Customer review pages")}>{before!==null&&<button type="button" onClick={()=>{setBusy(true);setError(false);setBefore(null);}}><T text="Latest reviews"/></button>}{nextBefore!==null&&<button type="button" onClick={()=>{setBusy(true);setError(false);setBefore(nextBefore);}}><T text="More reviews"/></button>}</nav></>}
 </section>;
}
