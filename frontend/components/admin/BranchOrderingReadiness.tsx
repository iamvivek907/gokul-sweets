"use client";
import styles from "./BranchOrderingReadiness.module.css";
import Link from "next/link";
import {useEffect,useRef,useState} from "react";
import {discoverPickupDates} from "@/services/availabilityApi";

/** Deliberate, bounded read. Capacity discovery is not an inventory or orderability promise. */
export default function BranchOrderingReadiness({branchId}:{branchId:number}) {
    const [status,setStatus]=useState<"idle"|"loading"|"ready"|"empty"|"error">("idle");
    const request=useRef<AbortController|null>(null);
    useEffect(()=>()=>request.current?.abort(),[]);
    async function check() {
        request.current?.abort();const controller=new AbortController();request.current=controller;setStatus("loading");
        const today=new Intl.DateTimeFormat("en-CA",{timeZone:"Asia/Kolkata",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date());
        try {
            const result=await discoverPickupDates(branchId,today,8,AbortSignal.any([controller.signal,AbortSignal.timeout(10000)]));
            if(!Array.isArray(result.dates))throw new Error("Invalid discovery");
            if(!controller.signal.aborted)setStatus(result.dates.some(day=>day.slots.some(slot=>slot.normalAvailable))?"ready":"empty");
        } catch {if(!controller.signal.aborted)setStatus("error");}
    }
    return <details className={styles.readiness}><summary>Can customers order from this branch?</summary><p>Check standard pickup capacity for today and the next seven days. This does not change slots, stock or service hours.</p>
        <button type="button" disabled={status==="loading"} onClick={()=>void check()}>{status==="loading"?"Checking pickup capacity…":"Check pickup capacity"}</button>
        {status!=="idle"&&status!=="loading"&&<p role="status">{status==="ready"?"Standard pickup capacity was found. Items still need approved inventory, service hours and preparation time.":status==="empty"?"No standard pickup capacity was returned for these eight dates. Customers may be unable to order. Check pickup schedules first, then stock and service hours.":"Pickup capacity could not be checked. This is not confirmation that the branch is closed. Retry or review the settings."}</p>}
        <nav aria-label="Ordering readiness settings"><Link href="/admin/pickup-scheduling">Pickup schedules</Link><Link href="/admin/inventory/centre">Dated inventory</Link><Link href="/admin/menu/service-hours">Service hours</Link></nav>
    </details>;
}
