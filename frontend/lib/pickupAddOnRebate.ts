import type {AvailableRebateResponse} from "../types/rebate";
/** Promote a genuine tier only when its additional rebate is smaller than the additional spend. */
export function usefulRebateTarget(offers: AvailableRebateResponse[]) {
 return offers.map(o=>({...o,nextSlabRebateAmount:o.nextSlabRebateAmount==null ? null : Math.min(o.nextSlabRebateAmount,o.maximumDiscountAmount ?? Infinity)}))
 .filter(o=>o.amountNeededForNextSlab!=null && o.amountNeededForNextSlab>0 && o.nextSlabRebateAmount!=null && o.nextSlabRebateAmount>o.rebateAmount && o.nextSlabRebateAmount-o.rebateAmount<o.amountNeededForNextSlab)
 .sort((a,b)=>a.amountNeededForNextSlab!-b.amountNeededForNextSlab!)[0] ?? null;
}
