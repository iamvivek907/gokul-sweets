"use client";
import Link from "next/link";
import {useEffect,useRef,useState} from "react";
import WorkspaceDialog from "./WorkspaceDialog";
import AdminHelp from "./AdminHelp";
import {readWorkspaceDraft,writeWorkspaceDraft,clearWorkspaceDraft} from "@/lib/menuWorkspaceDraft";
import {serviceHoursRequest,jsonRequest,type ItemServiceHours,type WorkspaceItem} from "@/services/menuWorkspaceApi";
import styles from "./MenuWorkspace.module.css";
type Draft={revision:number;startsAt:string;endsAt:string};
export default function WorkspaceServiceHoursEditor({branch,item,draftKey,onClose,onSaved}:{branch:number;item:WorkspaceItem;draftKey:string;onClose:()=>void;onSaved:(notice?:string)=>void}) {
  const [draft,setDraft]=useState<Draft|null>(()=>readWorkspaceDraft<Draft>(draftKey));
  const [settings,setSettings]=useState<ItemServiceHours|null>(null);
  const [reload,setReload]=useState(0),[busy,setBusy]=useState(false),[error,setError]=useState("");
  const lock=useRef(false);
  useEffect(()=>{
    const controller=new AbortController();
    void serviceHoursRequest(branch,item.branchProductId,{signal:controller.signal}).then(value=>{
      if(controller.signal.aborted)return;
      setSettings(value);setDraft(current=>current??{revision:value.revision,startsAt:value.item.startsAt?.slice(0,5)??"",endsAt:value.item.endsAt?.slice(0,5)??""});
    }).catch(reason=>{if(!controller.signal.aborted)setError(reason instanceof Error?reason.message:"Could not load service hours.");});
    return()=>controller.abort();
  },[branch,item.branchProductId,reload]);
  useEffect(()=>{if(draft)writeWorkspaceDraft(draftKey,draft);},[draft,draftKey]);
  function close(){clearWorkspaceDraft(draftKey);onClose();}
  async function save(){
    if(!draft||!settings||lock.current)return;
    if(!!draft.startsAt!==!!draft.endsAt||draft.startsAt&&draft.startsAt===draft.endsAt){setError("Enter both service times with different start and end times, or clear both.");return;}
    lock.current=true;setBusy(true);setError("");
    try{
      const value=await serviceHoursRequest(branch,item.branchProductId,jsonRequest("PUT",{revision:draft.revision,startsAt:draft.startsAt||null,endsAt:draft.endsAt||null}));
      clearWorkspaceDraft(draftKey);
      onSaved(value.enabled?"Daily service hours saved for this item. Existing orders are unchanged.":"Hours saved. Branch service-rule enforcement is OFF; enable it on Service hours & sold out for these hours to apply.");
    }catch(reason){setError(reason instanceof Error?reason.message:"Could not save service hours. Your draft is retained.");}
    finally{lock.current=false;setBusy(false);}
  }
  function reloadSaved(){clearWorkspaceDraft(draftKey);setDraft(null);setSettings(null);setError("");setReload(value=>value+1);}
  const weekdayNames=["Mon","Tue","Wed","Thu","Fri","Sat","Sun"].filter((_,index)=>!!((settings?.item.weekdays??127)&(1<<index))).join(", ");
  return <WorkspaceDialog title={`Service hours · ${item.name}`} scope="Selected branch only · Daily pickup times in IST" busy={busy} onClose={close} footer={<><button type="button" disabled={busy} onClick={close}>Cancel</button><button type="button" className={styles.primary} disabled={busy||!settings||!draft} onClick={()=>void save()}>{busy?"Saving…":"Save service hours"}</button></>}>
    <p>Customers can book earlier for a pickup within these hours. Start is inclusive; end is exclusive.</p>
    {!settings&&!error&&<p role="status">Loading saved service hours…</p>}
    {settings&&!settings.enabled&&<p className={styles.notice}>Branch service-rule enforcement is OFF. These hours are saved but will not restrict pickups until enforcement is enabled. <Link href="/admin/menu/service-hours">Service hours & sold out</Link></p>}
    <fieldset disabled={busy||!settings||!draft}>
      <div className={styles.formGrid}>
        <label>Daily service start (IST)<input type="time" value={draft?.startsAt??""} onChange={e=>setDraft(current=>current?{...current,startsAt:e.target.value}:current)}/><small>Pickup can start at this time. Example: 08:00 means 8 AM.</small></label>
        <label>Daily service end (IST)<input type="time" value={draft?.endsAt??""} onChange={e=>setDraft(current=>current?{...current,endsAt:e.target.value}:current)}/><small>Pickup must start before this time. Example: 21:30 means 9:30 PM.</small></label>
      </div>
      <AdminHelp title="Daily service hours" description="These are pickup times, not the time customers must place their orders. Hours repeat on the item's saved service weekdays. Stock, manual availability, sold-out settings and ingredient dependencies still apply." guidance="Enter both times. Start and end must differ. An end earlier than the start means overnight service: 22:00–02:00 continues into the next day using the opening weekday. Clear both removes only the daily time restriction. Save publishes this item only; a refresh keeps your draft."/>
      <button type="button" onClick={()=>setDraft(current=>current?{...current,startsAt:"",endsAt:""}:current)}>Clear daily hours</button>
      {settings&&<p className={styles.notice}>Service days: {settings.item.weekdays===127?"Every day":weekdayNames}. {settings.item.soldOut?"This item is marked sold out. ":""}Weekdays, sold-out settings, dependencies and other items are preserved.</p>}
    </fieldset>
    {error&&<p role="alert" className={styles.error}>{error}</p>}
    <button type="button" disabled={busy} onClick={reloadSaved}>Reload saved hours</button><p className={styles.muted}>Reload discards this popup’s unsaved times and loads the latest rule.</p>
  </WorkspaceDialog>;
}
