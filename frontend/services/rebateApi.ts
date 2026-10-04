import {CheckoutSavingsUncertainError} from "@/lib/checkoutRefresh";
import {ApiError,apiClient} from "@/services/apiClient";

import type {
    AppliedRebateResponse,
    ApplyRebateRequest,
    AvailableRebateResponse
} from "@/types/rebate";


/*
 * =========================================================
 * AVAILABLE REBATES
 * =========================================================
 *
 * Backend:
 *
 * GET /api/orders/{orderNumber}/available-rebates
 *
 * The backend is authoritative for:
 *
 * - branch eligibility
 * - customer eligibility
 * - validity dates
 * - minimum order amount
 * - usage limits
 * - percentage calculations
 * - fixed rebates
 * - slab calculations
 * - payable amount
 */
export async function getAvailableRebates(
    orderNumber: string, signal: AbortSignal = AbortSignal.timeout(15000)
): Promise<AvailableRebateResponse[]> {

    return apiClient<
        AvailableRebateResponse[]
    >(
        `/api/orders/${encodeURIComponent(
            orderNumber
        )}/available-rebates`,
        {credentials: "include", signal}
    );
}

/** The backend locks the order, preserves stronger selected offers and revalidates eligibility. */
export async function applyBestRebate(orderNumber: string, signal:AbortSignal=AbortSignal.timeout(10000)): Promise<AppliedRebateResponse> {
    return mutateRebate(`/api/orders/${encodeURIComponent(orderNumber)}/rebate/best`, {
        method: "POST", credentials: "include", signal
    });
}


/*
 * =========================================================
 * APPLY REBATE
 * =========================================================
 *
 * Backend:
 *
 * POST /api/orders/{orderNumber}/rebate
 *
 * Request:
 *
 * {
 *     code: "SWEET50"
 * }
 *
 * Do not calculate the discount on the frontend.
 *
 * The backend response contains the authoritative
 * rebate amount and new order total.
 */
export async function applyRebate(
    orderNumber: string,
    code: string,
    signal:AbortSignal=AbortSignal.timeout(15000)
): Promise<AppliedRebateResponse> {

    const request:
        ApplyRebateRequest =
        {
            code:
                code
                    .trim()
                    .toUpperCase()
        };


    return mutateRebate(
        `/api/orders/${encodeURIComponent(
            orderNumber
        )}/rebate`,
        {
            method:
                "POST",
            credentials: "include",
            signal,

            body:
                JSON.stringify(
                    request
                )
        }
    );
}


/*
 * =========================================================
 * REMOVE REBATE
 * =========================================================
 *
 * Backend:
 *
 * DELETE /api/orders/{orderNumber}/rebate
 *
 * Removing a rebate restores the server-calculated
 * amountBeforeRebate as the order total.
 */
export async function removeRebate(
    orderNumber: string, signal:AbortSignal=AbortSignal.timeout(15000)
): Promise<AppliedRebateResponse> {

    return mutateRebate(
        `/api/orders/${encodeURIComponent(
            orderNumber
        )}/rebate`,
        {
            method:
                "DELETE",
            credentials: "include",
            signal
        }
    );
}

async function mutateRebate(path:string,options:RequestInit):Promise<AppliedRebateResponse>{
 try{return await apiClient<AppliedRebateResponse>(path,options);}
 catch(error){
  if(options.signal?.aborted||!(error instanceof ApiError)||error.status===0||error.status>=500)throw new CheckoutSavingsUncertainError();
  throw error;
 }
}
