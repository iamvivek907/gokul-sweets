"use client";
import {useEffect, useState} from "react";
import {apiClient} from "@/services/apiClient";
import styles from "./BranchRatings.module.css";
type Page = {reviews:{comment:string;overallRating:number;displayName:string}[];nextBefore:number|null};
export default function BranchReviewList({branchId}:{branchId:number}) {
 const [before,setBefore]=useState<number|null>(null),[page,setPage]=useState<Page|null>(null),[busy,setBusy]=useState(true),[error,setError]=useState(false),[retry,setRetry]=useState(0);
 useEffect(()=>{
  const controller=new AbortController();
  const query=before==null?"":`?before=${encodeURIComponent(before)}`;
  apiClient<Page>(`/api/branches/${branchId}/discovery/reviews${query}`,{signal:AbortSignal.any([controller.signal,AbortSignal.timeout(10000)])})
   .then(data=>{if(!Array.isArray(data.reviews))throw new Error("Invalid reviews");if(!controller.signal.aborted)setPage(data);})
   .catch(()=>{if(!controller.signal.aborted)setError(true);})
   .finally(()=>{if(!controller.signal.aborted)setBusy(false);});
  return()=>controller.abort();
 },[branchId,before,retry]);
 const nextBefore=page?.nextBefore ?? null;
 return <section className={styles.reviews} aria-label="Customer reviews"><p className="gokul-overline">From our customers</p><h2>What guests are saying</h2>
 {busy?<p role="status">Loading customer reviews…</p>:error?<div role="alert"><p>Reviews could not be loaded.</p><button type="button" onClick={()=>{setBusy(true);setError(false);setRetry(n=>n+1);}}>Try again</button></div>:<>
 {page?.reviews.length ? <div>{page.reviews.map((review,index)=><figure key={index}><div aria-label={`${review.overallRating} out of 5 stars`}>{"★".repeat(review.overallRating)}{"☆".repeat(5-review.overallRating)}</div><blockquote>{review.comment}</blockquote><figcaption>{review.displayName} · Completed order</figcaption></figure>)}</div>:<p>{before===null&&!page?.nextBefore?"No published comments yet.":"No comments to show on this page."}</p>}
 <nav aria-label="Customer review pages">{before!==null&&<button type="button" onClick={()=>{setBusy(true);setError(false);setBefore(null);}}>Latest reviews</button>}{nextBefore!==null&&<button type="button" onClick={()=>{setBusy(true);setError(false);setBefore(nextBefore);}}>More reviews</button>}</nav></>}
 </section>;
}
