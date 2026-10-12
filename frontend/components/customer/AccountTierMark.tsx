import type {Badge} from '@/services/badgeApi';
/** Recognition for a configured badge, separate from identity or payment verification. */
export default function AccountTierMark({badge,large=false,earned=true}:{badge:Pick<Badge,'name'|'appearance'>|null;large?:boolean;earned?:boolean}){
 if(!badge)return null;
 const label=`${badge.name} · ${earned?'earned':'locked'} Gokul recognition`;
 return <span className={`badge-medallion${large?'':' account-tier-mark'} badge-${badge.appearance.toLowerCase()}${large?' badge-large':''}`} role="img" aria-label={label} title={label}><svg width={large?64:24} height={large?64:24} viewBox="0 0 24 24" aria-hidden="true">{earned?<path d="m6.5 12 3.5 3.5 7.5-7.5" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"/>:<><rect x="6" y="10" width="12" height="10" rx="2" fill="none" stroke="currentColor" strokeWidth="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v2" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></>}</svg></span>;
}
