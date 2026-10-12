"use client";
import {T} from "@/lib/language";

import {useEffect,useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {adminFetch} from "@/services/adminApi";
import {getActiveBranches} from "@/services/branchApi";
import {preferredAdminBranchId,rememberAdminBranchId} from "@/lib/adminBranchSelection";
type Snapshot={today:string;orders:number;preparing:number;ready:number;completed:number;updatedAt:string};
export default function OperationsToday() {
    const {profile,authorization,hasPermission}=useAdminAuth();
    const [branches,setBranches]=useState<{id:number;name:string}[]>([]);const [branchId,setBranchId]=useState<number|null>(null);
    const [loaded,setLoaded]=useState<{branchId:number;data:Snapshot}|null>(null);const [error,setError]=useState("");const [version,setVersion]=useState(0);
    const allowed=hasPermission("ORDER_VIEW");
    useEffect(()=>{
        if(!profile||!allowed)return;const controller=new AbortController();
        getActiveBranches(controller.signal).then(list=>{if(controller.signal.aborted)return;const permitted=profile.roleName==="OWNER_ADMIN"?list:list.filter(branch=>profile.branchIds.includes(branch.id));setBranches(permitted);setBranchId(current=>preferredAdminBranchId(profile.staffId,permitted,current));}).catch(()=>{if(!controller.signal.aborted)setError("Could not load your branches.");});
        return()=>controller.abort();
    },[profile,allowed]);
    useEffect(()=>{
        if(!branchId||!authorization||!allowed)return;const controller=new AbortController();
        const load=async()=>{try{const response=await adminFetch(`/api/admin/branches/${branchId}/dashboard/today`,authorization,{signal:controller.signal});if(!response.ok)throw new Error("Could not load today's orders.");const data=await response.json() as Snapshot;if(!controller.signal.aborted){setLoaded({branchId,data});setError("");}}catch(failure){if(!controller.signal.aborted)setError(failure instanceof Error?failure.message:"Could not load summary.");}};
        void load();const timer=window.setInterval(()=>{if(document.visibilityState==="visible")void load();},30000);
        return()=>{controller.abort();window.clearInterval(timer);};
    },[branchId,authorization,allowed,version]);
    if(!allowed)return null;
    const snapshot=loaded?.branchId===branchId?loaded.data:null;
    return <section className="rounded-2xl border border-[#d9e5df] bg-[#f4f8f5] p-5 sm:p-6" aria-label="Today at a glance">
        <div className="flex flex-wrap items-start justify-between gap-4"><div><h2 className="text-xl font-bold">Today at a glance</h2><p className="mt-1 text-sm text-[#526762]">Orders scheduled for today at this branch · India time.{snapshot&&` ${snapshot.today}`}</p></div>
            <label className="text-sm font-semibold"><T text="Branch" /><select className="ml-2 min-h-11 rounded-xl border bg-white p-2" value={branchId??""} onChange={event=>{const id=Number(event.target.value);setBranchId(id);if(profile)rememberAdminBranchId(profile.staffId,id);}}>{branches.map(branch=><option key={branch.id} value={branch.id}>{branch.name}</option>)}</select></label></div>
        {error&&<p role="alert" className="mt-4 rounded-xl bg-red-50 p-3 text-red-800">{error} <button onClick={()=>setVersion(current=>current+1)} className="min-h-11 underline"><T text="Retry" /></button></p>}
        {!snapshot&&!error&&<p role="status" className="mt-4">Loading operational summary…</p>}
        {snapshot&&<><div className="mt-5 grid grid-cols-2 gap-3 lg:grid-cols-4">{[["Orders due today",snapshot.orders],["Preparing",snapshot.preparing],["Ready for pickup",snapshot.ready],["Completed today",snapshot.completed]].map(([label,value])=><div key={label} className="rounded-xl border bg-white p-4"><p className="text-sm text-[#526762]">{label}</p><p className="mt-2 text-3xl font-bold">{Number(value).toLocaleString("en-IN")}</p></div>)}</div>
        <p className="mt-3 text-xs text-[#526762]"><T text="Updated" />{" "}{new Date(snapshot.updatedAt).toLocaleTimeString("en-IN",{timeZone:"Asia/Kolkata",hour:"numeric",minute:"2-digit"})} IST · refreshes every 30 seconds. Unpaid and cancelled orders are excluded.</p></>}
    </section>;
}
