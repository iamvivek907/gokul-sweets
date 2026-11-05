"use client";

import Link from "next/link";
import {useEffect, useRef, useState} from "react";
import AdminHelp from "@/components/admin/AdminHelp";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {adminFetch} from "@/services/adminApi";
import {getActiveBranches} from "@/services/branchApi";

type Template={policyId:number;quantity:number;autoApprove:boolean};
type Plan={enabled:boolean;openingTime:string;closingTime:string;slotMinutes:number;capacity:number;maintainDays:number;weekdays:number;version:number;templates:Template[]};
type Policy={id:number;name:string;unit:string;bookingDays:number;buffer:number;maximum:number|null;readyStockRequired:boolean;eligible:boolean;selectable:boolean;unavailableReason:string|null};
type Workspace={plan:Plan|null;policies:Policy[];lastAttempt:string|null;lastSuccess:string|null;message:string|null};
const initial:Plan={enabled:false,openingTime:"10:00",closingTime:"21:00",slotMinutes:30,capacity:10,maintainDays:14,weekdays:127,version:-1,templates:[]};
const control="mt-2 block min-h-11 w-full rounded-xl border border-stone-300 bg-white px-3 py-2";
const action="min-h-11 rounded-xl border border-stone-300 bg-white px-4 py-2 disabled:opacity-50";
const days=["Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"];

type Help={description:string;guidance:string};
const help:Record<string,Help>={
    branch:{description:"Each branch has its own saved automation plan and inventory policy selections.",guidance:"Choose the branch whose pickup dates and inventory you want to automate. Save or reload edits before switching. This plan does not configure another branch."},
    enabled:{description:"The background worker uses only this branch’s enabled, saved plan. It checks after startup and every ten minutes while the application is running, and retries failed runs.",guidance:"Enable and save after configuring hours, weekdays and selected policies. The legacy inventory scheduler skips this branch while enabled; manual tools remain available. Pausing restores the legacy scheduler’s existing rules and preserves generated dates, allocations and bookings."},
    start:{description:"The first newly generated standard pickup slot starts at this time in India Standard Time (IST).",guidance:"Enter the branch’s pickup opening time, for example 10:00. Start must be before end on the same day. Existing manual or generated dates are preserved rather than rewritten."},
    end:{description:"Every newly generated slot must finish by this IST time. Overnight schedules are not supported.",guidance:"For a 10:00 start, 21:00 end and 30-minute duration, the final slot is 20:30–21:00. An incomplete interval at the end is omitted. Changing this value affects missing dates only."},
    duration:{description:"The worker divides the start-to-end interval into complete pickup slots of this many minutes.",guidance:"Enter 5–180 minutes. For example, 30 produces 10:00–10:30 and 10:30–11:00. Existing dates keep their slots; use manual scheduling to edit an existing date."},
    capacity:{description:"This limits normal pickup orders in each newly generated slot; it is independent of dish stock quantities.",guidance:"Enter 1–10,000 orders according to the branch’s pickup capacity. This flow creates standard slots without priority pickup. Each dish must separately pass inventory, preparation and service checks."},
    horizon:{description:"The worker maintains a rolling calendar range including today. Inventory follows active regular pickup dates on selected weekdays within this range, including existing future-dated regular slots.",guidance:"Enter 1–60 days: 14 means today through today + 13. Closed weekdays do not add extra days. Coverage advances automatically, but the storefront horizon and each policy’s booking window can shorten inventory coverage. Existing slots and allocations are preserved."},
    weekdays:{description:"These weekdays control which dates receive missing pickup slots and inventory allocations.",guidance:"Select at least one selling day. Unchecking a day does not delete existing slots or cancel bookings. No new allocations are generated on unselected weekdays, even if slots already exist. Previously created slots and allocations remain; disable exceptional existing slots manually."},
    policy:{description:"Only explicitly selected, configured physical inventory policies receive automated allocations. The unit, safety buffer, maximum and booking window come from existing product policy setup.",guidance:"Select only dishes this branch can reliably provide. Unselected dishes receive no allocation from this flow. Capacity-only, manual-control, offline and bulk-only products are excluded. Existing allocations are never topped up or reset."},
    quantity:{description:"This is the planned physical quantity for one policy on each eligible pickup date, not a quantity per slot or an instruction to refill sold stock.",guidance:"Use grams for GRAM policies (1,000 means 1 kg), or whole units otherwise. Enter more than the safety buffer and no more than the policy maximum. For 80 pieces with a buffer of 2, the buffer is still withheld. Only missing allocations are created; existing stock, reservations and physical readiness are preserved."},
    approval:{description:"Automatic approval publishes the configured guaranteed quantity through the existing approval service. Manual review creates a draft requiring staff approval each day.",guidance:"Choose Automatic approval for unattended daily allocation; new selections default to it. This never marks food physically ready. If this policy requires ready stock, staff must still record actual production readiness before it can be sold. Existing saved approval choices are preserved."},
};
function FieldLabel({id,title,helpKey}:{id:string;title:string;helpKey:string}){
    return <div className="flex items-center justify-between gap-2"><label htmlFor={id}>{title}</label><AdminHelp title={title} {...help[helpKey]}/></div>;
}

export default function RollingAutomationPage(){
    const {profile,authorization,hasPermission}=useAdminAuth();
    const [branches,setBranches]=useState<{id:number;name:string}[]>([]);
    const [branchId,setBranchId]=useState<number|null>(null);
    const [snapshot,setSnapshot]=useState<{branchId:number;data:Workspace}|null>(null);
    const [draft,setDraft]=useState<Plan>(initial);
    const [dirty,setDirty]=useState(false);
    const [reload,setReload]=useState(0);
    const [settledLoad,setSettledLoad]=useState<{branchId:number;reload:number}|null>(null);
    const requestVersion=useRef(0);
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
        const operation=++requestVersion.current;
        adminFetch(`/api/admin/branches/${branchId}/rolling-automation`,authorization,{signal:controller.signal}).then(async response=>{
            if(!response.ok)throw Error("Could not load this branch's automation.");
            const data=await response.json() as Workspace;
            if(!controller.signal.aborted&&operation===requestVersion.current){setSnapshot({branchId,data});setDraft(data.plan??{...initial,templates:[]});setDirty(false);setError("");}
        }).catch(reason=>{if(!controller.signal.aborted&&operation===requestVersion.current)setError(reason.message);})
            .finally(()=>{if(!controller.signal.aborted&&operation===requestVersion.current)setSettledLoad({branchId,reload});});
        return()=>controller.abort();
    },[authorization,branchId,reload]);
    const loading=branchId!==null&&(settledLoad?.branchId!==branchId||settledLoad?.reload!==reload);
    const workspace=snapshot?.branchId===branchId?snapshot.data:null;
    const controlsDisabled=busy||loading||!canManage;
    function change(patch:Partial<Plan>){setDraft(current=>({...current,...patch}));setDirty(true);setNotice("");}
    function changeTemplate(policyId:number,patch:Partial<Template>){
        change({templates:draft.templates.map(item=>item.policyId===policyId?{...item,...patch}:item)});
    }
    async function submit(run:boolean){
        if(!branchId||!authorization||!workspace||busy||loading||!canManage)return;
        const selectedBranch=branchId;
        const operation=++requestVersion.current;
        setBusy(true);setError("");setNotice("");
        try{
            const response=await adminFetch(`/api/admin/branches/${selectedBranch}/rolling-automation${run?"/reconcile":""}`,authorization,{
                method:run?"POST":"PUT",headers:{"Content-Type":"application/json"},...(run?{}:{body:JSON.stringify(draft)})});
            if(!response.ok){
                const detail=await response.json().catch(()=>({}));
                throw Error(detail.detail||detail.message||"Could not complete the request. Reload and retry.");
            }
            const data=await response.json() as Workspace;
            if(operation!==requestVersion.current)return;
            setSnapshot({branchId:selectedBranch,data});setDraft(data.plan??initial);setDirty(false);
            setNotice(run?"Coverage check finished. Review the run result below.":"Schedule saved. The next background check will use these settings.");
        }catch(reason){if(operation===requestVersion.current)setError(reason instanceof Error?reason.message:"Could not save.");}
        finally{setBusy(false);}
    }
    return <main aria-busy={busy||loading} className="mx-auto max-w-6xl space-y-6 p-4 sm:p-6">
        <header><p className="text-sm font-semibold uppercase tracking-widest text-[#8f1838]">Separate automation</p>
            <h1 className="mt-2 text-3xl font-bold">Automatic slots & inventory</h1>
            <p className="mt-2 text-stone-600">Configure once per branch. The system fills missing pickup dates and allocations for your selected policies.</p>
        </header>
        <aside className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm">
            Your manual tools stay available. Enabling this flow gives it ownership of scheduled inventory for this branch, so the legacy inventory scheduler skips the branch. Turning it off allows the legacy scheduler to resume its existing rules. Neither action deletes slots, stock or bookings.
        </aside>
        <div className="flex flex-wrap items-end gap-3">
            <div className="min-w-0 flex-1 font-semibold"><FieldLabel id="automation-branch" title="Branch" helpKey="branch"/><select id="automation-branch" className={control} value={branchId??""} disabled={busy||dirty||loading} onChange={event=>{requestVersion.current++;setBranchId(Number(event.target.value));setError("");setNotice("");}}>
                {!branches.length&&<option value="">No accessible branches</option>}{branches.map(branch=><option key={branch.id} value={branch.id}>{branch.name}</option>)}
            </select></div>
            <button className={action} disabled={busy||loading||!branchId} onClick={()=>{requestVersion.current++;setReload(value=>value+1);setNotice("");setError("");}}>Reload saved settings</button>
        </div>
        <p className="text-sm text-stone-600">Save or reload unsaved edits before switching branches. Reload discards your unsaved changes.</p>
        {error&&<p role="alert" className="rounded-xl bg-red-50 p-3 text-red-900">{error}</p>}
        {notice&&<p role="status" className="rounded-xl bg-green-50 p-3 text-green-900">{notice}</p>}
        {loading&&<p role="status">Loading saved settings…</p>}
        {workspace&&<form className="space-y-6" noValidate={!draft.enabled} onSubmit={event=>{event.preventDefault();void submit(false);}}>
            <fieldset className="space-y-6">
                <section className="space-y-4 rounded-2xl border bg-white p-5">
                    <h2 className="text-xl font-bold">1. Choose when pickup is offered</h2>
                    <div className="flex items-center justify-between gap-2"><label className="flex min-h-11 items-center gap-3 font-semibold"><input type="checkbox" disabled={controlsDisabled} checked={draft.enabled} onChange={event=>change({enabled:event.target.checked})}/>Enable rolling automation for this branch</label><AdminHelp title="Enable rolling automation" {...help.enabled}/></div>
                    <p className="text-sm text-stone-600">Off until you explicitly enable and save. Catch-up runs after application startup and then every ten minutes while the application is running.</p>
                    <div className="grid gap-4 sm:grid-cols-2">
                        <div><FieldLabel id="automation-start" title="Start time · IST" helpKey="start"/><input id="automation-start" disabled={controlsDisabled} className={control} type="time" required value={draft.openingTime.slice(0,5)} onChange={event=>change({openingTime:event.target.value})}/><small className="mt-2 block text-stone-600">First slot begins at this time. Use the actual pickup opening time for this branch.</small></div>
                        <div><FieldLabel id="automation-end" title="End time · IST" helpKey="end"/><input id="automation-end" disabled={controlsDisabled} className={control} type="time" required value={draft.closingTime.slice(0,5)} onChange={event=>change({closingTime:event.target.value})}/><small className="mt-2 block text-stone-600">Last slot must finish by this time on the same day. Overnight schedules are not supported.</small></div>
                        <div><FieldLabel id="automation-duration" title="Slot duration · minutes" helpKey="duration"/><input id="automation-duration" disabled={controlsDisabled} className={control} type="number" min={5} max={180} required value={draft.slotMinutes} onChange={event=>change({slotMinutes:Number(event.target.value)})}/><small className="mt-2 block text-stone-600">For example, 30 creates 10:00–10:30 and 10:30–11:00. An incomplete final interval is omitted.</small></div>
                        <div><FieldLabel id="automation-capacity" title="Orders per slot" helpKey="capacity"/><input id="automation-capacity" disabled={controlsDisabled} className={control} type="number" min={1} max={10000} required value={draft.capacity} onChange={event=>change({capacity:Number(event.target.value)})}/><small className="mt-2 block text-stone-600">Normal pickup capacity, not item quantity. This flow creates standard slots without priority pickup.</small></div>
                        <div><FieldLabel id="automation-horizon" title="Calendar days to maintain" helpKey="horizon"/><input id="automation-horizon" disabled={controlsDisabled} className={control} type="number" min={1} max={60} required value={draft.maintainDays} onChange={event=>change({maintainDays:Number(event.target.value)})}/><small className="mt-2 block text-stone-600">Includes today. 14 means today plus the following 13 dates. Closed weekdays are skipped, not replaced with extra days.</small></div>
                    </div>
                    <fieldset><legend className="sr-only">Selling weekdays</legend><div className="flex items-center justify-between gap-2"><span className="font-semibold" aria-hidden="true">Selling weekdays</span><AdminHelp title="Selling weekdays" {...help.weekdays}/></div><div className="mt-2 flex flex-wrap gap-2">{days.map((day,index)=><label key={day} className="flex min-h-11 items-center gap-2 rounded-xl border px-3"><input type="checkbox" disabled={controlsDisabled} checked={Boolean(draft.weekdays&(1<<index))} onChange={()=>change({weekdays:draft.weekdays^(1<<index)})}/>{day}</label>)}</div><p className="mt-2 text-sm text-stone-600">Choose at least one day. Disabling a weekday does not remove already-generated slots or cancel bookings; use manual pickup scheduling for exceptions.</p></fieldset>
                    <p className="rounded-xl bg-stone-50 p-3 text-sm">A date with manual slots is preserved in full. Generated dates are not regenerated or rewritten when you change these settings. Edit or disable an existing slot manually. Private bulk appointments remain separate.</p>
                </section>
                <section className="space-y-4 rounded-2xl border bg-white p-5">
                    <h2 className="text-xl font-bold">2. Select configured inventory policies</h2>
                    <p className="text-sm text-stone-600">Only policies configured for this branch appear. Menu items without a policy, capacity-only policies and manual-control policies are excluded. Saved selections that become unsupported stay visible so you can remove them or pause safely. Quantities use the unit shown below: grams for GRAM policies, whole units otherwise. Policies, safety buffers and booking windows are not changed here.</p>
                    {!workspace.policies.length&&<p>No inventory policies configured for this branch yet.</p>}
                    {workspace.policies.map(policy=>{
                        const template=draft.templates.find(item=>item.policyId===policy.id);
                        return <div key={policy.id} className="space-y-3 rounded-xl border p-4">
                            <div className="flex items-center justify-between gap-2"><label className="flex min-h-11 items-center gap-3 font-semibold"><input type="checkbox" checked={!!template} disabled={controlsDisabled||!policy.selectable&&!template} onChange={event=>change({templates:event.target.checked?[...draft.templates,{policyId:policy.id,quantity:0,autoApprove:true}]:draft.templates.filter(item=>item.policyId!==policy.id)})}/>{policy.name}</label><AdminHelp title={`Inventory policy: ${policy.name}`} {...help.policy}/></div>
                            <p className="text-sm text-stone-600">Unit: {policy.unit} · Buffer: {policy.buffer} · Maximum: {policy.maximum??"Not set"} · Booking window: {policy.bookingDays} days ahead</p>
                            {policy.unavailableReason&&<p className="text-sm text-amber-900">{policy.unavailableReason}</p>}
                            {template&&<div className="grid gap-4 sm:grid-cols-2">
                                <div><FieldLabel id={`automation-quantity-${policy.id}`} title={`Guaranteed daily quantity · ${policy.unit}`} helpKey="quantity"/><input id={`automation-quantity-${policy.id}`} className={control} type="number" disabled={controlsDisabled||!policy.selectable} min={policy.unit==="GRAM"?0.001:1} step={policy.unit==="GRAM"?0.001:1} max={policy.maximum??undefined} required value={template.quantity||""} onChange={event=>changeTemplate(policy.id,{quantity:Number(event.target.value)})}/><small className="mt-2 block text-stone-600">Enter the quantity you can reliably provide. It must exceed the safety buffer and stay within the policy maximum.</small></div>
                                <div><FieldLabel id={`automation-approval-${policy.id}`} title="Approval mode" helpKey="approval"/><select id={`automation-approval-${policy.id}`} className={control} disabled={controlsDisabled||!policy.selectable} value={template.autoApprove?"approved":"draft"} onChange={event=>changeTemplate(policy.id,{autoApprove:event.target.value==="approved"})}><option value="approved">Automatic approval · no daily approval needed</option><option value="draft">Manual review · create a daily draft</option></select><small className="mt-2 block text-stone-600">{policy.readyStockRequired?"Actual production must still be marked ready. Approval does not prove the food exists.":"Automatic approval publishes the guaranteed quantity without daily approval. Manual review requires staff approval each day."}</small></div>
                            </div>}
                        </div>;
                    })}
                    <p className="text-sm text-stone-600">Allocations are created only on selected weekdays with active regular pickup slots, within the booking window of each policy and the storefront horizon. Offline dishes and bulk-only products are skipped. Existing allocations are never topped up, replaced or reset.</p>
                    <Link href="/admin/inventory/setup" className="inline-flex min-h-11 items-center font-semibold underline">Open existing product policy setup</Link>
                </section>
                <button type="submit" disabled={controlsDisabled} className="min-h-11 rounded-xl bg-[#8f1838] px-5 py-3 font-semibold text-white">{busy?"Saving…":"Save automation settings"}</button>
            </fieldset>
        </form>}
        {workspace&&<section className="space-y-3 rounded-2xl border bg-white p-5">
            <h2 className="text-xl font-bold">3. Check generation</h2>
            <p className="text-sm">Last attempt: {workspace.lastAttempt??"Not run"} · Last success: {workspace.lastSuccess??"Not run"}</p>
            <p role="status">{workspace.message??"Save and enable a schedule. Coverage is generated automatically at the next background check."}</p>
            <button type="button" className={action} disabled={busy||loading||dirty||!workspace.plan?.enabled||!canManage} onClick={()=>void submit(true)}>Generate missing coverage now</button>
            <p className="text-sm text-stone-600">Optional immediate check using saved settings; daily generation and retries do not require this button. Safe to repeat: existing dates and allocations are preserved. A failed run is retried at the next background check. If a run fails, resolve the reported setup problem and try again.</p>
            <Link href="/admin/inventory" className="inline-flex min-h-11 items-center font-semibold underline">Review daily allocations and drafts</Link>
        </section>}
    </main>;
}
