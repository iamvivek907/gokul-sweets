"use client";
import {useEffect,useId,useState} from 'react';
import Link from 'next/link';
import {getRewards,type RewardWallet} from '@/services/loyaltyApi';
import {useStorefrontFeatures} from '@/hooks/useStorefrontFeatures';
import {subscribeCustomerIdentityChanges} from '@/lib/customerIdentityEvents';
/** Optional reads never participate in the order-history loading boundary. */
export default function OrderRewardGuidance(){
 const features=useStorefrontFeatures(),[revision,setRevision]=useState(0);
 useEffect(()=>subscribeCustomerIdentityChanges(()=>setRevision(v=>v+1)),[]);
 // Unmounting discards the wallet and aborts reads on pause or an identity change.
 return features?.gokulRewards===true?<EnabledRewardGuidance key={revision}/>:null;
}
function EnabledRewardGuidance(){
 const [wallet,setWallet]=useState<RewardWallet|null>(null),[expanded,setExpanded]=useState(false);
 const detailsId=useId();
 useEffect(()=>{
  const c=new AbortController();
  const timer=setTimeout(()=>{void getRewards(AbortSignal.any([c.signal,AbortSignal.timeout(6000)])).then(v=>{if(!c.signal.aborted)setWallet(v);}).catch(()=>{});},500);
  return()=>{clearTimeout(timer);c.abort();};
 },[]);
 const reward=wallet?.rewards.find(r=>r.coins<=wallet.balance&&wallet.maximumRedemptionPercent>0);
 const earning=wallet?.history.find(h=>h.kind==='EARNED'&&h.coins>0);
 const minimum=reward&&wallet?Math.ceil(Math.max(reward.minimumSubtotal,reward.discount*100/wallet.maximumRedemptionPercent)*100)/100:0;
 return <aside className="order-reward-guidance" aria-label="Your earned coins">
  <div className="order-reward-summary"><h2>{wallet?`${wallet.balance} coins available`:'Coins & benefits'}</h2><button type="button" disabled={!wallet} aria-expanded={expanded} aria-controls={detailsId} onClick={()=>setExpanded(v=>!v)}>{expanded?'Hide':'Details'}</button></div>
  {wallet&&expanded&&<div id={detailsId} className="order-reward-details">
   {earning&&<p>Last eligible completed order: +{earning.coins} coins{(earning.badgeBonusCoins??0)>0?` (${earning.coins-(earning.badgeBonusCoins??0)} normal + ${earning.badgeBonusCoins} ${earning.badgeName??'badge'} bonus)`:''}.</p>}
   {reward?<p>Use {reward.coins} coins for ₹{reward.discount} off eligible product orders of ₹{minimum.toLocaleString('en-IN')} or more. Eligibility is checked at checkout; fees are excluded.</p>:<p>Keep earning towards your next saving. View rewards for current coin and amount requirements.</p>}
   {wallet.pendingCoins>0&&<p>{wallet.pendingCoins} coins pending paid order completion.</p>}
   <Link href="/profile/rewards">View rewards & benefits</Link>
  </div>}
 </aside>;
}
