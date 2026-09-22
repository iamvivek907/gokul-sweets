export type RebateScope =
    | "GENERAL"
    | "CUSTOMER";


export type RebateType =
    | "PERCENTAGE"
    | "FIXED_AMOUNT"
    | "SLAB";


/*
 * Response from:
 *
 * GET /api/orders/{orderNumber}/available-rebates
 */
export interface AvailableRebateResponse {

    rebateId: number;

    code: string;

    name: string;

    description:
        string
        | null;

    scope: RebateScope;

    rebateType: RebateType;

    rebateAmount: number;

    payableAfterRebate: number;

    minimumOrderAmount:
        number
        | null;

    maximumDiscountAmount:
        number
        | null;

    nextSlabMinimumOrderAmount:
        number
        | null;

    nextSlabRebateAmount:
        number
        | null;

    amountNeededForNextSlab:
        number
        | null;
}


/*
 * Request for:
 *
 * POST /api/orders/{orderNumber}/rebate
 */
export interface ApplyRebateRequest {

    code: string;
}


/*
 * Response from:
 *
 * POST   /api/orders/{orderNumber}/rebate
 * DELETE /api/orders/{orderNumber}/rebate
 */
export interface AppliedRebateResponse {

    orderNumber: string;

    rebateCode:
        string
        | null;

    rebateName:
        string
        | null;

    rebateAmount: number;

    amountBeforeRebate: number;

    totalAmount: number;
}