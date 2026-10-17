"use client";
import {useEffect, useState} from "react";
import {adminManagementApi} from "@/services/adminManagementApi";
type Choice = {id:number; name:string; categoryId:number|null};
type Catalog = {categories:Choice[]; products:Choice[]; selectedProducts:Choice[]; hasMore:boolean};
export type OfferExclusions = {excludedCategoryIds:number[]; excludedProductIds:number[]};
export default function OfferExclusionsPicker({authorization,branchId,value,onChange}:{authorization:string;branchId:string;value:OfferExclusions;onChange:(value:OfferExclusions)=>void}) {
 const [search,setSearch]=useState(""),[page,setPage]=useState(0),[retry,setRetry]=useState(0);
 const [result,setResult]=useState<{key:string;catalog:Catalog}|null>(null),[error,setError]=useState<{key:string;message:string}|null>(null);
 const key=JSON.stringify([branchId,search,page,value.excludedProductIds,retry]);
 useEffect(()=>{
  if(!branchId)return;
  const controller=new AbortController();
  const timer=setTimeout(()=>{
   const params=new URLSearchParams({search,page:String(page)});
   if(branchId!=="all")params.set("branchId",branchId);
   if(value.excludedProductIds.length)params.set("selected",value.excludedProductIds.join(","));
   void adminManagementApi<Catalog>(`/api/admin/rebates/exclusion-options?${params}`,authorization,{signal:controller.signal})
    .then(catalog=>{if(!controller.signal.aborted)setResult({key,catalog});})
    .catch(e=>{if(!controller.signal.aborted)setError({key,message:e.message});});
  },200);
  return()=>{clearTimeout(timer);controller.abort();};
 },[authorization,branchId,search,page,key,retry,value.excludedProductIds]);
 const catalog=result?.key===key?result.catalog:null;
 function toggle(field:keyof OfferExclusions,id:number){const ids=value[field];if(!ids.includes(id)&&ids.length>=500)return;onChange({...value,[field]:ids.includes(id)?ids.filter(x=>x!==id):[...ids,id]});}
 return <section className="space-y-3 rounded-xl border border-[#eadfd6] p-4 sm:col-span-2" aria-label="Offer exclusions">
  <h3 className="font-bold">Excluded categories & items</h3>
  <p className="text-sm">Excluded products stay full price and do not count toward this offer’s minimum spend or slabs. Category exclusions include future products in that category. Item exclusions apply to the selected variant. Either rule excludes the item.</p>
  <p className="text-sm">With exclusions, only eligible product spend after proportional loyalty savings counts; taxes, pickup and payment fees do not count. Loyalty earning rules are configured separately.</p>
  {!branchId?<p>Choose a branch first.</p>:<>
   {error?.key===key?<p role="alert">{error.message} <button type="button" className="underline" onClick={()=>setRetry(n=>n+1)}>Retry exclusion choices</button></p>:!catalog&&<p role="status">Loading exclusion choices…</p>}
   <fieldset><legend className="font-semibold">Categories ({value.excludedCategoryIds.length} selected)</legend><div className="flex flex-wrap gap-3">{result?.catalog.categories.map(c=><label key={c.id} className="flex min-h-11 items-center gap-2"><input type="checkbox" checked={value.excludedCategoryIds.includes(c.id)} onChange={()=>toggle("excludedCategoryIds",c.id)}/>{c.name}</label>)}</div></fieldset>
   {!!value.excludedCategoryIds.length&&<button type="button" className="min-h-11 underline" onClick={()=>onChange({...value,excludedCategoryIds:[]})}>Clear category exclusions</button>}
   <label className="block">Search items<input className="block min-h-11 w-full rounded-xl border px-3" maxLength={100} value={search} onChange={e=>{setSearch(e.target.value);setPage(0);}}/></label>
   <fieldset><legend className="font-semibold">Items ({value.excludedProductIds.length} selected)</legend><div className="grid max-h-64 gap-2 overflow-auto sm:grid-cols-2">{catalog?.products.map(p=><label key={p.id} className="flex min-h-11 items-center gap-2"><input type="checkbox" checked={value.excludedProductIds.includes(p.id)} onChange={()=>toggle("excludedProductIds",p.id)}/>{p.name}{value.excludedCategoryIds.includes(p.categoryId??-1)&&<small>Already excluded by category</small>}</label>)}</div></fieldset>
   {catalog&&!catalog.products.length&&<p>No matching items.</p>}
   <div className="flex gap-4"><button type="button" disabled={!catalog||page===0} className="min-h-11 underline disabled:opacity-40" onClick={()=>setPage(p=>p-1)}>Previous items</button><button type="button" disabled={!catalog?.hasMore} className="min-h-11 underline disabled:opacity-40" onClick={()=>setPage(p=>p+1)}>Next items</button></div>
   {!!value.excludedProductIds.length&&<div><p className="text-sm">Selected item exclusions</p><div className="flex flex-wrap gap-2">{value.excludedProductIds.map(id=><button type="button" className="min-h-11 rounded border px-3" key={id} onClick={()=>toggle("excludedProductIds",id)}>Remove {catalog?.selectedProducts.find(p=>p.id===id)?.name??`item #${id}`}</button>)}</div></div>}
   {(value.excludedCategoryIds.length>=500||value.excludedProductIds.length>=500)&&<p>Maximum 500 selections per list. Use categories for whole groups.</p>}
  </>}
 </section>;
}
