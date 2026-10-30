"use client";
import Image from 'next/image';
import {useEffect,useId,useRef,useState} from 'react';
import {uploadMealImage} from '@/services/mealImageApi';
import styles from './AdminMeals.module.css';
export default function MealPhotoUpload({branchId,value,onChange,onBusy,disabled=false}:{branchId:number;value:string;onChange:(url:string)=>void;onBusy:(busy:boolean)=>void;disabled?:boolean}){
 const id=useId(),request=useRef<AbortController|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState(''),[failedUrl,setFailedUrl]=useState<string|null>(null);
 useEffect(()=>()=>{request.current?.abort();onBusy(false);},[onBusy]);
 async function upload(file:File){
  if(request.current||disabled)return;
  const controller=new AbortController();request.current=controller;setBusy(true);onBusy(true);setError('');setNotice('');
  try{const url=await uploadMealImage(branchId,file,AbortSignal.any([controller.signal,AbortSignal.timeout(30000)]));if(controller.signal.aborted)return;onChange(url);setFailedUrl(null);setNotice('Photo uploaded. Save the meal to use this photo.');}
  catch(reason){if(!controller.signal.aborted)setError(reason instanceof Error?reason.message:'Photo upload failed. Try again.');}
  finally{if(request.current===controller){request.current=null;if(!controller.signal.aborted){setBusy(false);onBusy(false);}}}
 }
 return <div className={styles.photo} aria-busy={busy}>
  <label htmlFor={id}>Meal photo</label><p id={`${id}-help`} className={styles.fieldHelp}>Upload a JPG, PNG or WebP up to 5 MB. A landscape photo works well. This changes only the meal photo, not the dish photos.</p>
  <input id={id} type="file" accept="image/jpeg,image/png,image/webp" disabled={busy||disabled} aria-describedby={`${id}-help`} onChange={event=>{const file=event.currentTarget.files?.[0];event.currentTarget.value='';if(file)void upload(file);}}/>
  {busy&&<p role="status">Uploading photo…</p>}{error&&<p role="alert" className={styles.error}>{error}</p>}{notice&&<p role="status">{notice}</p>}
  {value&&failedUrl!==value&&<Image unoptimized className={styles.photoPreview} width={480} height={300} src={value} alt="Meal photo preview" onError={()=>setFailedUrl(value)}/>}
  {value&&failedUrl===value&&<p role="status">Photo preview could not load. Check the URL or upload another photo.</p>}
  <label htmlFor={`${id}-url`}>Image URL (optional)</label><input id={`${id}-url`} type="text" maxLength={600} value={value} disabled={busy||disabled} aria-describedby={`${id}-url-help`} placeholder="Or paste an existing HTTPS photo URL" onChange={event=>{setError('');setNotice('');onChange(event.target.value);}}/>
  <p id={`${id}-url-help`} className={styles.fieldHelp}>Uploads fill this automatically. Leave blank to use the first dish’s photo. Save the meal to apply changes.</p>
  {value&&<button type="button" disabled={busy||disabled} onClick={()=>{setError('');onChange('');setNotice('Photo removed from this draft. Save the meal to apply.');}}>Remove meal photo</button>}
 </div>;
}
