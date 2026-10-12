"use client";
import {useEffect,useState} from 'react';
import {getOrderRewards,selectOrderReward,type RewardCheckout} from '@/services/loyaltyApi';
import RewardPicker from './RewardPicker';
import {T} from '@/lib/language';
export default function OrderRewards({orderNumber,disabled,onBusy,onChanged}:{orderNumber:string;disabled:boolean;onBusy:(value:boolean)=>void;onChanged:(value:RewardCheckout)=>Promise<void>}){
 const [value,setValue]=useState<RewardCheckout|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[revision,setRevision]=useState(0);
 useEffect(()=>{const c=new AbortController();void getOrderRewards(orderNumber,AbortSignal.any([c.signal,AbortSignal.timeout(15000)])).then(v=>{if(!c.signal.aborted){setValue(v);setError('');}}).catch(()=>{if(!c.signal.aborted)setError('Could not check your earned coins. Try again.');});return()=>c.abort();},[orderNumber,revision]);
 async function select(code:string|null){if(busy||disabled)return;setBusy(true);onBusy(true);setError('');try{const result=await selectOrderReward(orderNumber,code,value?.rewards.policyVersion??'');setValue(result);await onChanged(result);}catch(failure){setError(failure instanceof Error?failure.message:'Could not apply this reward. Try again.');}finally{setBusy(false);onBusy(false);}}
 return <div>{value?<RewardPicker wallet={value.rewards} selected={value.rewardCode} discount={Number(value.rewardDiscount)} busy={busy||disabled} onSelect={code=>void select(code)}/>:!error&&<p role="status"><T text="Checking earned coins…" /></p>}{error&&<div role="alert"><p>{error}</p><button type="button" disabled={busy} onClick={()=>setRevision(v=>v+1)}><T text="Try again" /></button></div>}</div>;
}
