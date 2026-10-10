"use client";
import {useEffect,useRef,useState} from 'react';
import {usePathname} from 'next/navigation';
import {useStorefrontFeatures} from '@/hooks/useStorefrontFeatures';
import {subscribeCustomerIdentityChanges} from '@/lib/customerIdentityEvents';
import {apiClient} from '@/services/apiClient';
import {claimBadge,acknowledgeBadge,type BadgeCelebration as Award} from '@/services/badgeApi';
import AccountTierMark from './AccountTierMark';
let lastAttempt=0;
/** Optional recognition runs after launch and never gates menu, OTP, checkout or order recovery. */
export default function BadgeCelebration(){
 const path=usePathname(),features=useStorefrontFeatures(),dialog=useRef<HTMLDialogElement>(null);
 const presented=useRef<Award|null>(null);
 const [award,setAward]=useState<Award|null>(null),[revision,setRevision]=useState(0);
 const allowed=features?.customerAccountHub===true&&!/^\/(checkout|orders|cart|admin|staff)(\/|$)/.test(path);
 useEffect(()=>subscribeCustomerIdentityChanges(()=>{lastAttempt=0;setAward(null);setRevision(v=>v+1);},{revalidateOnResume:true}),[]);
 useEffect(()=>{
  if(!allowed)return;
  const c=new AbortController();let active=true,verified=false;
  let timer:ReturnType<typeof setTimeout>;
  const deadline=Date.now()+90000;
  const schedule=(delay=500)=>{if(active&&Date.now()+delay<deadline)timer=setTimeout(attempt,delay);};
  const attempt=async()=>{
   if(!active||Date.now()>=deadline)return;
   if(document.visibilityState!=='visible'||document.querySelector('dialog[open],[aria-modal=true],:popover-open')){schedule();return;}
   try{
    if(!verified){
     const remaining=30000-(Date.now()-lastAttempt);
     if(remaining>0){schedule(remaining);return;}
     lastAttempt=Date.now();
     const session=await apiClient<{authenticated:boolean}>('/api/customer/identity/me',{credentials:'include',signal:AbortSignal.any([c.signal,AbortSignal.timeout(8000)])});
     if(!active||!session.authenticated)return;
     verified=true;
    }
    if(document.visibilityState!=='visible'||document.querySelector('dialog[open],[aria-modal=true],:popover-open')){schedule();return;}
    const next=await claimBadge(AbortSignal.any([c.signal,AbortSignal.timeout(8000)]));
    // Keep a leased award if another dialog opens while the request is in flight.
    if(active&&next)setAward(next);
   }catch{/* Recognition failure must not interrupt ordering. */}
  };
  schedule(2600);
  return()=>{active=false;clearTimeout(timer);c.abort();};
 },[allowed,path,revision]);
 useEffect(()=>{
  if(!allowed||!award){dialog.current?.close();return;}
  // An effect restart must not present an award already shown in this session.
  if(presented.current===award)return;
  const surface=dialog.current,deadline=Date.now()+90000;
  let timer:ReturnType<typeof setTimeout>;
  const present=()=>{
   if(Date.now()>=deadline)return;
   if(document.visibilityState!=='visible'||document.querySelector('dialog[open],[aria-modal=true],:popover-open')){
    if(Date.now()+500<deadline)timer=setTimeout(present,500);
    return;
   }
   if(!surface)return;
   surface.showModal();
   presented.current=award;
   // Acknowledge only after presentation; the server owns durable replay prevention.
   void acknowledgeBadge(award).catch(()=>{});
  };
  present();
  return()=>{clearTimeout(timer);surface?.close();};
 },[award,allowed]);
 return <dialog ref={dialog} className="badge-celebration" aria-labelledby="badge-celebration-title" onCancel={()=>setAward(null)}><div className="badge-celebration-stars" aria-hidden="true">✦ · ✧ · ✦</div>{award&&<><AccountTierMark badge={award} large/><p className="badge-celebration-kicker">A little recognition, just for you</p><h2 id="badge-celebration-title">You’ve earned {award.name}</h2><p>{award.description}</p><div className="badge-benefit">{Number(award.bonusPercent)>0?`Your current highest badge benefit: +${award.bonusPercent}% bonus coins on eligible purchases.`:'Thank you for being part of the Gokul journey.'}</div><small>Normal earning rules apply. New orders use the current Admin settings; existing orders keep their saved bonus.</small><button type="button" onClick={()=>{void acknowledgeBadge(award).catch(()=>{});setAward(null);}}>Continue</button></>}</dialog>;
}
