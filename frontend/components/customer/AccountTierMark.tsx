import type {Badge} from '@/services/badgeApi';
/** Recognition for a configured badge, separate from identity or payment verification. */
export default function AccountTierMark({badge,large=false}:{badge:Pick<Badge,'name'|'appearance'>|null;large?:boolean}){
 if(!badge)return null;
 return <span className={`badge-medallion${large?'':' account-tier-mark'} badge-${badge.appearance.toLowerCase()}${large?' badge-large':''}`} role="img" aria-label={`${badge.name} · earned Gokul recognition`} title={`${badge.name} · earned Gokul recognition`}><svg width={large?64:24} height={large?64:24} viewBox="0 0 24 24" aria-hidden="true"><path d="m6.5 12 3.5 3.5 7.5-7.5" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"/></svg></span>;
}
