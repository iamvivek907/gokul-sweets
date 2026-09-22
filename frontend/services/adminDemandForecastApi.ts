import {
    adminFetch
} from "@/services/adminApi";

import type {
    DemandForecastResponse
} from "@/types/adminDemandForecast";


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


export async function getDemandForecast(
    authorization: string,
    params: {
        targetDate: string;
        branchId: number;
    },
    signal?: AbortSignal
): Promise<DemandForecastResponse> {

    const query =
        new URLSearchParams({
            targetDate:
                params.targetDate,
            branchId:
                String(
                    params.branchId
                )
        });


    const response =
        await adminFetch(
            `/api/admin/reports/forecast?${query.toString()}`,
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
                "Unable to calculate demand forecast."
            )
        );
    }


    return response.json();
}
