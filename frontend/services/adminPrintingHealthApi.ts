import {
    adminFetch
} from "@/services/adminApi";

import type {
    AdminPrintingHealth,
    PrinterStation
} from "@/types/adminPrintingHealth";


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


export async function getAdminPrintingHealth(
    branchId: number,
    authorization: string,
    station: PrinterStation = "KITCHEN",
    signal?: AbortSignal
): Promise<AdminPrintingHealth> {

    const params =
        new URLSearchParams();


    params.set(
        "branchId",
        String(
            branchId
        )
    );


    params.set(
        "station",
        station
    );


    const response =
        await adminFetch(
            `/api/admin/printing/health?${params.toString()}`,
            authorization,
            {
                method: "GET",
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
            "You do not have permission to view printing health."
        );
    }


    if (!response.ok) {

        throw new Error(
            await readErrorMessage(
                response,
                "Unable to load printing health."
            )
        );
    }


    return response.json();
}
