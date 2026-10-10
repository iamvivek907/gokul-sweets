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
 const [award,setAward]=useState<Award|null>(null),[revision,setRevision]=useState(0);
 const allowed=features?.customerAccountHub===true&&!/^\/(checkout|orders|cart|admin|staff)(\/|$)/.test(path);
 useEffect(()=>subscribeCustomerIdentityChanges(()=>{lastAttempt=0;setAward(null);setRevision(v=>v+1);},{revalidateOnResume:true}),[]);
 useEffect(()=>{
  if(!allowed)return;
  const c=new AbortController();let active=true;
  const timer=setTimeout(async()=>{
   if(document.visibilityState!=='visible'||document.querySelector('dialog[open],[aria-modal=true]')||Date.now()-lastAttempt<30000)return;
   lastAttempt=Date.now();
   try{
    const signal=AbortSignal.any([c.signal,AbortSignal.timeout(8000)]);
    const session=await apiClient<{authenticated:boolean}>('/api/customer/identity/me',{credentials:'include',signal});
    if(!active||!session.authenticated||document.querySelector('dialog[open],[aria-modal=true]'))return;
    const next=await claimBadge(signal);
    if(active&&next&&!document.querySelector('dialog[open],[aria-modal=true]'))setAward(next);
   }catch{/* Recognition failure must not interrupt ordering. */}
  },2600);
  return()=>{active=false;clearTimeout(timer);c.abort();};
 },[allowed,path,revision]);
 useEffect(()=>{
  if(!allowed||!award){dialog.current?.close();return;}
  if(document.visibilityState!=='visible'||document.querySelector('dialog[open],[aria-modal=true]'))return;
  const surface=dialog.current;
  surface?.showModal();
  // Acknowledge after the presentation is actually visible; the server owns replay prevention.
  void acknowledgeBadge(award).catch(()=>{});
  return()=>surface?.close();
 },[award,allowed]);
 return <dialog ref={dialog} className="badge-celebration" aria-labelledby="badge-celebration-title" onCancel={()=>setAward(null)}><div className="badge-celebration-stars" aria-hidden="true">✦ · ✧ · ✦</div>{award&&<><AccountTierMark badge={award} large/><p className="badge-celebration-kicker">A little recognition, just for you</p><h2 id="badge-celebration-title">You’ve earned {award.name}</h2><p>{award.description}</p><div className="badge-benefit">{Number(award.bonusPercent)>0?`Your current highest badge benefit: +${award.bonusPercent}% bonus coins on eligible purchases.`:'Thank you for being part of the Gokul journey.'}</div><small>Normal earning rules apply. New orders use the current Admin settings; existing orders keep their saved bonus.</small><button type="button" onClick={()=>{void acknowledgeBadge(award).catch(()=>{});setAward(null);}}>Continue</button></>}</dialog>;
}
