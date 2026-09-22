import {
    adminFetch
} from "@/services/adminApi";

import type {
    AdminCustomerDetail,
    AdminCustomerDirectoryResponse,
    AdminCustomerOrderHistoryResponse,
    CustomerVerificationStatus
} from "@/types/adminCustomer";


interface GetAdminCustomersParams {

    search?: string;

    status?: CustomerVerificationStatus;

    page?: number;

    size?: number;
}


async function readErrorMessage(
    response: Response,
    fallback: string
): Promise<string> {

    try {

        const body =
            await response.json() as {
                message?: string;
                error?: string;
            };


        if (
            typeof body.message === "string"
            &&
            body.message.trim()
        ) {

            return body.message;
        }


        if (
            typeof body.error === "string"
            &&
            body.error.trim()
        ) {

            return body.error;
        }

    } catch {

        /*
         * Response may not contain JSON.
         */
    }


    return fallback;
}


async function ensureOk(
    response: Response,
    fallback: string
) {

    if (
        response.ok
    ) {

        return;
    }


    if (
        response.status === 401
    ) {

        throw new Error(
            "Your admin session is no longer authorized."
        );
    }


    if (
        response.status === 403
    ) {

        throw new Error(
            "You do not have permission to view customer information."
        );
    }


    throw new Error(
        await readErrorMessage(
            response,
            fallback
        )
    );
}


function buildQuery(
    values: Record<
        string,
        string | number | null | undefined
    >
) {

    const params =
        new URLSearchParams();


    Object.entries(
        values
    ).forEach(
        ([
            key,
            value
        ]) => {

            if (
                value !== null
                &&
                value !== undefined
                &&
                value !== ""
            ) {

                params.set(
                    key,
                    String(
                        value
                    )
                );
            }
        }
    );


    const query =
        params.toString();


    return query
        ? `?${query}`
        : "";
}


export async function getAdminCustomers(
    authorization: string,
    params: GetAdminCustomersParams = {},
    signal?: AbortSignal
): Promise<AdminCustomerDirectoryResponse> {

    const query =
        buildQuery({
            search:
                params.search,
            status:
                params.status,
            page:
                params.page
                ?? 0,
            size:
                params.size
                ?? 20
        });


    const response =
        await adminFetch(
            `/api/admin/customers${query}`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load customers."
    );


    return response.json();
}


export async function getAdminCustomer(
    customerId: number,
    authorization: string,
    signal?: AbortSignal
): Promise<AdminCustomerDetail> {

    const response =
        await adminFetch(
            `/api/admin/customers/${customerId}`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load customer."
    );


    return response.json();
}


export async function getAdminCustomerOrders(
    customerId: number,
    authorization: string,
    page = 0,
    size = 20,
    signal?: AbortSignal
): Promise<AdminCustomerOrderHistoryResponse> {

    const query =
        buildQuery({
            page,
            size
        });


    const response =
        await adminFetch(
            `/api/admin/customers/${customerId}/orders${query}`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load customer order history."
    );


    return response.json();
}
