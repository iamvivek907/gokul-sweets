"use client";

import Link from "next/link";
import {useEffect, useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {adminFetch} from "@/services/adminApi";
import {getActiveBranches} from "@/services/branchApi";

type Template={policyId:number;quantity:number;autoApprove:boolean};
type Plan={enabled:boolean;openingTime:string;closingTime:string;slotMinutes:number;capacity:number;maintainDays:number;weekdays:number;version:number;templates:Template[]};
type Policy={id:number;name:string;unit:string;bookingDays:number;buffer:number;maximum:number|null;readyStockRequired:boolean;eligible:boolean};
type Workspace={plan:Plan|null;policies:Policy[];lastAttempt:string|null;lastSuccess:string|null;message:string|null};
const initial:Plan={enabled:false,openingTime:"10:00",closingTime:"21:00",slotMinutes:30,capacity:10,maintainDays:14,weekdays:127,version:-1,templates:[]};
const control="mt-2 block min-h-11 w-full rounded-xl border border-stone-300 bg-white px-3 py-2";
const action="min-h-11 rounded-xl border border-stone-300 bg-white px-4 py-2 disabled:opacity-50";
const days=["Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"];

export default function RollingAutomationPage(){
    const {profile,authorization,hasPermission}=useAdminAuth();
    const [branches,setBranches]=useState<{id:number;name:string}[]>([]);
    const [branchId,setBranchId]=useState<number|null>(null);
    const [snapshot,setSnapshot]=useState<{branchId:number;data:Workspace}|null>(null);
    const [draft,setDraft]=useState<Plan>(initial);
    const [dirty,setDirty]=useState(false);
    const [reload,setReload]=useState(0);
    const [error,setError]=useState("");
    const [notice,setNotice]=useState("");
    const [busy,setBusy]=useState(false);
    const canManage=hasPermission("BRANCH_MANAGE")&&hasPermission("INVENTORY_MANAGE")&&hasPermission("APPROVAL_MANAGE");
    useEffect(()=>{
        if(!profile)return;
        const controller=new AbortController();
        getActiveBranches(controller.signal).then(list=>{
            if(controller.signal.aborted)return;
            const allowed=profile.roleName==="OWNER_ADMIN"?list:list.filter(branch=>profile.branchIds.includes(branch.id));
            setBranches(allowed);setBranchId(current=>allowed.some(b=>b.id===current)?current:allowed[0]?.id??null);
        }).catch(()=>{if(!controller.signal.aborted)setError("Could not load branches.");});
        return()=>controller.abort();
    },[profile]);
    useEffect(()=>{
        if(!authorization||!branchId)return;
        const controller=new AbortController();
        adminFetch(`/api/admin/branches/${branchId}/rolling-automation`,authorization,{signal:controller.signal}).then(async response=>{
            if(!response.ok)throw Error("Could not load this branch's automation.");
            const data=await response.json() as Workspace;
            if(!controller.signal.aborted){setSnapshot({branchId,data});setDraft(data.plan??{...initial,templates:[]});setDirty(false);setError("");}
        }).catch(reason=>{if(!controller.signal.aborted)setError(reason.message);});
        return()=>controller.abort();
    },[authorization,branchId,reload]);
    const workspace=snapshot?.branchId===branchId?snapshot.data:null;
    function change(patch:Partial<Plan>){setDraft(current=>({...current,...patch}));setDirty(true);setNotice("");}
    function changeTemplate(policyId:number,patch:Partial<Template>){
        change({templates:draft.templates.map(item=>item.policyId===policyId?{...item,...patch}:item)});
    }
    async function submit(run:boolean){
        if(!branchId||!authorization||!workspace||busy||!canManage)return;
        const selectedBranch=branchId;
        setBusy(true);setError("");setNotice("");
        try{
            const response=await adminFetch(`/api/admin/branches/${selectedBranch}/rolling-automation${run?"/reconcile":""}`,authorization,{
                method:run?"POST":"PUT",headers:{"Content-Type":"application/json"},...(run?{}:{body:JSON.stringify(draft)})});
            if(!response.ok){
                const detail=await response.json().catch(()=>({}));
                throw Error(detail.detail||detail.message||"Could not complete the request. Reload and retry.");
            }
            const data=await response.json() as Workspace;
            setSnapshot({branchId:selectedBranch,data});setDraft(data.plan??initial);setDirty(false);
            setNotice(run?"Coverage check finished. Review the run result below.":"Schedule saved. The next background check will use these settings.");
        }catch(reason){setError(reason instanceof Error?reason.message:"Could not save.");}
        finally{setBusy(false);}
    }
    return <main className="mx-auto max-w-6xl space-y-6 p-4 sm:p-6">
        <header><p className="text-sm font-semibold uppercase tracking-widest text-[#8f1838]">Separate automation</p>
            <h1 className="mt-2 text-3xl font-bold">Automatic slots & inventory</h1>
            <p className="mt-2 text-stone-600">Configure once per branch. The system fills missing pickup dates and allocations for your selected policies.</p>
        </header>
        <aside className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm">
            Your manual tools stay available. Enabling this flow gives it ownership of scheduled inventory for this branch, so the legacy inventory scheduler skips the branch. Turning it off allows the legacy scheduler to resume its existing rules. Neither action deletes slots, stock or bookings.
        </aside>
        <div className="flex flex-wrap items-end gap-3">
            <label className="min-w-0 flex-1 font-semibold">Branch<select className={control} value={branchId??""} disabled={busy||dirty} onChange={event=>{setBranchId(Number(event.target.value));setError("");setNotice("");}}>
                {!branches.length&&<option value="">No accessible branches</option>}{branches.map(branch=><option key={branch.id} value={branch.id}>{branch.name}</option>)}
            </select></label>
            <button className={action} disabled={busy||!branchId} onClick={()=>{setReload(value=>value+1);setNotice("");}}>Reload saved settings</button>
        </div>
        <p className="text-sm text-stone-600">Save or reload unsaved edits before switching branches. Reload discards your unsaved changes.</p>
        {error&&<p role="alert" className="rounded-xl bg-red-50 p-3 text-red-900">{error}</p>}
        {notice&&<p role="status" className="rounded-xl bg-green-50 p-3 text-green-900">{notice}</p>}
        {!workspace&&!error&&branchId&&<p role="status">Loading schedule…</p>}
        {workspace&&<form className="space-y-6" onSubmit={event=>{event.preventDefault();void submit(false);}}>
            <fieldset disabled={busy||!canManage} className="space-y-6 disabled:opacity-75">
                <section className="space-y-4 rounded-2xl border bg-white p-5">
                    <h2 className="text-xl font-bold">1. Choose when pickup is offered</h2>
                    <label className="flex min-h-11 items-center gap-3 font-semibold"><input type="checkbox" checked={draft.enabled} onChange={event=>change({enabled:event.target.checked})}/>Enable rolling automation for this branch</label>
                    <p className="text-sm text-stone-600">Off until you explicitly enable and save. Catch-up runs after application startup and then every ten minutes while the application is running.</p>
                    <div className="grid gap-4 sm:grid-cols-2">
                        <label>Start time · IST<input className={control} type="time" required value={draft.openingTime.slice(0,5)} onChange={event=>change({openingTime:event.target.value})}/><small className="mt-2 block text-stone-600">First slot begins at this time. Use the actual pickup opening time for this branch.</small></label>
                        <label>End time · IST<input className={control} type="time" required value={draft.closingTime.slice(0,5)} onChange={event=>change({closingTime:event.target.value})}/><small className="mt-2 block text-stone-600">Last slot must finish by this time on the same day. Overnight schedules are not supported.</small></label>
                        <label>Slot duration · minutes<input className={control} type="number" min={5} max={180} required value={draft.slotMinutes} onChange={event=>change({slotMinutes:Number(event.target.value)})}/><small className="mt-2 block text-stone-600">For example, 30 creates 10:00–10:30 and 10:30–11:00. An incomplete final interval is omitted.</small></label>
                        <label>Orders per slot<input className={control} type="number" min={1} max={10000} required value={draft.capacity} onChange={event=>change({capacity:Number(event.target.value)})}/><small className="mt-2 block text-stone-600">Normal pickup capacity, not item quantity. This flow creates standard slots without priority pickup.</small></label>
                        <label>Calendar days to maintain<input className={control} type="number" min={1} max={60} required value={draft.maintainDays} onChange={event=>change({maintainDays:Number(event.target.value)})}/><small className="mt-2 block text-stone-600">Includes today. 14 means today plus the following 13 dates. Closed weekdays are skipped, not replaced with extra days.</small></label>
                    </div>
                    <fieldset><legend className="font-semibold">Selling weekdays</legend><div className="mt-2 flex flex-wrap gap-2">{days.map((day,index)=><label key={day} className="flex min-h-11 items-center gap-2 rounded-xl border px-3"><input type="checkbox" checked={Boolean(draft.weekdays&(1<<index))} onChange={()=>change({weekdays:draft.weekdays^(1<<index)})}/>{day}</label>)}</div><p className="mt-2 text-sm text-stone-600">Choose at least one day. Disabling a weekday does not remove already-generated slots or cancel bookings; use manual pickup scheduling for exceptions.</p></fieldset>
                    <p className="rounded-xl bg-stone-50 p-3 text-sm">A date with manual slots is preserved in full. Generated dates are not regenerated or rewritten when you change these settings. Edit or disable an existing slot manually. Private bulk appointments remain separate.</p>
                </section>
                <section className="space-y-4 rounded-2xl border bg-white p-5">
                    <h2 className="text-xl font-bold">2. Select configured inventory policies</h2>
                    <p className="text-sm text-stone-600">Only policies configured for this branch appear. Menu items without a policy are excluded. Quantities use the unit shown below: grams for GRAM policies, whole units otherwise. Policies, safety buffers and booking windows are not changed here.</p>
                    {!workspace.policies.length&&<p>No inventory policies configured for this branch yet.</p>}
                    {workspace.policies.map(policy=>{
                        const template=draft.templates.find(item=>item.policyId===policy.id);
                        return <div key={policy.id} className="space-y-3 rounded-xl border p-4">
                            <label className="flex min-h-11 items-center gap-3 font-semibold"><input type="checkbox" checked={!!template} onChange={event=>change({templates:event.target.checked?[...draft.templates,{policyId:policy.id,quantity:0,autoApprove:false}]:draft.templates.filter(item=>item.policyId!==policy.id)})}/>{policy.name}</label>
                            <p className="text-sm text-stone-600">Unit: {policy.unit} · Buffer: {policy.buffer} · Maximum: {policy.maximum??"Not set"} · Booking window: {policy.bookingDays} days ahead</p>
                            {!policy.eligible&&<p className="text-sm text-amber-900">Skipped while this policy is offline or set to manual control.</p>}
                            {template&&<div className="grid gap-4 sm:grid-cols-2">
                                <label>Guaranteed daily quantity · {policy.unit}<input className={control} type="number" min={policy.unit==="GRAM"?0.001:1} step={policy.unit==="GRAM"?0.001:1} max={policy.maximum??undefined} required value={template.quantity||""} onChange={event=>changeTemplate(policy.id,{quantity:Number(event.target.value)})}/><small className="mt-2 block text-stone-600">Enter the quantity you can reliably provide. It must exceed the safety buffer and stay within the policy maximum.</small></label>
                                <label>Approval mode<select className={control} value={template.autoApprove?"approved":"draft"} onChange={event=>changeTemplate(policy.id,{autoApprove:event.target.value==="approved"})}><option value="draft">Create a draft for staff review</option><option value="approved">Approve this guaranteed quantity automatically</option></select><small className="mt-2 block text-stone-600">{policy.readyStockRequired?"Actual production must still be marked ready. Approval does not prove the food exists.":"Draft quantities are not sellable until staff approve them."}</small></label>
                            </div>}
                        </div>;
                    })}
                    <p className="text-sm text-stone-600">Allocations are created only on dates with active regular pickup slots, within the booking window of each policy and the storefront horizon. Offline dishes and bulk-only products are skipped. Existing allocations are never topped up, replaced or reset.</p>
                    <Link href="/admin/inventory/setup" className="inline-flex min-h-11 items-center font-semibold underline">Open existing product policy setup</Link>
                </section>
                <button type="submit" className="min-h-11 rounded-xl bg-[#8f1838] px-5 py-3 font-semibold text-white">{busy?"Saving…":"Save automation settings"}</button>
            </fieldset>
        </form>}
        {workspace&&<section className="space-y-3 rounded-2xl border bg-white p-5">
            <h2 className="text-xl font-bold">3. Check generation</h2>
            <p className="text-sm">Last attempt: {workspace.lastAttempt??"Not run"} · Last success: {workspace.lastSuccess??"Not run"}</p>
            <p role="status">{workspace.message??"Save and enable a schedule, then run the first coverage check."}</p>
            <button type="button" className={action} disabled={busy||dirty||!workspace.plan?.enabled||!canManage} onClick={()=>void submit(true)}>Generate missing coverage now</button>
            <p className="text-sm text-stone-600">Uses saved settings. Safe to repeat: existing dates and allocations are preserved. A failed run is retried at the next background check. If a run fails, resolve the reported setup problem and try again.</p>
            <Link href="/admin/inventory" className="inline-flex min-h-11 items-center font-semibold underline">Review daily allocations and drafts</Link>
        </section>}
    </main>;
}
