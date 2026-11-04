"use client";

import {useState} from "react";
import {adminFetch} from "@/services/adminApi";
import type {OccasionCatalogue, OccasionPackage} from "@/types/occasionCatalogue";

const empty: OccasionPackage = {id:null,boxId:0,name:"",description:"",imageUrl:null,recipe:[],published:false};

export default function OccasionPackageEditor({catalogue, path, authorization, busy, onSave}: {
    catalogue: OccasionCatalogue;
    path: string;
    authorization: string;
    busy: boolean;
    onSave: (preset: OccasionPackage) => Promise<boolean | undefined>;
}) {
    const [draft,setDraft]=useState(empty);
    const [uploading,setUploading]=useState(false);
    const [error,setError]=useState("");
    const disabled=busy||uploading;
    const box=catalogue.boxes.find(item=>item.id===draft.boxId);
    const pieces=draft.recipe.reduce((total,line)=>total+line.pieces,0);
    async function upload(file:File|undefined){
        if(!file||disabled)return;
        if(file.size>5*1024*1024||!["image/jpeg","image/png","image/webp"].includes(file.type)){
            setError("Choose a JPG, PNG or WebP photo up to 5 MB.");return;
        }
        setUploading(true);setError("");
        try{
            const body=new FormData();body.append("image",file);
            const response=await adminFetch(path+"/photos",authorization,{method:"POST",body});
            if(!response.ok)throw new Error();
            const {url:imageUrl}=await response.json();
            if(typeof imageUrl!=="string"||!imageUrl)throw new Error();
            setDraft(current=>({...current,imageUrl}));
        }catch{setError("The package photo could not be uploaded.");}
        finally{setUploading(false);}
    }
    return <section className="mt-6 space-y-4 rounded-xl border bg-white p-4">
        <h3 className="text-lg font-bold">Ready-made bulk packages</h3>
        <p className="text-sm">Build a recipe for one complete box. Customers can add the package for their guest count and adjust quantities before requesting a quote.</p>
        <label className="block">Edit package<select className="ml-2 rounded-lg border p-2" value={draft.id??""} disabled={disabled}
            onChange={event=>{setError("");setDraft(catalogue.packages?.find(item=>item.id===Number(event.target.value))??empty);}}>
            <option value="">New package</option>
            {(catalogue.packages??[]).map(item=><option value={item.id??""} key={item.id}>{item.name}</option>)}
        </select></label>
        <form className="space-y-3" onSubmit={event=>{event.preventDefault();void onSave(draft).then(saved=>{if(saved)setDraft(empty);});}}>
            <label className="block">Package name<input className="block w-full rounded-lg border p-2" required maxLength={100} value={draft.name}
                onChange={event=>setDraft({...draft,name:event.target.value})}/></label>
            <label className="block">Description<textarea className="block w-full rounded-lg border p-2" maxLength={300} value={draft.description}
                onChange={event=>setDraft({...draft,description:event.target.value})}/></label>
            <label className="block">Box<select className="block w-full rounded-lg border p-2" required value={draft.boxId||""}
                onChange={event=>setDraft({...draft,boxId:Number(event.target.value)})}>
                <option value="">Select a box</option>
                {catalogue.boxes.map(item=><option key={item.id} value={item.id??""}>{item.name} · up to {item.capacityPieces} pieces{item.published?"":" · unpublished"}</option>)}
            </select></label>
            <label className="block">Photo of the finished box<input className="block" type="file" accept="image/jpeg,image/png,image/webp" disabled={disabled}
                onChange={event=>{void upload(event.target.files?.[0]);event.target.value="";}}/></label>
            {draft.imageUrl&&<p className="text-sm"><a className="underline" href={draft.imageUrl} target="_blank" rel="noopener noreferrer">Preview uploaded photo</a></p>}
            <label className="block">Add a product<select className="block w-full rounded-lg border p-2" value=""
                onChange={event=>{const productId=Number(event.target.value);if(productId)setDraft({...draft,recipe:[...draft.recipe,{productId,pieces:1}]});}}>
                <option value="">Choose a published product</option>
                {catalogue.sweets.filter(item=>item.published&&!draft.recipe.some(line=>line.productId===item.id)).map(item=><option key={item.id} value={item.id}>{item.name}</option>)}
            </select></label>
            {draft.recipe.map(line=><div className="flex flex-wrap items-center gap-3" key={line.productId}>
                <label>{catalogue.sweets.find(item=>item.id===line.productId)?.name??"Unavailable item"} · pieces
                    <input className="ml-2 w-20 rounded-lg border p-2" type="number" required min={1} max={box?.capacityPieces??1000} value={line.pieces}
                        onChange={event=>setDraft({...draft,recipe:draft.recipe.map(item=>item.productId===line.productId?{...item,pieces:Number(event.target.value)}:item)})}/>
                </label>
                <button type="button" className="min-h-11 underline" onClick={()=>setDraft({...draft,recipe:draft.recipe.filter(item=>item.productId!==line.productId)})}>Remove</button>
            </div>)}
            <p className="text-sm">{pieces} pieces per box{box?` · capacity ${box.capacityPieces}`:""}</p>
            <label className="flex gap-2"><input type="checkbox" checked={draft.published} onChange={event=>setDraft({...draft,published:event.target.checked})}/>Publish package</label>
            <button className="min-h-11 rounded-xl bg-[#8f1838] px-4 font-semibold text-white disabled:opacity-50"
                disabled={disabled||!box||pieces<1||pieces>box.capacityPieces||draft.published&&(!box.published||!draft.imageUrl)}>Save package</button>
            {error&&<p role="alert">{error}</p>}
        </form>
    </section>;
}
