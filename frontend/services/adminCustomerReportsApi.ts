import {
    adminFetch
} from "@/services/adminApi";

import type {
    CustomerIntelligenceResponse,
    CustomerLifecycleState,
    CustomerValueSegment
} from "@/types/adminCustomerReports";


async function readError(
    response: Response,
    fallback: string
) {

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

        // Response may not be JSON.
    }


    return fallback;
}


export async function getCustomerIntelligence(
    authorization: string,
    params: {
        search?: string;
        lifecycle?: CustomerLifecycleState | null;
        valueSegment?: CustomerValueSegment | null;
        page?: number;
        size?: number;
    },
    signal?: AbortSignal
): Promise<CustomerIntelligenceResponse> {

    const query =
        new URLSearchParams({
            page:
                String(
                    params.page
                    ?? 0
                ),
            size:
                String(
                    params.size
                    ?? 25
                )
        });


    if (
        params.search?.trim()
    ) {

        query.set(
            "search",
            params.search.trim()
        );
    }


    if (
        params.lifecycle
    ) {

        query.set(
            "lifecycle",
            params.lifecycle
        );
    }


    if (
        params.valueSegment
    ) {

        query.set(
            "valueSegment",
            params.valueSegment
        );
    }


    const response =
        await adminFetch(
            `/api/admin/reports/customers?${query.toString()}`,
            authorization,
            {
                method:
                    "GET",
                signal
            }
        );


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
            "You do not have permission to view reports."
        );
    }


    if (
        !response.ok
    ) {

        throw new Error(
            await readError(
                response,
                "Unable to load customer intelligence."
            )
        );
    }


    return response.json();
}
