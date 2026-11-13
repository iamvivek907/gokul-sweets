"use client";

import Link from "next/link";
import {useEffect, useMemo, useRef, useState} from "react";
import AdminHelp from "@/components/admin/AdminHelp";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {adminFetch} from "@/services/adminApi";
import {getActiveBranches} from "@/services/branchApi";

type Template={policyId:number;quantity:number;autoApprove:boolean};
type Plan={enabled:boolean;openingTime:string;closingTime:string;slotMinutes:number;capacity:number;maintainDays:number;weekdays:number;version:number;templates:Template[];usePolicyMaximum?:boolean;autoReady?:boolean};
type Policy={id:number;name:string;unit:string;bookingDays:number;buffer:number;maximum:number|null;readyStockRequired:boolean;eligible:boolean;selectable:boolean;unavailableReason:string|null};
type Run={id:number;trigger:string;actor:string;status:string;startedAt:string;finishedAt:string|null;durationMs:number;lockWaitMs:number;lockHeldMs:number;slotsCreated:number;allocationsCreated:number;summary:string};
type Health={status:string;overdue:boolean;lastRunFailed:boolean;interrupted:boolean;missingSlots:number;missingAllocations:number;expectedAllocations:number;closedDates:number;includedPolicies:number;excludedPolicies:number;drafts:number;readinessPending:number;issues:{date:string;item:string;reason:string}[];message:string};
type Workspace={plan:Plan|null;policies:Policy[];lastAttempt:string|null;lastSuccess:string|null;message:string|null;health?:Health;runs?:Run[];storefrontBookingDays?:number|null};
const initial:Plan={enabled:false,openingTime:"10:00",closingTime:"21:00",slotMinutes:30,capacity:10,maintainDays:14,weekdays:127,version:-1,templates:[],usePolicyMaximum:true,autoReady:true};
const control="mt-2 block min-h-11 w-full rounded-xl border border-stone-300 bg-white px-3 py-2";
const action="min-h-11 rounded-xl border border-stone-300 bg-white px-4 py-2 disabled:opacity-50";
const days=["Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"];

type Help={description:string;guidance:string};
const help:Record<string,Help>={
    branch:{description:"Each branch has its own saved automation plan and inventory policy selections.",guidance:"Choose the branch whose pickup dates and inventory you want to automate. Save or reload edits before switching. This plan does not configure another branch."},
    enabled:{description:"The background worker uses only this branch’s enabled, saved plan. It runs once daily at 12:00 AM IST while the application is running. It does not run on startup or poll throughout the day.",guidance:"Enable and save after configuring hours, weekdays and policies. Use Save & generate now to apply new configuration and fill missing stock immediately. The legacy inventory scheduler skips this branch while enabled; manual tools remain available. Pausing restores the legacy scheduler’s existing rules and preserves generated dates, allocations and bookings."},
    start:{description:"The first newly generated standard pickup slot starts at this time in India Standard Time (IST).",guidance:"Enter the branch’s pickup opening time, for example 10:00. Start must be before end on the same day. Each check fills missing intervals. Existing manual, booked and disabled slots are preserved."},
    end:{description:"Every newly generated slot must finish by this IST time. Overnight schedules are not supported.",guidance:"For a 10:00 start, 21:00 end and 30-minute duration, the final slot is 20:30–21:00. An incomplete interval at the end is omitted. Changing this value affects missing intervals only; existing slots are not resized or removed."},
    duration:{description:"The worker divides the start-to-end interval into complete pickup slots of this many minutes.",guidance:"Enter 5–180 minutes. For example, 30 produces 10:00–10:30 and 10:30–11:00. Missing intervals are repaired on existing dates too. Overlapping manual or disabled slots are never replaced."},
    capacity:{description:"This limits normal pickup orders in each newly generated slot; it is independent of dish stock quantities.",guidance:"Enter 1–10,000 orders according to the branch’s pickup capacity. This flow creates standard slots without priority pickup. Each dish must separately pass inventory, preparation and service checks."},
    horizon:{description:"The worker maintains a rolling calendar range including today. Inventory follows active regular pickup dates on selected weekdays within this range, including existing future-dated regular slots.",guidance:"Enter 1–60 days: 14 means today through today + 13. Closed weekdays do not add extra days. Coverage advances automatically, but the storefront horizon and each policy’s booking window can shorten inventory coverage. Existing slots and allocations are preserved."},
    weekdays:{description:"These weekdays control which dates receive missing pickup slots and inventory allocations.",guidance:"Select at least one selling day. Unchecking a day does not delete existing slots or cancel bookings. No new allocations are generated on unselected weekdays, even if slots already exist. Previously created slots and allocations remain; disable exceptional existing slots manually."},
    policy:{description:"Only explicitly selected, configured physical inventory policies receive automated allocations. The unit, safety buffer, maximum and booking window come from existing product policy setup.",guidance:"Select only dishes this branch can reliably provide. Unselected dishes receive no allocation from this flow. Capacity-only, manual-control, offline and bulk-only products are excluded. Existing allocations are never topped up or reset."},
    quantity:{description:"This is the planned physical quantity for one policy on each eligible pickup date, not a quantity per slot or an instruction to refill sold stock.",guidance:"Use grams for GRAM policies (1,000 means 1 kg), or whole units otherwise. Enter more than the safety buffer and no more than the policy maximum. For 80 pieces with a buffer of 2, the buffer is still withheld. Only missing allocations are created; existing stock, reservations and physical readiness are preserved."},
    source:{description:"Choose where the worker gets daily quantities. Policy maximum mode automatically uses every eligible configured physical policy in this branch, including policies configured later.",guidance:"Use policy maximums to avoid selecting dishes and entering quantities individually. A maximum of 200 with a buffer of 50 approves 200 and withholds 50 once, leaving 150 before other availability checks. Missing/invalid maximums, offline, manual, capacity-only and bulk-only policies are skipped. Missing allocations use the policy maximum and are auto-approved. Existing ready quantities, including zero, and manual allocations are preserved. Automatic readiness records guaranteed quantities when enabled; otherwise ready-stock policies still require staff readiness. Saved manual selections are retained."},
    readiness:{description:"Automatically records each guaranteed, automatically approved quantity as ready. This is on by default for rolling automation and removes the daily manual readiness step.",guidance:"Use this only for quantities the branch guarantees it can supply. The gross approved quantity is marked ready; the safety buffer is withheld once during ordering. Previously untouched approvals created by this automation are also upgraded. Existing staff quantities, explicit zero readings, delays, closures, drafts and any allocation with stock activity are preserved. Turn this off if staff must confirm physical stock first. Product policies and manual inventory settings are not changed."},
    approval:{description:"Automatic approval publishes the configured guaranteed quantity. With automatic readiness enabled, readiness is recorded in the same run. Manual review creates a draft requiring staff approval each day.",guidance:"Choose Automatic approval for unattended daily allocation; new selections default to it. Automatic readiness records the branch’s guaranteed quantity as ready. With it turned off, a ready-stock policy still needs staff readiness before sale. Existing saved approval choices are preserved."},
};
function FieldLabel({id,title,helpKey}:{id:string;title:string;helpKey:string}){
    return <div className="flex items-center justify-between gap-2"><label htmlFor={id}>{title}</label><AdminHelp title={title} {...help[helpKey]}/></div>;
}

function usableMaximum(policy:Policy){
    return policy.eligible&&policy.maximum!==null&&Number.isFinite(policy.maximum)&&policy.maximum>policy.buffer
        &&policy.maximum>0&&(policy.unit==="GRAM"||Number.isInteger(policy.maximum));
}
function runTime(value:string|null){
    if(!value)return "Not run";
    const parsed=new Date(value.replace(" ","T").replace(/(\.\d{3})\d+/,"$1").replace(/([+-]\d{2})$/,"$1:00"));
    return Number.isNaN(parsed.getTime())?"Time unavailable":new Intl.DateTimeFormat("en-IN",{timeZone:"Asia/Kolkata",day:"numeric",month:"short",year:"numeric",hour:"numeric",minute:"2-digit",hour12:true}).format(parsed)+" IST";
}
function needsQuantity(template:Template,policy:Policy|undefined,enabled:boolean){
    const q=template.quantity;
    return !Number.isFinite(q)||q<=0||q>99999999999.999||Math.abs(q*1000-Math.round(q*1000))>0.000001
        ||!!(enabled&&policy?.selectable&&(q<=policy.buffer||(policy.maximum!==null&&q>policy.maximum)||(policy.unit!=="GRAM"&&!Number.isInteger(q))));
}


function skipReason(policy:Policy){
    if(policy.unavailableReason)return policy.unavailableReason;
    if(policy.maximum===null)return "Set a maximum daily quantity in Product policies.";
    if(policy.maximum<=policy.buffer)return "Maximum quantity must be greater than the safety buffer.";
    if(policy.unit!=="GRAM"&&!Number.isInteger(policy.maximum))return "This unit needs a whole-number maximum quantity.";
    return "This policy is not eligible for automatic daily stock.";
}
function indiaDate(){const parts=new Intl.DateTimeFormat("en-GB",{timeZone:"Asia/Kolkata",year:"numeric",month:"2-digit",day:"2-digit"}).formatToParts(new Date());const value=Object.fromEntries(parts.map(part=>[part.type,part.value]));return `${value.year}-${value.month}-${value.day}`;}
function endDate(start:string,daysAhead:number){
    if(!start)return "…";
    const [year,month,day]=start.split("-").map(Number);
    return new Intl.DateTimeFormat("en-IN",{timeZone:"UTC",day:"numeric",month:"short",year:"numeric"}).format(new Date(Date.UTC(year,month-1,day+Math.max(0,daysAhead))));
}
function StepHeading({step,title,description}:{step:number;title:string;description:string}){
    return <div className="flex items-start gap-3"><span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-[#f8edf0] text-sm font-bold text-[#8f1838]" aria-hidden="true">{step}</span><div><h2 className="text-xl font-bold text-stone-900">{step}. {title}</h2><p className="mt-1 text-sm leading-6 text-stone-600">{description}</p></div></div>;
}

function CoveragePanel({health,runs,retryDisabled,onRetry}:{health:Health;runs:Run[];retryDisabled:boolean;onRetry:()=>void}){
    const attention=health.status==="NEEDS_ATTENTION";
    const title=health.status==="COMPLETE"?"Coverage complete":health.status==="RUNNING"?"Generation in progress":health.status==="PAUSED"?"Automation paused":"Coverage needs attention";
    const labels:Record<string,string>={SUCCESS:"Completed",FAILED:"Failed",RUNNING:"Running",SKIPPED:"Skipped"};
    return <section aria-label="Automation coverage health" className={`space-y-4 rounded-2xl border p-4 ${attention?"border-amber-300 bg-amber-50":health.status==="COMPLETE"?"border-emerald-200 bg-emerald-50":"border-stone-200 bg-stone-50"}`}>
        <div className="flex flex-wrap items-center justify-between gap-3"><h3 className="text-lg font-bold">{title}</h3>{attention&&<button type="button" className={action} disabled={retryDisabled} onClick={onRetry}>Retry coverage now</button>}</div>
        {health.overdue&&<p role="alert" className="font-semibold text-amber-950">Daily generation is overdue. No successful check has completed for today’s due schedule. Review the run history and trigger a manual check.</p>}
        {health.lastRunFailed&&<p role="alert" className="font-semibold text-red-900">The latest run failed. Review its result below and retry after resolving the reported setup problem.</p>}
        {health.interrupted&&<p role="alert" className="font-semibold text-red-900">The latest run has not finished within the expected time. Check application health before retrying.</p>}
        <p className="text-sm">{health.message}</p>
        {health.status!=="PAUSED"&&<>
            <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">{[["Missing slots",health.missingSlots],["Missing allocations",health.missingAllocations],["Expected allocations",health.expectedAllocations],["Included policies",health.includedPolicies]].map(([label,value])=><div key={label} className="rounded-xl bg-white/80 p-3"><dt className="text-sm text-stone-600">{label}</dt><dd className="text-2xl font-bold">{value}</dd></div>)}</dl>
            {health.includedPolicies===0&&<p className="font-semibold">No eligible policies are included. Review quantity source, policy maximums and dish availability below.</p>}
            <p className="text-sm">{health.excludedPolicies} excluded policies · {health.closedDates} closed or elapsed dates. Selected weekdays, policy/storefront booking limits and intentionally disabled intervals are respected.</p>
            <p className="text-sm">{health.drafts} existing drafts await approval · {health.readinessPending} approved allocations await physical readiness. Existing manual restrictions and exhausted stock remain governed by inventory controls.</p>
            {!!health.issues.length&&<details><summary className="min-h-11 cursor-pointer py-3 font-semibold">View missing dates and dishes</summary><ul className="space-y-2">{health.issues.map((issue,index)=><li key={`${issue.date}-${index}`} className="rounded-xl bg-white p-3 text-sm"><strong>{issue.date} · {issue.item}</strong><p>{issue.reason}</p></li>)}</ul><p className="mt-2 text-xs">Examples are bounded; totals cover the entire effective window.</p></details>}
        </>}
        <p className="text-xs text-stone-600">Overdue alerts allow 15 minutes after midnight IST. Newly enabled plans begin their first daily schedule at the next midnight. Ordinary edits preserve overdue alerts. Save changes before retrying; there is no additional background polling.</p>
        <details><summary className="min-h-11 cursor-pointer py-3 font-semibold">Recent automation runs · {runs.length} of at most 30</summary>{!runs.length?<p className="text-sm">No recorded runs yet. Earlier success timestamps remain available above.</p>:<ol className="space-y-3">{runs.map(run=><li key={run.id} className="space-y-2 rounded-xl border border-stone-200 bg-white p-3 text-sm"><div className="flex flex-wrap justify-between gap-2"><strong><span className={`mr-2 rounded-full px-2 py-1 text-xs ${run.status==="FAILED"?"bg-red-50 text-red-900":run.status==="SUCCESS"?"bg-emerald-50 text-emerald-900":"bg-stone-100 text-stone-700"}`}>{labels[run.status]??run.status}</span>{run.trigger==="SCHEDULED"?"Scheduled":"Manual"}</strong><span>{runTime(run.startedAt)}</span></div><p className="break-words">Triggered by {run.actor} · {run.finishedAt?`Finished ${runTime(run.finishedAt)}`:"Awaiting completion"}</p><p>{run.slotsCreated} slots · {run.allocationsCreated} allocations · {(run.durationMs/1000).toFixed(2)}s total · {(run.lockWaitMs/1000).toFixed(2)}s waiting for branch lock · {(run.lockHeldMs/1000).toFixed(2)}s holding branch lock</p><p className="break-words">{run.summary||"Execution is in progress."}</p></li>)}</ol>}</details>
    </section>;
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
    const [feedbackTarget,setFeedbackTarget]=useState<"page"|"review"|"stock">("page");
    const feedbackRef=useRef<HTMLDivElement>(null);
    useEffect(()=>{if(error||notice)feedbackRef.current?.scrollIntoView({block:"nearest"});},[error,notice]);
    const [busy,setBusy]=useState(false);
    const mutation=useRef(false);
    const form=useRef<HTMLFormElement>(null);
    const [policyListOpen,setPolicyListOpen]=useState(true);
    const [previewDate,setPreviewDate]=useState("");
    useEffect(()=>{
        let timer:ReturnType<typeof setTimeout>;
        function update(){
            const date=indiaDate();setPreviewDate(date);clearTimeout(timer);
            const next=Date.parse(`${date}T00:00:00+05:30`)+86400000;
            timer=setTimeout(update,Math.max(1000,next-Date.now()+1000));
        }
        update();window.addEventListener("focus",update);
        return()=>{clearTimeout(timer);window.removeEventListener("focus",update);};
    },[]);
    const [policySearch,setPolicySearch]=useState("");
    const [policyFilter,setPolicyFilter]=useState("all");
    const [policyPage,setPolicyPage]=useState(1);
    const [pageSize,setPageSize]=useState(25);
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
            if(!controller.signal.aborted&&operation===requestVersion.current){setSnapshot({branchId,data});setDraft(data.plan??{...initial,templates:[]});setPolicyListOpen(!(data.plan?.usePolicyMaximum??(data.plan?false:true)));setDirty(false);setError("");}
        }).catch(reason=>{if(!controller.signal.aborted&&operation===requestVersion.current)setError(reason.message);})
            .finally(()=>{if(!controller.signal.aborted&&operation===requestVersion.current)setSettledLoad({branchId,reload});});
        return()=>controller.abort();
    },[authorization,branchId,reload]);
    const loading=branchId!==null&&(settledLoad?.branchId!==branchId||settledLoad?.reload!==reload);
    const workspace=snapshot?.branchId===branchId?snapshot.data:null;
    const controlsDisabled=busy||loading||!canManage;
    const policyById=useMemo(()=>new Map(workspace?.policies.map(policy=>[policy.id,policy])??[]),[workspace?.policies]);
    const maximumPolicies=useMemo(()=>(workspace?.policies??[]).filter(usableMaximum),[workspace?.policies]);
    const selectedIds=useMemo(()=>new Set(draft.usePolicyMaximum?maximumPolicies.map(policy=>policy.id):draft.templates.map(item=>item.policyId)),[draft.usePolicyMaximum,maximumPolicies,draft.templates]);
    const invalidIds=useMemo(()=>new Set(draft.usePolicyMaximum?[]:draft.templates.filter(item=>needsQuantity(item,policyById.get(item.policyId),draft.enabled)).map(item=>item.policyId)),[draft.usePolicyMaximum,draft.templates,draft.enabled,policyById]);
    const filteredPolicies=useMemo(()=>{
        const query=policySearch.trim().toLocaleLowerCase();
        return (workspace?.policies??[]).filter(policy=>(!query||policy.name.toLocaleLowerCase().includes(query))
            &&(policyFilter==="all"||policyFilter==="selected"&&selectedIds.has(policy.id)
                ||policyFilter==="attention"&&(draft.usePolicyMaximum?!usableMaximum(policy):invalidIds.has(policy.id)||selectedIds.has(policy.id)&&!policy.eligible)
                ||policyFilter==="shorter"&&selectedIds.has(policy.id)&&policy.eligible&&Math.min(policy.bookingDays,workspace?.storefrontBookingDays??policy.bookingDays)<draft.maintainDays-1));
    },[workspace?.policies,workspace?.storefrontBookingDays,policySearch,policyFilter,selectedIds,invalidIds,draft.usePolicyMaximum,draft.maintainDays]);
    const totalPages=Math.max(1,Math.ceil(filteredPolicies.length/pageSize));
    const currentPage=Math.min(policyPage,totalPages);
    const visiblePolicies=filteredPolicies.slice((currentPage-1)*pageSize,currentPage*pageSize);
    const skippedSelections=draft.usePolicyMaximum?(workspace?.policies.length??0)-maximumPolicies.length:draft.templates.filter(item=>!policyById.get(item.policyId)?.eligible).length;
    const savedSelectionCount=workspace?.plan?.usePolicyMaximum?maximumPolicies.length:workspace?.plan?.templates.length??0;
    const savedReadyCount=workspace?.plan?.usePolicyMaximum?maximumPolicies.length:workspace?.plan?.templates.filter(item=>policyById.get(item.policyId)?.eligible&&!needsQuantity(item,policyById.get(item.policyId),true)).length??0;
    const matchingBulkPolicies=filteredPolicies.filter(policy=>policy.selectable&&policy.eligible);
    function selectMatching(){
        if(controlsDisabled)return;
        const existing=new Set(draft.templates.map(item=>item.policyId));
        change({templates:[...draft.templates,...matchingBulkPolicies.filter(policy=>!existing.has(policy.id)).map(policy=>({policyId:policy.id,quantity:usableMaximum(policy)?policy.maximum!:0,autoApprove:true}))]});
    }
    function clearMatching(){
        if(controlsDisabled)return;
        const matching=new Set(filteredPolicies.map(policy=>policy.id));
        change({templates:draft.templates.filter(item=>!matching.has(item.policyId))});
    }
    function change(patch:Partial<Plan>){setDraft(current=>({...current,...patch}));setDirty(true);setNotice("");}
    function changeTemplate(policyId:number,patch:Partial<Template>){
        change({templates:draft.templates.map(item=>item.policyId===policyId?{...item,...patch}:item)});
    }
    const draftDays=Number.isInteger(draft.maintainDays)&&draft.maintainDays>=1&&draft.maintainDays<=60?draft.maintainDays:1;
    const readyPolicies=useMemo(()=>(workspace?.policies??[]).filter(policy=>selectedIds.has(policy.id)&&policy.eligible&&!invalidIds.has(policy.id)),[workspace?.policies,selectedIds,invalidIds]);
    const shorterPolicies=readyPolicies.filter(policy=>Math.min(policy.bookingDays,workspace?.storefrontBookingDays??policy.bookingDays)<draftDays-1);
    const globalShort=workspace?.storefrontBookingDays!=null&&workspace.storefrontBookingDays<draftDays-1;
    const skipGroups=useMemo(()=>{
        const grouped=new Map<string,number>();
        for(const policy of workspace?.policies??[])if(!usableMaximum(policy)){const reason=skipReason(policy);grouped.set(reason,(grouped.get(reason)??0)+1);}
        return [...grouped].sort((a,b)=>b[1]-a[1]);
    },[workspace?.policies]);
    function showPolicies(filter:string){setPolicyFilter(filter);setPolicyPage(1);setPolicyListOpen(true);}
    async function submit(mode:"save"|"run"|"saveAndRun"){
        if(!branchId||!authorization||!workspace||mutation.current||loading||!canManage)return;
        setFeedbackTarget(mode==="run"?"stock":"review");
        if(mode==="run"&&dirty){setError("Save your changes before generating daily stock.");return;}
        if(mode==="run"&&savedReadyCount===0){setError("The saved setup has no eligible dishes or quantities. Review step 2, then Save & generate now.");return;}
        if(mode!=="run"){
            if(invalidIds.size){setError(`${invalidIds.size} selected dishes need a valid daily quantity. Review Needs attention, including selections on other pages.`);setPolicySearch("");showPolicies("attention");return;}
            if(draft.enabled&&form.current&&!form.current.reportValidity())return;
            if(draft.enabled&&(!draft.weekdays||draft.openingTime>=draft.closingTime)){setError("Choose at least one selling weekday, with opening time before closing time.");return;}
        }
        const selectedBranch=branchId,operation=++requestVersion.current;
        mutation.current=true;setBusy(true);setError("");setNotice("");
        let savedNow=false,current=workspace;
        async function read(response:Response){
            if(!response.ok){const detail=await response.json().catch(()=>({}));throw Error(detail.detail||detail.message||"Could not complete the request. Reload and retry.");}
            return await response.json() as Workspace;
        }
        try{
            if(mode==="save"||(mode==="saveAndRun"&&(dirty||!workspace.plan))){
                current=await read(await adminFetch(`/api/admin/branches/${selectedBranch}/rolling-automation`,authorization,{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify({...draft,autoReady:draft.autoReady!==false,templates:draft.usePolicyMaximum?workspace.plan?.templates??[]:draft.templates})}));
                if(operation!==requestVersion.current)return;
                setSnapshot({branchId:selectedBranch,data:current});setDraft(current.plan??initial);setDirty(false);savedNow=true;
                if(mode==="save"){setNotice(current.plan?.enabled?"Schedule saved. The next daily run at 12:00 AM IST will use these settings. No stock was generated by this save.":"Automation paused. This daily job will not generate slots or inventory while paused. Existing stock and bookings are kept; the legacy scheduler resumes its existing rules.");return;}
            }
            if(!current.plan?.enabled||!Number.isInteger(current.plan.version)||current.plan.version<0)throw Error("The saved setup is not ready to run. Reload and review the branch settings.");
            const data=await read(await adminFetch(`/api/admin/branches/${selectedBranch}/rolling-automation/reconcile?expectedVersion=${current.plan.version}`,authorization,{method:"POST"}));
            if(operation!==requestVersion.current)return;
            setSnapshot({branchId:selectedBranch,data});setDraft(data.plan??initial);setDirty(false);
            if(data.health?.includedPolicies===0){setError("No daily stock was created: the saved setup has no eligible dishes. Review step 2.");}
            else if(data.health?.expectedAllocations===0){setError("No daily stock is expected for this window. Review pickup hours, disabled dates and booking limits in steps 1 and 2.");}
            else if(data.health&&data.health.missingAllocations>0){setError(`The check finished, but ${data.health.missingAllocations} daily stock entries are still missing. Open saved stock status for the details.`);}
            else if(data.health&&(data.health.readinessPending>0||data.health.drafts>0)){setError(`Stock coverage checked, but ${data.health.readinessPending} allocations still need readiness and ${data.health.drafts} await approval. Existing staff overrides and manual-review drafts were preserved. Review saved stock details.`);}
            else{setNotice(data.health?"Saved setup checked. Eligible pickup dates have daily stock entries. Existing stock and bookings were kept.":"Stock check finished. Review the saved stock status below.");}
        }catch(reason){if(operation===requestVersion.current)setError(`${savedNow?"Settings saved, but daily stock generation did not finish. ":""}${reason instanceof Error?reason.message:"Could not complete the request."}`);}
        finally{mutation.current=false;setBusy(false);}
    }
    const feedback=(error||notice)?<div ref={feedbackRef} data-testid="action-feedback" className="scroll-mt-28 space-y-2">{error&&<p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-900">{error}</p>}{notice&&<p role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">{notice}</p>}</div>:null;
    return <main aria-busy={busy||loading} className="mx-auto max-w-7xl space-y-5 p-4 text-stone-800 sm:p-6">
        <header className="rounded-3xl border border-[#ecdfe1] bg-gradient-to-br from-[#fff7ed] via-white to-[#f2f8f5] p-5 sm:p-7">
            <p className="text-xs font-bold uppercase tracking-[.18em] text-[#8f1838]">Less daily admin work</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight sm:text-3xl">Automatic slots & inventory</h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-stone-600">Set your pickup window once. Keep missing slots and daily stock prepared for future dates.</p>
            <nav aria-label="Setup steps" className="mt-5 grid gap-2 sm:grid-cols-3">{[["1","Pickup window","#pickup-window"],["2","Dishes & quantities","#dish-setup"],["3","Review & start","#automation-review"]].map(([step,label,href])=><a key={step} href={href} className="flex min-h-11 items-center gap-2 rounded-xl border border-white bg-white/80 px-3 text-sm font-semibold"><span className="flex h-6 w-6 items-center justify-center rounded-full bg-[#f8edf0] text-xs text-[#8f1838]">{step}</span>{label}</a>)}</nav>
        </header>
        <div className="flex flex-wrap items-end gap-3 rounded-2xl border border-stone-200 bg-white p-4">
            <div className="min-w-0 flex-1 font-semibold"><FieldLabel id="automation-branch" title="Branch" helpKey="branch"/><select id="automation-branch" className={control} value={branchId??""} disabled={busy||dirty||loading} onChange={event=>{requestVersion.current++;setBranchId(Number(event.target.value));setPolicySearch("");setPolicyFilter("all");setPolicyPage(1);setError("");setNotice("");}}>{!branches.length&&<option value="">No accessible branches</option>}{branches.map(branch=><option key={branch.id} value={branch.id}>{branch.name}</option>)}</select></div>
            <button type="button" className={action} disabled={busy||loading||!branchId} onClick={()=>{requestVersion.current++;setFeedbackTarget("page");setReload(value=>value+1);setNotice("");setError("");}}>Reload saved settings</button>
            <p className="w-full text-xs text-stone-500">Save or reload before switching branches. Reload discards unsaved changes.</p>
        </div>
        {(feedbackTarget==="page"||!workspace)&&feedback}
        {loading&&<p role="status">Loading saved settings…</p>}
        {workspace&&<>
            <div className={`flex flex-wrap items-center justify-between gap-3 rounded-2xl border p-4 ${dirty?"border-amber-200 bg-amber-50":"border-stone-200 bg-stone-50"}`}>
                <div><p className="text-sm font-bold">{dirty?"You have unsaved changes":!workspace.plan?"New setup · not saved yet":"Your setup is saved"}</p><p className="mt-1 text-sm text-stone-600">{workspace.plan?`Saved setup: ${workspace.plan.enabled?"on":"paused"} · ${savedSelectionCount} dishes · ${workspace.plan.usePolicyMaximum?"policy maximums":"individual selection"}.`:"Nothing has been saved for this branch yet."}</p></div>
                <p className="max-w-sm text-sm font-semibold text-[#8f1838]">{dirty||!workspace.plan?`This preview has ${readyPolicies.length} dishes ready. Save & generate to apply it.`:savedSelectionCount===0?"No dishes are saved for daily stock yet. Start with step 2.":"Daily refresh: 12:00 AM IST. You can also generate now."}</p>
            </div>
            <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_330px]">
                <form ref={form} id="automation-form" className="min-w-0 space-y-5" noValidate onSubmit={event=>{event.preventDefault();void submit("save");}}>
                <section id="pickup-window" className="scroll-mt-28 space-y-5 rounded-3xl border border-stone-200 bg-white p-5 sm:p-6">
                    <StepHeading step={1} title="Set your pickup window" description="Choose the hours and dates to keep prepared for online pickup."/>
                    <div className="flex items-center justify-between gap-2"><label className="flex min-h-11 items-center gap-3 font-semibold"><input type="checkbox" disabled={controlsDisabled} aria-label="Enable daily setup for this branch" checked={draft.enabled} onChange={event=>change({enabled:event.target.checked})}/><span>Enable daily setup<span className="mt-1 block text-sm font-normal text-stone-500">Refresh missing pickup slots and daily stock at midnight IST.</span></span></label><AdminHelp title="Enable daily setup" {...help.enabled}/></div>

                    <div className="grid gap-4 sm:grid-cols-2">
                        <div><FieldLabel id="automation-start" title="Start time · IST" helpKey="start"/><input id="automation-start" disabled={controlsDisabled} className={control} type="time" required value={draft.openingTime.slice(0,5)} onChange={event=>change({openingTime:event.target.value})}/><small className="mt-2 block text-stone-600">First slot begins at this time. Use the actual pickup opening time for this branch.</small></div>
                        <div><FieldLabel id="automation-end" title="End time · IST" helpKey="end"/><input id="automation-end" disabled={controlsDisabled} className={control} type="time" required value={draft.closingTime.slice(0,5)} onChange={event=>change({closingTime:event.target.value})}/><small className="mt-2 block text-stone-600">Last slot must finish by this time on the same day. Overnight schedules are not supported.</small></div>
                        <div><FieldLabel id="automation-duration" title="Slot duration · minutes" helpKey="duration"/><input id="automation-duration" disabled={controlsDisabled} className={control} type="number" min={5} max={180} required value={draft.slotMinutes} onChange={event=>change({slotMinutes:Number(event.target.value)})}/><small className="mt-2 block text-stone-600">For example, 30 creates 10:00–10:30 and 10:30–11:00. An incomplete final interval is omitted.</small></div>
                        <div><FieldLabel id="automation-capacity" title="Orders per slot" helpKey="capacity"/><input id="automation-capacity" disabled={controlsDisabled} className={control} type="number" min={1} max={10000} required value={draft.capacity} onChange={event=>change({capacity:Number(event.target.value)})}/><small className="mt-2 block text-stone-600">Normal pickup capacity, not item quantity. This flow creates standard slots without priority pickup.</small></div>
                        <div><FieldLabel id="automation-horizon" title="Calendar days to maintain" helpKey="horizon"/><input id="automation-horizon" disabled={controlsDisabled} className={control} type="number" min={1} max={60} required value={draft.maintainDays} onChange={event=>change({maintainDays:Number(event.target.value)})}/><small className="mt-2 block text-stone-600">Includes today. 14 means today plus the following 13 dates. Closed weekdays are skipped, not replaced with extra days.</small></div>
                    </div>
                    <fieldset><legend className="sr-only">Selling weekdays</legend><div className="flex items-center justify-between gap-2"><span className="font-semibold" aria-hidden="true">Selling weekdays</span><AdminHelp title="Selling weekdays" {...help.weekdays}/></div><div className="mt-2 flex flex-wrap gap-2">{days.map((day,index)=><label key={day} className="flex min-h-11 items-center gap-2 rounded-xl border px-3"><input type="checkbox" disabled={controlsDisabled} checked={Boolean(draft.weekdays&(1<<index))} onChange={()=>change({weekdays:draft.weekdays^(1<<index)})}/>{day}</label>)}</div><p className="mt-2 text-sm text-stone-600">Choose at least one day. Disabling a weekday does not remove already-generated slots or cancel bookings; use manual pickup scheduling for exceptions.</p></fieldset>
                    <details className="rounded-xl bg-stone-50 px-4"><summary className="cursor-pointer py-3 text-sm font-semibold text-stone-600">How existing slots and manual changes are protected</summary><p className="pb-4 text-sm leading-6 text-stone-600">Missing intervals are filled. Existing times, bookings and disabled intervals are kept. Disable an interval to pause it intentionally; deleting it lets automation recreate it. Private occasion appointments stay separate.</p></details>
                </section>
                <section id="dish-setup" className="scroll-mt-28 space-y-5 rounded-3xl border border-stone-200 bg-white p-5 sm:p-6">
                    <div id="automation-policies-title"><StepHeading step={2} title="Choose how daily stock is set" description="Use your configured policy maximums, or choose individual dishes and quantities."/></div>
                    <div className="rounded-xl border border-[#ead7df] bg-[#fff7f9] p-4">
                        <FieldLabel id="automation-source" title="Inventory quantity source" helpKey="source"/>
                        <select id="automation-source" className={control} disabled={controlsDisabled} value={draft.usePolicyMaximum?"maximum":"selected"} onChange={event=>{change({usePolicyMaximum:event.target.value==="maximum"});setPolicyPage(1);setPolicyFilter("all");setPolicyListOpen(event.target.value!=="maximum");setError("");}}>
                            <option value="selected">Choose dishes and quantities myself</option>
                            <option value="maximum">Automatic · use policy maximums (recommended)</option>
                        </select>
                        <p className="mt-2 text-sm leading-6 text-stone-600">{draft.usePolicyMaximum?"Every eligible configured policy is included automatically. No one-by-one selection or daily quantity entry.":"Select dishes below and enter the daily quantity you can guarantee. For less manual setup, use policy maximums."}</p>
                        {!draft.usePolicyMaximum&&<button type="button" disabled={controlsDisabled} className="mt-3 min-h-11 rounded-xl bg-[#143936] px-4 text-sm font-semibold text-white disabled:opacity-50" onClick={()=>{change({usePolicyMaximum:true});setPolicyListOpen(false);setPolicyFilter("all");}}>Use policy maximums</button>}
                        {draft.usePolicyMaximum&&<p className="mt-3 rounded-lg bg-white p-3 text-sm text-[#143936]">Example: maximum 200 − buffer 50 = 150 planned before orders and other stock checks.</p>}
                    </div>
                    <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4"><div className="flex items-start justify-between gap-3"><label className="flex min-h-11 items-start gap-3 font-semibold text-emerald-950"><input type="checkbox" className="mt-1" checked={draft.autoReady!==false} disabled={controlsDisabled} onChange={event=>change({autoReady:event.target.checked})}/><span>Automatically mark guaranteed stock ready<span className="mt-1 block text-sm font-normal leading-6">On by default. Automatically approved dishes receive their guaranteed ready quantity in the same run, so customers do not wait for daily staff confirmation.</span></span></label><AdminHelp title="Automatic readiness" {...help.readiness}/></div><p className="mt-2 text-xs leading-5 text-emerald-950">Only use quantities the branch guarantees. Recorded staff quantities, stock activity, delays, closures and manual-review drafts are preserved.</p></div>
                    <div className="grid grid-cols-2 gap-3"><button type="button" onClick={()=>showPolicies("selected")} className="rounded-2xl border border-emerald-100 bg-emerald-50 p-4 text-left"><strong className="block text-2xl text-emerald-900" data-testid="draft-ready-count">{readyPolicies.length}</strong><span className="text-sm text-emerald-900">dishes ready to automate</span></button><button type="button" onClick={()=>showPolicies(draft.usePolicyMaximum?"attention":"all")} className="rounded-2xl border border-stone-200 bg-stone-50 p-4 text-left"><strong className="block text-2xl text-stone-800">{draft.usePolicyMaximum?skippedSelections:workspace.policies.length-selectedIds.size}</strong><span className="text-sm text-stone-600">{draft.usePolicyMaximum?"not included · see why":"not selected · review dishes"}</span></button></div>
                    {draft.usePolicyMaximum&&skipGroups.length>0&&<div className="space-y-1 text-sm text-stone-600">{skipGroups.slice(0,4).map(([reason,count])=><p key={reason}>{count} · {reason}</p>)}</div>}
                    {shorterPolicies.length>0&&<div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950"><strong>{shorterPolicies.length} dishes have shorter stock windows.</strong><p className="mt-1">They cannot fill every pickup date in this setup until their booking limits are extended.</p><button type="button" onClick={()=>showPolicies("shorter")} className="mt-2 min-h-11 font-semibold underline">Review shorter windows</button></div>}
                    <details id="policy-list" open={policyListOpen} onToggle={event=>setPolicyListOpen(event.currentTarget.open)} className="rounded-2xl border border-stone-200 p-3 sm:p-4"><summary className="cursor-pointer py-2 font-semibold text-stone-800">Review dishes & quantities</summary>
                    <div className="mt-3 space-y-4">
                    {!workspace.policies.length&&<p>No configured inventory policies for this branch. <Link className="font-semibold underline" href="/admin/inventory/setup" target="_blank" rel="noopener noreferrer">Open Product policies</Link> to configure them first.</p>}
                    <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto_auto]">
                        <label className="min-w-0 text-sm font-semibold">Find a dish<input data-browse-control type="search" className={control} value={policySearch} placeholder="Search dish name…" onKeyDown={event=>{if(event.key==="Enter")event.preventDefault();}} onChange={event=>{setPolicySearch(event.target.value);setPolicyPage(1);}}/></label>
                        <label className="text-sm font-semibold">Show<select data-browse-control className={control} value={policyFilter} onChange={event=>{setPolicyFilter(event.target.value);setPolicyPage(1);}}><option value="all">All dishes</option><option value="selected">{draft.usePolicyMaximum?"Included policies":"Selected dishes"}</option><option value="attention">{draft.usePolicyMaximum?"Not included":"Needs attention"}</option><option value="shorter">Shorter booking windows</option></select></label>
                        <label className="text-sm font-semibold">Rows per page<select data-browse-control className={control} value={pageSize} onChange={event=>{setPageSize(Number(event.target.value));setPolicyPage(1);}}>{[10,25,50].map(size=><option key={size} value={size}>{size}</option>)}</select></label>
                    </div>
                    {!draft.usePolicyMaximum&&<div className="space-y-2 rounded-xl bg-stone-50 p-3"><div className="flex flex-wrap gap-2"><button type="button" className={action} disabled={controlsDisabled||!matchingBulkPolicies.some(policy=>!selectedIds.has(policy.id))} onClick={selectMatching}>Select all matching dishes</button><button type="button" className={action} disabled={controlsDisabled||!filteredPolicies.some(policy=>selectedIds.has(policy.id))} onClick={clearMatching}>Clear matching selections</button></div><p className="text-xs leading-5 text-stone-600">Applies to the current search and filter across all pages. Only eligible dishes are added. Existing quantities and approval choices are kept. New selections use valid policy maximums; enter quantities where no valid maximum exists. Review before saving.</p></div>}
                    <p role="status" className="text-sm text-stone-600">{filteredPolicies.length?`Showing ${(currentPage-1)*pageSize+1}–${Math.min(currentPage*pageSize,filteredPolicies.length)} of ${filteredPolicies.length} dishes`:"No dishes match this view."} · {selectedIds.size} {draft.usePolicyMaximum?"automatically included":"selected across all pages"}. Changing page or search keeps your selections and quantities.</p>
                    <div aria-labelledby="automation-policies-title" className="space-y-2">
                    {visiblePolicies.map(policy=>{
                        const template=draft.templates.find(item=>item.policyId===policy.id);
                        return <div key={policy.id} data-policy-row className="space-y-2 rounded-xl border border-stone-200 bg-white p-3">
                            <div className="flex items-center justify-between gap-2">{draft.usePolicyMaximum?<p className="font-semibold">{policy.name} <span className={`ml-2 rounded-full px-2 py-1 text-xs ${usableMaximum(policy)?"bg-green-50 text-green-900":"bg-amber-50 text-amber-900"}`}>{usableMaximum(policy)?"Automatic":"Skipped"}</span></p>:<label className="flex min-h-11 items-center gap-3 font-semibold"><input type="checkbox" checked={!!template} disabled={controlsDisabled||!policy.selectable&&!template} onChange={event=>change({templates:event.target.checked?[...draft.templates,{policyId:policy.id,quantity:0,autoApprove:true}]:draft.templates.filter(item=>item.policyId!==policy.id)})}/>{policy.name}</label>}<AdminHelp title={`Inventory policy: ${policy.name}`} {...(draft.usePolicyMaximum?help.source:help.policy)}/></div>
                            <p className="text-sm text-stone-600">Unit: {policy.unit} · Buffer: {policy.buffer} · Maximum: {policy.maximum??"Not set"} · Booking window: {policy.bookingDays} days ahead</p>
                            {invalidIds.has(policy.id)&&<p className="text-sm font-semibold text-red-800">Enter a positive daily quantity in the correct unit, above the buffer and within the maximum.</p>}
                            {policy.unavailableReason&&<p className="text-sm text-amber-900">{policy.unavailableReason}</p>}
                            {draft.usePolicyMaximum&&<p className="text-sm">{usableMaximum(policy)?`Planned daily stock: ${policy.maximum} ${policy.unit} · After buffer: ${Number((policy.maximum!-policy.buffer).toFixed(3))} ${policy.unit}${policy.readyStockRequired?(draft.autoReady!==false?" · Guaranteed readiness automated":" · Staff readiness required before sale"):""}`:skipReason(policy)}</p>}
                            {selectedIds.has(policy.id)&&policy.eligible&&<p className={`text-sm ${Math.min(policy.bookingDays,workspace.storefrontBookingDays??policy.bookingDays)<draft.maintainDays-1?"font-semibold text-amber-900":"text-stone-500"}`}>Stock window preview: through {endDate(previewDate,Math.min(draftDays-1,policy.bookingDays,workspace.storefrontBookingDays??policy.bookingDays))} · {Math.min(draftDays,policy.bookingDays+1,(workspace.storefrontBookingDays??policy.bookingDays)+1)} calendar days including today.</p>}
                            {!draft.usePolicyMaximum&&template&&<div className="grid gap-4 sm:grid-cols-2">
                                <div><FieldLabel id={`automation-quantity-${policy.id}`} title={`Guaranteed daily quantity · ${policy.unit}`} helpKey="quantity"/><input id={`automation-quantity-${policy.id}`} className={control} type="number" disabled={controlsDisabled||!policy.selectable} min={policy.unit==="GRAM"?0.001:1} step={policy.unit==="GRAM"?0.001:1} max={policy.maximum??undefined} required value={template.quantity||""} onChange={event=>changeTemplate(policy.id,{quantity:Number(event.target.value)})}/><small className="mt-2 block text-stone-600">Enter the quantity you can reliably provide. It must exceed the safety buffer and stay within the policy maximum.</small></div>
                                <div><FieldLabel id={`automation-approval-${policy.id}`} title="Approval mode" helpKey="approval"/><select id={`automation-approval-${policy.id}`} className={control} disabled={controlsDisabled||!policy.selectable} value={template.autoApprove?"approved":"draft"} onChange={event=>changeTemplate(policy.id,{autoApprove:event.target.value==="approved"})}><option value="approved">Automatic approval · no daily approval needed</option><option value="draft">Manual review · create a daily draft</option></select><small className="mt-2 block text-stone-600">{policy.readyStockRequired?"Automatic readiness applies only with Automatic approval. Manual review or disabled automatic readiness needs staff confirmation.":"Automatic approval publishes the guaranteed quantity without daily approval. Manual review requires staff approval each day."}</small></div>
                            </div>}
                        </div>;
                    })}
                    </div>
                    <nav aria-label="Dish pages" className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-stone-50 p-3">
                        <button type="button" className={action} disabled={currentPage<=1} onClick={()=>setPolicyPage(currentPage-1)}>Previous dishes</button>
                        <span className="text-sm font-semibold">Page {currentPage} of {totalPages}</span>
                        <button type="button" className={action} disabled={currentPage>=totalPages} onClick={()=>setPolicyPage(currentPage+1)}>Next dishes</button>
                    </nav>
                    </div></details>
                    <p className="text-sm leading-6 text-stone-500">Only configured branch policies are used. Existing stock and orders are kept. Product and storefront booking limits still apply.</p>
                    <Link href="/admin/inventory/setup" target="_blank" rel="noopener noreferrer" className="inline-flex min-h-11 items-center text-sm font-semibold text-[#8f1838] underline">Open Product policies in a new tab</Link>
                </section>
                    <a href="#automation-review" className="inline-flex min-h-11 items-center rounded-xl border border-[#ead7df] bg-white px-4 text-sm font-semibold text-[#8f1838]">Continue to review & start →</a>
                </form>
                <aside id="automation-review" aria-label="Review automation setup" className="scroll-mt-28 space-y-4 rounded-3xl border border-[#ead7df] bg-gradient-to-b from-[#fff8ed] to-white p-5 shadow-sm xl:sticky xl:top-28">
                    <StepHeading step={3} title="Review & start" description="This preview uses the setup shown in steps 1 and 2."/>
                    <span className={`inline-flex rounded-full px-3 py-1 text-xs font-bold ${dirty?"bg-amber-100 text-amber-900":"bg-stone-100 text-stone-700"}`}>{dirty||!workspace.plan?"Unsaved preview":"Saved setup preview"}</span>
                    <div className="rounded-2xl bg-white p-4"><p className="text-xs font-semibold uppercase tracking-wide text-stone-500">Pickup window preview</p><p className="mt-2 text-lg font-bold">{endDate(previewDate,0)} – {endDate(previewDate,draftDays-1)}</p><p className="mt-1 text-sm text-stone-600">{draftDays} calendar days including today · selected weekdays only</p><p className="mt-2 text-sm">{draft.openingTime.slice(0,5)}–{draft.closingTime.slice(0,5)} IST · {draft.slotMinutes}-minute slots</p></div>
                    <p className="text-sm"><strong>{readyPolicies.length} dishes</strong> ready for daily stock · {draft.usePolicyMaximum?"policy maximum quantities":"your selected quantities"}.</p>
                    <p className={`rounded-xl p-3 text-sm ${draft.autoReady!==false?"bg-emerald-50 text-emerald-950":"bg-amber-50 text-amber-950"}`}>{draft.autoReady!==false?"Automatic readiness is ON: guaranteed automatically approved stock is marked ready in the same run. Staff overrides and manual-review drafts are kept.":"Automatic readiness is OFF: ready-stock dishes may stay unavailable until staff records readiness."}</p>
                    {globalShort&&<p className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-950">The storefront allows only {workspace.storefrontBookingDays} days ahead. Stock generation is limited to {workspace.storefrontBookingDays!+1} calendar days, even though pickup slots span {draftDays} days. Extend the storefront limit to cover the whole window.</p>}
                    {workspace.storefrontBookingDays==null&&<p className="text-xs text-stone-500">The storefront booking limit also applies. Reload after the latest backend deployment to see the exact limit.</p>}
                    {shorterPolicies.length>0&&<p className="text-sm text-amber-950">{shorterPolicies.length} included dishes have shorter booking windows. Review step 2 before expecting stock on every date.</p>}
                    {!draft.enabled&&<p className="text-sm font-semibold text-amber-950">Turn on daily setup in step 1 to generate stock.</p>}
                    {readyPolicies.length===0&&<p className="text-sm font-semibold text-amber-950">Choose policy maximums or configure eligible dishes in step 2. There is no daily stock to generate yet.</p>}
                    {invalidIds.size>0&&<button type="button" className="min-h-11 text-left text-sm font-semibold text-red-900 underline" onClick={()=>showPolicies("attention")}>Fix quantities for {invalidIds.size} selected dishes</button>}
                    {feedbackTarget==="review"&&feedback}
                    <button type="button" className="min-h-12 w-full rounded-2xl bg-[#8f1838] px-4 py-3 font-bold text-white shadow-sm disabled:opacity-40" disabled={controlsDisabled||!draft.enabled||!readyPolicies.length||invalidIds.size>0} onClick={()=>void submit("saveAndRun")}>{busy?"Applying setup…":"Save & generate now"}</button>
                    <button type="submit" form="automation-form" disabled={controlsDisabled} className={`${action} w-full text-sm font-semibold`}>Save automation settings</button>
                    <p className="text-xs leading-5 text-stone-500">Save & generate applies this setup and creates only missing records now. Save settings alone waits for the next daily run. Pausing takes effect when saved.</p>
                    {!canManage&&<p className="text-xs text-stone-500">Read-only access: ask an authorized manager to save or generate.</p>}
                    <details className="border-t border-stone-200 pt-2"><summary className="cursor-pointer py-2 text-sm font-semibold">Stock and manual changes</summary><p className="py-2 text-xs leading-5 text-stone-600">Existing quantities, bookings and disabled slots are kept. Buffers are withheld once. Automatic readiness records guaranteed quantities only where enabled and automatically approved. Existing staff readiness and manual-review drafts are preserved. Manual inventory tools remain available.</p></details>
                </aside>
            </div>
            <section aria-label="Saved stock status" className="space-y-3 rounded-3xl border border-stone-200 bg-white p-5">
                <div className="flex flex-wrap justify-between gap-2"><h2 className="font-bold">Saved stock status</h2><span className="text-xs text-stone-500">Last check: {runTime(workspace.lastSuccess)}</span></div>
                {!workspace.plan?<p className="text-sm text-stone-600">Choose your setup above, then Save & generate now.</p>:dirty?<p className="text-sm text-stone-600">This status belongs to your saved setup. Your unsaved preview above has not been applied.</p>:savedSelectionCount===0?<p className="text-sm text-amber-950">No dishes are saved for daily stock. Use policy maximums in step 2, then Save & generate now.</p>:workspace.health?.missingAllocations?<p className="text-sm text-amber-950">{workspace.health.missingAllocations} daily stock entries are missing. Generate now to fill eligible dates.</p>:<p className="text-sm text-stone-600">{workspace.health?.status==="COMPLETE"?"Expected pickup and stock records are present. Normal readiness and stock checks still apply.":"Review the details below if stock is still missing after generation."}</p>}
                {!!workspace.health&&(workspace.health.readinessPending>0||workspace.health.drafts>0)&&<p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-950">Customer ordering can still be blocked for {workspace.health.readinessPending} allocations awaiting readiness and {workspace.health.drafts} awaiting approval. Existing staff overrides and manual-review drafts are kept.</p>}
                {workspace.health?.overdue&&<p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-950">The saved setup’s daily refresh is overdue. Save & generate now to check the current setup.</p>}
                {(workspace.health?.lastRunFailed||workspace.health?.interrupted)&&<p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-900">The most recent run failed or was interrupted. Open run history for its result before retrying.</p>}
                {feedbackTarget==="stock"&&feedback}
                <details><summary className="min-h-11 cursor-pointer py-3 text-sm font-semibold text-[#8f1838]">Saved stock details & run history</summary><div className="mt-2 space-y-3">{workspace.health&&<CoveragePanel health={workspace.health} runs={workspace.runs??[]} retryDisabled={busy||loading||dirty||!workspace.plan?.enabled||!canManage||savedReadyCount===0} onRetry={()=>void submit("run")}/>}<details className="rounded-xl bg-stone-50 p-3"><summary className="cursor-pointer text-sm font-semibold">Technical execution summary</summary><p className="mt-2 break-words text-xs leading-5">{workspace.message??"No completed generation yet."}</p><p className="mt-2 text-xs">Last attempt: {runTime(workspace.lastAttempt)}</p></details><button type="button" className={action} disabled={busy||loading||dirty||!workspace.plan?.enabled||!canManage||savedReadyCount===0} onClick={()=>void submit("run")}>Generate missing coverage now</button></div></details>
                <Link href="/admin/inventory" className="inline-flex min-h-11 items-center text-sm font-semibold text-[#8f1838] underline">Review daily stock in Inventory</Link>
            </section>
        </>}
    </main>;
}
