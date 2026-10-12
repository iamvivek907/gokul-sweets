import {getCartSnapshot} from "./cartStorage";
import {getStoredBranchSnapshot} from "./branchStorage";
import {getCustomerSnapshot,getPickupSlotSnapshot} from "./checkoutStorage";
import {getPendingOrderSnapshot} from "./pendingOrderStorage";
import {previewCheckoutQuote} from "@/services/orderApi";
import type {CreateOrderRequest} from "@/types/order";

/** A response is usable only for the checkout that requested it. */
export async function refreshCurrentCheckoutQuote(request:CreateOrderRequest,orderNumber:string|undefined,signal:AbortSignal){
 const snapshot=()=>JSON.stringify([getCartSnapshot(),getStoredBranchSnapshot(),getPickupSlotSnapshot(),getCustomerSnapshot(),getPendingOrderSnapshot()]);
 const before=snapshot();
 const quote=await previewCheckoutQuote(request,orderNumber,signal);
 return !signal.aborted&&before===snapshot()?quote:null;
}
