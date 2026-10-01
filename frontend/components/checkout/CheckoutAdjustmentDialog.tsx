"use client";
import {translate,useLanguage,T} from "@/lib/language";
import {useEffect,useRef,useState} from "react";
import {usePickupClock} from "@/hooks/usePickupClock";
import {indiaToday,validPickupDate,pickupIsFresh} from "@/lib/pickupFreshness";
import {availabilityItems,checkCartAvailability,type CartAvailability} from "@/services/availabilityApi";
import type {CartItem} from "@/types/cart";
import type {PickupSelection} from "@/types/pickup";
import styles from "./CheckoutAdjustmentDialog.module.css";

export default function CheckoutAdjustmentDialog({items,pickup,branchId,days,message,onApply,onClose}:{items:CartItem[];pickup:PickupSelection;branchId:number;days:number;message?:string;onApply:(items:CartItem[],pickup:PickupSelection)=>Promise<void>;onClose:()=>void}) {
    useLanguage();
 const ref=useRef<HTMLDialogElement>(null),clock=usePickupClock();
 const [draft,setDraft]=useState(items),[selection,setSelection]=useState(pickup),[date,setDate]=useState(pickup.date),[slots,setSlots]=useState<CartAvailability|null>(null),[error,setError]=useState(""),[busy,setBusy]=useState(false),[revision,setRevision]=useState(0);
 const today=indiaToday(new Date(clock)),max=new Date(`${today}T12:00:00+05:30`);max.setUTCDate(max.getUTCDate()+days);
 const key=JSON.stringify(availabilityItems(draft));
 useEffect(()=>{ref.current?.showModal();},[]);
 useEffect(()=>{if(!validPickupDate(date,today,days))return;const controller=new AbortController();
  checkCartAvailability(branchId,date,1,JSON.parse(key),controller.signal).then(result=>{if(!controller.signal.aborted)setSlots(result);}).catch(()=>{if(!controller.signal.aborted)setError("We couldn’t refresh available times. Retry the check before continuing.");});
  return ()=>controller.abort();
 },[branchId,date,today,days,key,revision]);
 async function apply(){if(busy)return;setError("");
  if(!draft.length){setError("Keep at least one item, or cancel your order using the order actions.");return;}
  if(!validPickupDate(date,today,days)||selection.date!==date||!pickupIsFresh(selection,new Date())){setError("Choose a current available pickup time.");return;}
  if(draft.some(i=>i.product.saleMode==="UNIT"?!Number.isInteger(i.quantity)||i.quantity<1:!Number.isInteger(i.weightGrams)||i.weightGrams!<(i.product.minimumWeightGrams??250)||(i.weightGrams!-(i.product.minimumWeightGrams??250))%(i.product.weightStepGrams??50)!==0)){setError("Enter a valid quantity or weight using the shown minimum and step.");return;}
  setBusy(true);try{await onApply(draft,selection);onClose();}catch(cause){setError(cause instanceof Error?cause.message:"Could not check this change. Your existing reservation is unchanged; please retry.");}finally{setBusy(false);}
 }
 return <dialog ref={ref} className={styles.dialog} aria-labelledby="checkout-adjust-title" onCancel={event=>{event.preventDefault();if(!busy)onClose();}}>
  <div className={styles.header}><h2 id="checkout-adjust-title"><T text="Adjust your pickup here" /></h2><button type="button" disabled={busy} onClick={onClose} aria-label="Close checkout adjustments">×</button></div>
  <p>{message||"Change quantities or choose another pickup. We’ll check availability and the updated total before saving."}</p>
  <p><T text="Your current reservation stays unchanged if the check fails. No payment is started here." /></p>
  <div className={styles.lines}>{draft.map(item=><div key={item.product.id}><label>{item.product.name}<input aria-label={`${item.product.name} ${item.product.saleMode==="WEIGHT"?"weight in grams":"quantity"}`} type="number" inputMode="numeric" disabled={busy} min={item.product.saleMode==="WEIGHT"?(item.product.minimumWeightGrams??250):1} step={item.product.saleMode==="WEIGHT"?(item.product.weightStepGrams??50):1} value={item.product.saleMode==="WEIGHT"?(item.weightGrams??0):item.quantity} onChange={event=>{const value=Number(event.target.value);setDraft(current=>current.map(i=>i.product.id===item.product.id?{...i,...(i.product.saleMode==="WEIGHT"?{weightGrams:value}:{quantity:value})}:i));}}/>{item.product.saleMode==="WEIGHT"&&<small><T text="Grams · minimum" />{item.product.minimumWeightGrams??250}<T text=", step" />{item.product.weightStepGrams??50}</small>}</label><button type="button" aria-label={`${translate("Remove")} ${item.product.name}`} disabled={busy} onClick={()=>setDraft(current=>current.filter(i=>i.product.id!==item.product.id))}><T text="Remove" /></button></div>)}</div>
  <label><T text="Pickup date" /><input type="date" min={today} max={indiaToday(max)} value={date} disabled={busy} onChange={event=>{const value=event.target.value;if(!validPickupDate(value,today,days)){setError("Choose a pickup date within the available India dates.");return;}setDate(value);setSlots(null);setError("");}}/></label>
  <label><T text="Pickup time" /><select aria-label="Adjustment pickup time" disabled={busy} value={selection.date===date?String(selection.slot.id):""} onChange={event=>{const choice=slots?.dates.find(d=>d.date===date)?.slots.find(s=>String(s.slot.id)===event.target.value);if(choice&&choice.normalAvailable)setSelection({date,slot:choice.slot,pickupType:"NORMAL"});}}><option value=""><T text="Choose an available time" /></option>{date===pickup.date&&<option value={pickup.slot.id}>{pickup.slot.startTime.slice(0,5)} · current pickup (rechecked)</option>}{slots?.dates.find(d=>d.date===date)?.slots.filter(s=>s.normalAvailable&&s.slot.id!==pickup.slot.id).map(s=><option key={s.slot.id} value={s.slot.id}>{s.slot.startTime.slice(0,5)}–{s.slot.endTime.slice(0,5)}</option>)}</select></label>
  {error&&<p role="alert" className={styles.error}>{translate(error)}</p>}
  <button type="button" disabled={busy} onClick={()=>{setError("");setRevision(value=>value+1);}}><T text="Refresh available times" /></button>
  <div className={styles.actions}><button type="button" disabled={busy} onClick={onClose}><T text="Keep current pickup" /></button><button type="button" disabled={busy} onClick={()=>void apply()}>{busy?"Checking changes…":"Check & apply changes"}</button></div>
 </dialog>;
}
