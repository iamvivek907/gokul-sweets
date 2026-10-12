import {apiClient} from './apiClient';
export type Badge={id:number;code:string;name:string;description:string;requiredOrders:number;minimumSubtotal:number;bonusPercent:number;appearance:'GOLD'|'SILVER'|'MAROON';active:boolean;version:number;qualifyingOrders:number;earned:boolean};
export type BadgeSnapshot={badges:Badge[];current:Badge|null};
export type BadgeCelebration={awardId:number;claimId:string;name:string;description:string;bonusPercent:number;appearance:Badge['appearance']};
const base='/api/customer/identity/badges';
export const getBadges=async(signal?:AbortSignal)=>{const value=await apiClient<BadgeSnapshot>(base,{credentials:'include',signal});if(!value||!Array.isArray(value.badges))throw new Error('Badges unavailable');return value;};
export const claimBadge=async(signal?:AbortSignal)=>{const value=await apiClient<BadgeCelebration|null>(`${base}/celebration`,{method:'POST',credentials:'include',signal});return value&&Number.isSafeInteger(value.awardId)&&typeof value.claimId==='string'?value:null;};
export const acknowledgeBadge=(value:BadgeCelebration)=>apiClient<void>(`${base}/${value.awardId}/acknowledge`,{method:'POST',credentials:'include',body:JSON.stringify({claimId:value.claimId}),signal:AbortSignal.timeout(8000)});
