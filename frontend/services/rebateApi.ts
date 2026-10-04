import {
    apiClient
} from "@/services/apiClient";

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
    orderNumber: string, signal?: AbortSignal
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
export async function applyBestRebate(orderNumber: string): Promise<AppliedRebateResponse> {
    return apiClient<AppliedRebateResponse>(`/api/orders/${encodeURIComponent(orderNumber)}/rebate/best`, {
        method: "POST", credentials: "include", signal: AbortSignal.timeout(10_000)
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
    code: string
): Promise<AppliedRebateResponse> {

    const request:
        ApplyRebateRequest =
        {
            code:
                code
                    .trim()
                    .toUpperCase()
        };


    return apiClient<
        AppliedRebateResponse
    >(
        `/api/orders/${encodeURIComponent(
            orderNumber
        )}/rebate`,
        {
            method:
                "POST",
            credentials: "include",

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
    orderNumber: string
): Promise<AppliedRebateResponse> {

    return apiClient<
        AppliedRebateResponse
    >(
        `/api/orders/${encodeURIComponent(
            orderNumber
        )}/rebate`,
        {
            method:
                "DELETE",
            credentials: "include"
        }
    );
}
