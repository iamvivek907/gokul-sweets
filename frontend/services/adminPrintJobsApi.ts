import type {
    AdminPrintJob,
    AdminPrintJobCounts,
    AdminPrintJobPageResponse,
    PrintJobStatus
} from "@/types/adminPrintJobs";
import {ADMIN_API_BASE_URL} from "@/lib/constants";


const API_BASE = ADMIN_API_BASE_URL;


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
         * Ignore invalid/non-JSON error bodies.
         */
    }


    return fallback;
}


export async function getAdminPrintJobs(
    branchId: number,
    authorization: string,
    options?: {
        status?: PrintJobStatus;
        page?: number;
        size?: number;
        signal?: AbortSignal;
    }
): Promise<AdminPrintJobPageResponse> {

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
            options?.page
            ?? 0
        )
    );


    params.set(
        "size",
        String(
            options?.size
            ?? 20
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
        await fetch(
            `${API_BASE}/api/admin/print-jobs?${params.toString()}`,
            {
                method:
                    "GET",

                headers: {
                    Authorization:
                        authorization
                },

                cache:
                    "no-store",

                signal:
                    options?.signal
            }
        );


    if (!response.ok) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to load print jobs."
            )
        );
    }


    return response.json();
}


export async function getAdminPrintJobCounts(
    branchId: number,
    authorization: string,
    signal?: AbortSignal
): Promise<AdminPrintJobCounts> {

    const response =
        await fetch(
            `${API_BASE}/api/admin/print-jobs/counts?branchId=${branchId}`,
            {
                method:
                    "GET",

                headers: {
                    Authorization:
                        authorization
                },

                cache:
                    "no-store",

                signal
            }
        );


    if (!response.ok) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to load print queue counts."
            )
        );
    }


    return response.json();
}


export async function retryAdminPrintJob(
    printJobId: number,
    authorization: string
): Promise<AdminPrintJob> {

    const response =
        await fetch(
            `${API_BASE}/api/admin/print-jobs/${printJobId}/retry`,
            {
                method:
                    "POST",

                headers: {
                    Authorization:
                        authorization
                },

                cache:
                    "no-store"
            }
        );


    if (!response.ok) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to retry the print job."
            )
        );
    }


    return response.json();
}
