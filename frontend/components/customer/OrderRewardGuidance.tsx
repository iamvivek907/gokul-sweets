"use client";
import {useEffect,useState} from 'react';
import Link from 'next/link';
import {getRewards,type RewardWallet} from '@/services/loyaltyApi';
import {useStorefrontFeatures} from '@/hooks/useStorefrontFeatures';
import {subscribeCustomerIdentityChanges} from '@/lib/customerIdentityEvents';
/** Optional read: never participates in the order-history loading boundary. */
export default function OrderRewardGuidance(){
 const features=useStorefrontFeatures();const [wallet,setWallet]=useState<RewardWallet|null>(null),[revision,setRevision]=useState(0);
 useEffect(()=>subscribeCustomerIdentityChanges(()=>{setWallet(null);setRevision(v=>v+1);}),[]);
 useEffect(()=>{if(!features?.gokulRewards)return;const c=new AbortController();const timer=setTimeout(()=>{void getRewards(AbortSignal.any([c.signal,AbortSignal.timeout(6000)])).then(v=>{if(!c.signal.aborted)setWallet(v);}).catch(()=>{});},500);return()=>{clearTimeout(timer);c.abort();};},[features?.gokulRewards,revision]);
 if(!wallet||(!wallet.balance&&!wallet.pendingCoins))return null;
 const reward=wallet.rewards.find(r=>r.coins<=wallet.balance&&wallet.maximumRedemptionPercent>0);
 const earning=wallet.history.find(h=>h.kind==='EARNED'&&h.coins>0);
 const minimum=reward?Math.ceil(Math.max(reward.minimumSubtotal,reward.discount*100/wallet.maximumRedemptionPercent)*100)/100:0;
 return <aside className="order-reward-guidance" aria-label="Your earned coins"><h2>{wallet.balance} coins available</h2>{earning&&<p>Last eligible completed order: +{earning.coins} coins{(earning.badgeBonusCoins??0)>0?` (${earning.coins-(earning.badgeBonusCoins??0)} normal + ${earning.badgeBonusCoins} ${earning.badgeName??'badge'} bonus)`:''}.</p>}{reward?<p>Use {reward.coins} coins for ₹{reward.discount} off eligible product orders of ₹{minimum.toLocaleString('en-IN')} or more. Eligibility is checked at checkout; fees are excluded.</p>:<p>Keep earning towards your next saving. View rewards for current coin and amount requirements.</p>}{wallet.pendingCoins>0&&<p>{wallet.pendingCoins} coins pending paid order completion.</p>}<Link href="/profile/rewards">View rewards & benefits</Link></aside>;
}
