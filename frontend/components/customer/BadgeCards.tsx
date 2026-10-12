"use client";
import {useEffect,useState} from 'react';
import {getBadges,type BadgeSnapshot} from '@/services/badgeApi';
import AccountTierMark from './AccountTierMark';
import {useStorefrontFeatures} from '@/hooks/useStorefrontFeatures';
export function useBadges(identity:string|undefined,enabled:boolean){
 const [state,setState]=useState<{key:string;value:BadgeSnapshot|null;error:boolean}|null>(null),[revision,setRevision]=useState(0);
 const key=`${identity}:${revision}`;
 useEffect(()=>{if(!enabled)return;const c=new AbortController();void getBadges(AbortSignal.any([c.signal,AbortSignal.timeout(8000)])).then(value=>{if(!c.signal.aborted)setState({key,value,error:false});}).catch(()=>{if(!c.signal.aborted)setState({key,value:null,error:true});});return()=>c.abort();},[key,enabled]);
 return {value:enabled&&state?.key===key?state.value:null,error:enabled&&state?.key===key&&state.error,retry:()=>setRevision(v=>v+1)};
}
export default function BadgeCards({state}:{state:ReturnType<typeof useBadges>}){
 const features=useStorefrontFeatures(),modern=features?.futuristicStorefrontV2===true||features?.checkoutExperienceV2===true;
 return <section id="account-milestones" className="account-milestones rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8"><p className="text-xs font-bold uppercase tracking-wide text-[#b17d28]">Your Gokul journey</p><h2 className="mt-2 text-2xl font-bold">Your badges</h2><p className="mt-2 text-sm text-[#756763]">Every qualifying completed paid order brings you closer.</p>
 {state.error?<p role="status" className="mt-4">Badges are temporarily unavailable. <button type="button" onClick={state.retry} className="underline">Try again</button></p>:!state.value?<p role="status" className="mt-4">Loading your badges…</p>:<div className="badge-card-grid mt-6 grid gap-3 sm:grid-cols-3">{state.value.badges.map(b=><article key={b.id} className={`${modern?'configured-badge':'account-badge'} ${b.earned?'is-earned':'is-locked'}`}>{modern?<AccountTierMark badge={b} large earned={b.earned}/>:<span className="account-badge-icon" aria-label={b.earned?"Unlocked":"Locked"}>{b.earned?"✓":"○"}</span>}<h3>{b.name}</h3><p>{b.description}</p><strong>{b.earned?'Earned':`${Math.min(b.qualifyingOrders,b.requiredOrders)} / ${b.requiredOrders} qualifying orders`}</strong><progress value={Math.min(b.qualifyingOrders,b.requiredOrders)} max={b.requiredOrders}/><p>{b.requiredOrders} completed paid {b.requiredOrders===1?'order':'orders'} · product subtotal ₹{Number(b.minimumSubtotal).toLocaleString('en-IN')} or more each, after discounts and before fees.</p><span className="badge-benefit">{Number(b.bonusPercent)>0?`+${b.bonusPercent}% bonus coins on eligible future purchases`:'Gokul recognition'}</span></article>)}</div>}
 <p className="badge-rule">The highest qualifying tier applies; bonuses do not stack. Bonus coins follow the normal earning minimum and exclusions and round down to whole coins. Refunded orders do not qualify. The badge tick celebrates your journey.</p></section>;
}
