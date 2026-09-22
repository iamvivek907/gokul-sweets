import {
    adminFetch
} from "@/services/adminApi";

import type {
    AdminBatchPreparationResponse,
    AdminOrderDetail,
    AdminOrderPageResponse,
    AdminOrderQueueCounts,
    AdminOrderQueueResponse,
    AdminOrderStatusUpdateRequest,
    AdminStartNextPreparationRequest,
    AdminStartSelectedPreparationRequest,
    OrderStatus
} from "@/types/adminOrders";


async function getErrorMessage(
    response: Response,
    fallback: string
) {

    try {

        const body =
            await response.json();


        if (
            typeof body?.message
            === "string"
            &&
            body.message.trim()
        ) {

            return body.message;
        }


        if (
            typeof body?.error
            === "string"
            &&
            body.error.trim()
        ) {

            return body.error;
        }

    } catch {

        /*
         * Ignore JSON parsing failure.
         */
    }


    return fallback;
}


/*
 * =========================================================
 * GENERAL ORDERS
 * =========================================================
 */

export async function getAdminOrders(
    branchId: number,
    authorization: string,
    options?: {
        status?: OrderStatus;
        page?: number;
        size?: number;
        signal?: AbortSignal;
    }
): Promise<AdminOrderPageResponse> {

    const page =
        options?.page
        ?? 0;


    const size =
        options?.size
        ?? 20;


    const params =
        new URLSearchParams();


    params.set(
        "branchId",
        String(
            branchId
        )
    );


    params.set(
        "page",
        String(
            page
        )
    );


    params.set(
        "size",
        String(
            size
        )
    );


    if (
        options?.status
    ) {

        params.set(
            "status",
            options.status
        );
    }


    const response =
        await adminFetch(
            `/api/admin/orders?${params.toString()}`,
            authorization,
            {
                method:
                    "GET",

                signal:
                    options?.signal,

                cache:
                    "no-store"
            }
        );


    if (
        response.status
        === 401
    ) {

        throw new Error(
            "Your admin session is no longer valid."
        );
    }


    if (
        response.status
        === 403
    ) {

        throw new Error(
            "You do not have permission to view orders for this branch."
        );
    }


    if (
        response.status
        === 400
    ) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to load orders because the request is invalid."
            )
        );
    }


    if (!response.ok) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to load orders."
            )
        );
    }


    const result:
        AdminOrderPageResponse =
        await response.json();


    return result;
}


/*
 * =========================================================
 * OPERATIONAL PREPARATION QUEUE
 * =========================================================
 */

export async function getAdminPreparationQueue(
    branchId: number,
    authorization: string,
    options?: {
        limit?: number;
        signal?: AbortSignal;
    }
): Promise<AdminOrderQueueResponse> {

    const params =
        new URLSearchParams();


    params.set(
        "branchId",
        String(
            branchId
        )
    );


    if (
        options?.limit
        !== undefined
    ) {

        params.set(
            "limit",
            String(
                options.limit
            )
        );
    }


    const response =
        await adminFetch(
            `/api/admin/orders/queue?${params.toString()}`,
            authorization,
            {
                method:
                    "GET",

                signal:
                    options?.signal,

                cache:
                    "no-store"
            }
        );


    if (
        response.status
        === 401
    ) {

        throw new Error(
            "Your admin session is no longer valid."
        );
    }


    if (
        response.status
        === 403
    ) {

        throw new Error(
            "You do not have permission to view the preparation queue."
        );
    }


    if (
        response.status
        === 400
    ) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to load the preparation queue."
            )
        );
    }


    if (!response.ok) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to load the preparation queue."
            )
        );
    }


    const result:
        AdminOrderQueueResponse =
        await response.json();


    return result;
}


/*
 * =========================================================
 * OPERATIONAL QUEUE COUNTS
 * =========================================================
 */

export async function getAdminPreparationQueueCounts(
    branchId: number,
    authorization: string,
    signal?: AbortSignal
): Promise<AdminOrderQueueCounts> {

    const params =
        new URLSearchParams();


    params.set(
        "branchId",
        String(
            branchId
        )
    );


    const response =
        await adminFetch(
            `/api/admin/orders/queue/counts?${params.toString()}`,
            authorization,
            {
                method:
                    "GET",

                signal,

                cache:
                    "no-store"
            }
        );


    if (
        response.status
        === 401
    ) {

        throw new Error(
            "Your admin session is no longer valid."
        );
    }


    if (
        response.status
        === 403
    ) {

        throw new Error(
            "You do not have permission to view order queue counts."
        );
    }


    if (
        response.status
        === 400
    ) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to load order queue counts."
            )
        );
    }


    if (!response.ok) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to load order queue counts."
            )
        );
    }


    const result:
        AdminOrderQueueCounts =
        await response.json();


    return result;
}


/*
 * =========================================================
 * START NEXT N
 * =========================================================
 */

export async function startNextAdminOrders(
    request: AdminStartNextPreparationRequest,
    authorization: string
): Promise<AdminBatchPreparationResponse> {

    const response =
        await adminFetch(
            "/api/admin/orders/queue/start-next",
            authorization,
            {
                method:
                    "POST",

                headers: {
                    "Content-Type":
                        "application/json"
                },

                body:
                    JSON.stringify(
                        request
                    )
            }
        );


    if (
        response.status
        === 401
    ) {

        throw new Error(
            "Your admin session is no longer valid."
        );
    }


    if (
        response.status
        === 403
    ) {

        throw new Error(
            "You do not have permission to start order preparation."
        );
    }


    if (
        response.status
        === 400
    ) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to start the next orders."
            )
        );
    }


    if (
        response.status
        === 409
    ) {

        throw new Error(
            await getErrorMessage(
                response,
                "The preparation queue changed while the batch was being processed."
            )
        );
    }


    if (!response.ok) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to start the next orders."
            )
        );
    }


    const result:
        AdminBatchPreparationResponse =
        await response.json();


    return result;
}


/*
 * =========================================================
 * START SELECTED
 * =========================================================
 */

export async function startSelectedAdminOrders(
    request: AdminStartSelectedPreparationRequest,
    authorization: string
): Promise<AdminBatchPreparationResponse> {

    const response =
        await adminFetch(
            "/api/admin/orders/queue/start-selected",
            authorization,
            {
                method:
                    "POST",

                headers: {
                    "Content-Type":
                        "application/json"
                },

                body:
                    JSON.stringify(
                        request
                    )
            }
        );


    if (
        response.status
        === 401
    ) {

        throw new Error(
            "Your admin session is no longer valid."
        );
    }


    if (
        response.status
        === 403
    ) {

        throw new Error(
            "You do not have permission to start selected orders."
        );
    }


    if (
        response.status
        === 400
    ) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to start the selected orders."
            )
        );
    }


    if (
        response.status
        === 409
    ) {

        throw new Error(
            await getErrorMessage(
                response,
                "One or more selected orders changed while the batch was being processed."
            )
        );
    }


    if (!response.ok) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to start the selected orders."
            )
        );
    }


    const result:
        AdminBatchPreparationResponse =
        await response.json();


    return result;
}


/*
 * =========================================================
 * ORDER DETAIL
 * =========================================================
 */

export async function getAdminOrderDetail(
    orderNumber: string,
    authorization: string,
    signal?: AbortSignal
): Promise<AdminOrderDetail> {

    const response =
        await adminFetch(
            `/api/admin/orders/${encodeURIComponent(
                orderNumber
            )}`,
            authorization,
            {
                method:
                    "GET",

                signal,

                cache:
                    "no-store"
            }
        );


    if (
        response.status
        === 401
    ) {

        throw new Error(
            "Your admin session is no longer valid."
        );
    }


    if (
        response.status
        === 403
    ) {

        throw new Error(
            "You do not have permission to view this order."
        );
    }


    if (
        response.status
        === 404
    ) {

        throw new Error(
            "Order not found."
        );
    }


    if (
        response.status
        === 400
    ) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to load this order."
            )
        );
    }


    if (!response.ok) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to load this order."
            )
        );
    }


    const result:
        AdminOrderDetail =
        await response.json();


    return result;
}


/*
 * =========================================================
 * SINGLE ORDER STATUS UPDATE
 * =========================================================
 */

export async function updateAdminOrderStatus(
    orderNumber: string,
    request: AdminOrderStatusUpdateRequest,
    authorization: string
): Promise<AdminOrderDetail> {

    const response =
        await adminFetch(
            `/api/admin/orders/${encodeURIComponent(
                orderNumber
            )}/status`,
            authorization,
            {
                method:
                    "PATCH",

                headers: {
                    "Content-Type":
                        "application/json"
                },

                body:
                    JSON.stringify(
                        request
                    )
            }
        );


    if (
        response.status
        === 401
    ) {

        throw new Error(
            "Your admin session is no longer valid."
        );
    }


    if (
        response.status
        === 403
    ) {

        throw new Error(
            "You do not have permission to perform this order action."
        );
    }


    if (
        response.status
        === 404
    ) {

        throw new Error(
            "Order not found."
        );
    }


    if (
        response.status
        === 400
    ) {

        throw new Error(
            await getErrorMessage(
                response,
                "The order status could not be updated."
            )
        );
    }


    if (
        response.status
        === 409
    ) {

        throw new Error(
            await getErrorMessage(
                response,
                "The order changed while this action was being processed. Refresh and try again."
            )
        );
    }


    if (!response.ok) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to update the order status."
            )
        );
    }


    const result:
        AdminOrderDetail =
        await response.json();


    return result;
}


/*
 * =========================================================
 * WORKFLOW HELPERS
 * =========================================================
 */

export function getNextOrderStatus(
    currentStatus: OrderStatus
): OrderStatus | null {

    switch (
        currentStatus
    ) {

        case "CONFIRMED":
            return "PREPARING";

        case "PREPARING":
            return "READY_FOR_PICKUP";

        case "READY_FOR_PICKUP":
            return "PICKED_UP";

        default:
            return null;
    }
}


export function getRequiredPermissionForTransition(
    currentStatus: OrderStatus
): string | null {

    switch (
        currentStatus
    ) {

        case "CONFIRMED":
            return "ORDER_START_PREPARATION";

        case "PREPARING":
            return "ORDER_MARK_READY";

        case "READY_FOR_PICKUP":
            return "ORDER_MARK_PICKED_UP";

        default:
            return null;
    }
}