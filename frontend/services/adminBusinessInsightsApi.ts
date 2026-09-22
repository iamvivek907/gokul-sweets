import {
    adminFetch
} from "@/services/adminApi";

import type {
    BusinessInsightsResponse
} from "@/types/adminBusinessInsights";


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


export async function getBusinessInsights(
    authorization: string,
    params: {
        fromDate: string;
        toDate: string;
        branchId?: number | null;
    },
    signal?: AbortSignal
): Promise<BusinessInsightsResponse> {

    const query =
        new URLSearchParams({
            fromDate:
                params.fromDate,
            toDate:
                params.toDate
        });


    if (
        params.branchId !== null
        &&
        params.branchId !== undefined
    ) {

        query.set(
            "branchId",
            String(
                params.branchId
            )
        );
    }


    const response =
        await adminFetch(
            `/api/admin/reports/insights?${query.toString()}`,
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
                "Unable to load business insights."
            )
        );
    }


    return response.json();
}
