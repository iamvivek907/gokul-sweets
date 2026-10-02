"use client";
import "./reward-controls.css";
import type {RewardWallet} from '@/services/loyaltyApi';
import {T} from '@/lib/language';
const money=(n:number)=>new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR'}).format(n);
export default function RewardPicker({wallet,selected,discount=0,busy,onSelect}:{wallet:RewardWallet;selected:string|null;discount?:number;busy:boolean;onSelect:(code:string|null)=>void}){
 return <section className="checkout-rewards" aria-label="Earned rewards" aria-busy={busy}><div className="checkout-savings-heading"><div><h2><T text="Your earned coins" /></h2><p>{wallet.balance} <T text="coins available" />{wallet.pendingCoins>0&&<> · {wallet.pendingCoins} <T text="pending completion" /></>}</p></div>{selected&&<button type="button" disabled={busy} onClick={()=>onSelect(null)}><T text="Remove reward" /></button>}</div>
 {selected&&<p role="status"><T text="Reward applied" /> · −{money(discount)}</p>}
 <div className="checkout-reward-choices">{wallet.rewards.map(reward=><button type="button" key={reward.code} disabled={busy||(!reward.eligible&&reward.code!==selected)} aria-pressed={selected===reward.code} onClick={()=>{if(reward.code!==selected)onSelect(reward.code);}}><strong>{reward.name}</strong><span>{reward.coins} <T text="coins" /> · <T text="Minimum" /> {money(reward.minimumSubtotal)}</span>{reward.code===selected?<small><T text="Applied" /></small>:!reward.eligible&&<small>{reward.unavailableReason}</small>}</button>)}</div>
 {busy&&<p role="status"><T text="Checking your rewards and eligible offers…" /></p>}
 <details><summary><T text="Rewards terms" /></summary><p>{wallet.terms}</p></details></section>;
}
