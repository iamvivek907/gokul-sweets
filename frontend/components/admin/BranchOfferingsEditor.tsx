"use client";
import {useEffect,useState} from "react";
import {adminManagementApi} from "@/services/adminManagementApi";
import type {BranchOffering,BranchOfferingsSnapshot} from "@/types/branchDiscovery";
export default function BranchOfferingsEditor({branchId,authorization}:{branchId:number;authorization:string}) {
 const path=`/api/admin/branches/${branchId}/offerings`;
 const [snapshot,setSnapshot]=useState<BranchOfferingsSnapshot|null>(null),[draft,setDraft]=useState<BranchOffering[]>([]),[busy,setBusy]=useState(false),[error,setError]=useState(""),[message,setMessage]=useState(""),[reload,setReload]=useState(0);
 useEffect(()=>{const c=new AbortController();adminManagementApi<BranchOfferingsSnapshot>(path,authorization,{signal:c.signal}).then(s=>{if(!c.signal.aborted){setSnapshot(s);setDraft(s.draft);setError("");}}).catch(e=>{if(!c.signal.aborted)setError(e.message);});return()=>c.abort();},[path,authorization,reload]);
 async function save(publish:boolean){if(!snapshot||busy)return;setBusy(true);setError("");setMessage("");try{
  let next=await adminManagementApi<BranchOfferingsSnapshot>(path,authorization,{method:"PUT",headers:{"Content-Type":"application/json","If-Match":String(snapshot.version)},body:JSON.stringify({offerings:draft})});setSnapshot(next);
  if(publish){next=await adminManagementApi<BranchOfferingsSnapshot>(`${path}/publish`,authorization,{method:"POST",headers:{"If-Match":String(next.version)}});setSnapshot(next);}
  setMessage(publish?"Branch offerings published. Customers can now see these details.":"Draft saved. Published offerings stay unchanged.");
 }catch(e){setError(e instanceof Error?e.message:"Could not save. Reload and try again.");}finally{setBusy(false);}}
 function update(index:number,key:keyof BranchOffering,value:string){setDraft(d=>d.map((item,i)=>i===index?{...item,[key]:value}:item));setMessage("");}
 function move(index:number,offset:number){setDraft(d=>{const next=[...d];[next[index],next[index+offset]]=[next[index+offset],next[index]];return next;});}
 return <section className="mt-6 rounded-2xl border border-[#eadfd6] bg-white p-5" aria-label="What this branch offers">
 <h3 className="text-lg font-bold">What this branch offers</h3><p className="mt-2 text-sm text-[#756763]">Describe only services and specialities genuinely available at this branch. Up to 12 cards, shown in the order below. These descriptions do not enable pickup, delivery or occasion ordering.</p>
 {!snapshot?<p role="status" className="mt-4">Loading branch offerings…</p>:<fieldset disabled={busy} className="mt-4 space-y-4">
 {draft.map((item,index)=><div key={index} className="rounded-xl border p-4"><div className="grid gap-3 sm:grid-cols-2"><label className="text-sm font-semibold">Offering {index+1} title<input value={item.title} maxLength={80} onChange={e=>update(index,"title",e.target.value)} className="mt-2 block min-h-11 w-full rounded-lg border p-3" placeholder="For example, a speciality you actually offer"/></label><label className="text-sm font-semibold">Offering {index+1} description<textarea value={item.description} maxLength={240} rows={3} onChange={e=>update(index,"description",e.target.value)} className="mt-2 block w-full rounded-lg border p-3"/></label></div><div className="mt-3 flex flex-wrap gap-3"><button type="button" disabled={index===0} onClick={()=>move(index,-1)} className="min-h-11 rounded-lg border px-3 disabled:opacity-40">Move up</button><button type="button" disabled={index===draft.length-1} onClick={()=>move(index,1)} className="min-h-11 rounded-lg border px-3 disabled:opacity-40">Move down</button><button type="button" onClick={()=>setDraft(d=>d.filter((_,i)=>i!==index))} className="min-h-11 px-3 text-red-800 underline">Remove offering {index+1}</button></div></div>)}
 <div className="flex flex-wrap gap-3"><button type="button" disabled={draft.length>=12} onClick={()=>setDraft(d=>[...d,{title:"",description:""}])} className="min-h-11 rounded-xl border px-4">Add offering</button><button type="button" onClick={()=>void save(false)} className="min-h-11 rounded-xl border px-4">Save offerings draft</button><button type="button" onClick={()=>void save(true)} className="min-h-11 rounded-xl bg-[#7a1625] px-4 font-bold text-white">Publish offerings</button></div><p className="text-xs text-[#756763]">Published cards: {snapshot.published.length}. To remove all public cards, remove the draft cards and publish the empty list. Reload discards unsaved edits.</p>
 </fieldset>}
 {error&&<p role="alert" className="mt-3 text-sm text-red-800">{error}</p>}{message&&<p role="status" className="mt-3 text-sm text-green-800">{message}</p>}<button type="button" disabled={busy} onClick={()=>setReload(n=>n+1)} className="mt-3 min-h-11 underline">Reload offerings</button>
 </section>;
}
