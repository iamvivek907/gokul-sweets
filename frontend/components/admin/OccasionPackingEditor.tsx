"use client";
import {useState} from "react";
import {adminFetch} from "@/services/adminApi";
type Calculation={packagingTotal:number;lines:{productId:number;name:string;unit:string;unitPrice:number;productionQuantity:number;cgstRate:number;sgstRate:number}[]};
export type PackingRequest={id:string;serviceDate:string;estimated?:boolean;packingFinalizedAt?:string|null;packingRevision?:number;paidAmount:number;creditReviewAmount?:number|null;originalEstimate?:number|null;calculation?:Calculation|null};
const money=(value:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(value);
export default function OccasionPackingEditor<T extends PackingRequest>({enquiry,branchId,authorization,onSaved}:{enquiry:T;branchId:number;authorization:string;onSaved:(value:T)=>void}) {
    const [quantities,setQuantities]=useState<Record<number,string>>({});const [packed,setPacked]=useState(false);const [reviewed,setReviewed]=useState(false);
    const [busy,setBusy]=useState(false);const [error,setError]=useState("");
    const quote=enquiry.calculation;
    if(!quote)return <p>Contact operations: this estimated booking has no rate snapshot.</p>;
    const actual=quote.lines.map(line=>({...line,quantity:Number(quantities[line.productId]??(line.unit==="GRAM"?"":String(line.productionQuantity)))*(line.unit==="GRAM"?1000:1)}));
    const valid=actual.every(line=>line.quantity>0&&(line.unit!=="GRAM"||/^\d+(?:\.\d{1,3})?$/.test(quantities[line.productId]??"")&&line.quantity>=250));
    const final=actual.reduce((total,line)=>{const cents=Math.round(Math.round(line.unitPrice*100)*(line.unit==="GRAM"?Math.round(line.quantity)/1000:line.quantity));const tax=Math.round(cents*(line.cgstRate+line.sgstRate)/100);return total+cents+tax;},Math.round(quote.packagingTotal*100))/100;
    async function save() {
        if(busy||!valid||!packed||!reviewed)return;setBusy(true);setError("");
        try {const response=await adminFetch(`/api/admin/branches/${branchId}/occasion-enquiries/${enquiry.id}/finalize-packing`,authorization,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({revision:enquiry.packingRevision??0,requestedPiecesPacked:packed,lines:actual.map(line=>({productId:line.productId,quantity:Math.round(line.quantity)}))})});const body=await response.json();if(!response.ok)throw new Error(body.message||body.detail||"Could not finalize packing.");onSaved(body as T);}
        catch(failure){setError(failure instanceof Error?failure.message:"Could not finalize packing.");}finally{setBusy(false);}
    }
    return <section className="mt-5 space-y-4 rounded-2xl border border-amber-300 bg-amber-50 p-5" aria-label="Final packed invoice"><h3 className="text-lg font-bold">Weigh packed food & finalize invoice</h3><p className="text-sm">Only after packing on {enquiry.serviceDate}. Agreed rates and accessory charges stay fixed. Enter the actual total kg for each weight-priced food, excluding the packaging weight.</p>
        <div className="grid gap-4 sm:grid-cols-2">{quote.lines.map(line=><label key={line.productId} className="rounded-xl border bg-white p-4 text-sm font-semibold">{line.name} · actual {line.unit==="GRAM"?"kg":"pieces"}<input type="number" min={line.unit==="GRAM"?.250:1} step={line.unit==="GRAM"?.001:1} readOnly={line.unit!=="GRAM"} value={quantities[line.productId]??(line.unit==="GRAM"?"":String(line.productionQuantity))} onChange={event=>{setQuantities(current=>({...current,[line.productId]:event.target.value}));setReviewed(false);}} className="mt-2 w-full rounded-lg border p-3" /><span className="mt-2 block text-xs font-normal">Booking estimate: {line.unit==="GRAM"?line.productionQuantity/1000:line.productionQuantity} {line.unit==="GRAM"?"kg":"pieces"} · agreed {money(line.unitPrice)} per {line.unit==="GRAM"?"kg":"piece"} before tax.</span></label>)}</div>
        <label className="flex gap-2 text-sm"><input type="checkbox" checked={packed} onChange={event=>setPacked(event.target.checked)} />All requested piece counts, boxes and accessories have been physically packed.</label>
        {valid&&<div className="rounded-xl border bg-white p-4 text-sm"><p>Booking estimate {money(enquiry.originalEstimate??0)} → final invoice {money(final)}</p><p>Already paid {money(enquiry.paidAmount)} · {final>=enquiry.paidAmount?`remaining ${money(final-enquiry.paidAmount)}`:`credit needing finance review ${money(enquiry.paidAmount-final)}`}</p><label className="mt-3 flex gap-2"><input type="checkbox" checked={reviewed} onChange={event=>setReviewed(event.target.checked)} />I reviewed the measured weights and this final invoice. It becomes immutable and the customer is notified.</label></div>}
        {error&&<p role="alert" className="rounded-lg bg-red-50 p-3 text-red-800">{error}</p>}
        <button type="button" disabled={busy||!valid||!packed||!reviewed} onClick={()=>void save()} className="min-h-12 rounded-xl bg-[#143936] px-5 font-bold text-white disabled:opacity-50">Finalize packed invoice</button>
    </section>;
}
