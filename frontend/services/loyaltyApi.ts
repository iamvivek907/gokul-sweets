import {apiClient} from './apiClient';
import type {AppliedRebateResponse} from '@/types/rebate';
export type Reward={code:string;name:string;coins:number;discount:number;minimumSubtotal:number;eligible:boolean;unavailableReason:string|null};
export type RewardWallet={policyVersion:string;balance:number;debt:number;pendingCoins:number;completedOrders:number;rewards:Reward[];history:{id:number;kind:string;coins:number;reason:string;orderNumber:string|null;customerOrderNumber?:number|null;createdAt:string;expiresAt:string|null;badgeBonusCoins?:number;badgeName?:string|null}[];nextExpiry:string|null;maximumRedemptionPercent:number;terms:string};
export type RewardCheckout={rewards:RewardWallet;rewardCode:string|null;coins:number;rewardDiscount:number;offer:AppliedRebateResponse|null};
export const getRewards=(signal?:AbortSignal)=>apiClient<RewardWallet>('/api/customer/identity/rewards',{credentials:'include',signal});
export const getOrderRewards=(number:string,signal?:AbortSignal)=>apiClient<RewardCheckout>(`/api/orders/${encodeURIComponent(number)}/rewards`,{credentials:'include',signal});
export const selectOrderReward=(number:string,rewardCode:string|null,policyVersion:string)=>apiClient<RewardCheckout>(`/api/orders/${encodeURIComponent(number)}/rewards`,{method:'PUT',credentials:'include',body:JSON.stringify({rewardCode,policyVersion}),signal:AbortSignal.timeout(15000)});
