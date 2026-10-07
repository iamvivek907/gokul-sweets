"use client";
import {useEffect, useState} from "react";
import ReferenceWordmark from "@/components/layout/ReferenceWordmark";
import {T} from "@/lib/language";

/** Presentation only: callers retain their existing timeout, recovery and payment rules. */
export default function BrandLoading({label="Loading your page…",className="",compact=false,fullscreen=false,detail}:{label?:string;className?:string;compact?:boolean;fullscreen?:boolean;detail?:string}) {
    const [delayed,setDelayed]=useState(false);
    useEffect(()=>{const timer=setTimeout(()=>setDelayed(true),8000);return()=>clearTimeout(timer);},[]);
    return <div role="status" aria-live="polite" aria-busy="true" className={`customer-brand-loading ${compact?"is-compact":"customer-page-state"} ${fullscreen?"customer-route-loading":""} ${className}`}>
        <div className="brand-loading-content"><ReferenceWordmark className="brand-loading-wordmark" /><span className="customer-brand-loading-spinner" aria-hidden="true"><span/></span><p><T text={label}/></p>
        <small className="brand-loading-detail"><T text={delayed ? "This is taking longer than usual. We are waiting for a response; please keep this page open." : detail ?? "Connecting to the branch and loading the latest details."}/></small></div>
    </div>;
}
