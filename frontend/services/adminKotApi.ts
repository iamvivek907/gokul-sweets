import {
    adminFetch
} from "@/services/adminApi";

import type {
    AdminKot,
    AdminKotReprintResponse
} from "@/types/adminKot";


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
            typeof body.message
            === "string"
            &&
            body.message.trim()
        ) {

            return body.message;
        }


        if (
            typeof body.error
            === "string"
            &&
            body.error.trim()
        ) {

            return body.error;
        }

    } catch {

        /*
         * The response may not contain JSON.
         */
    }


    return fallback;
}


/*
 * =========================================================
 * COMMON AUTHORIZATION ERROR HANDLING
 * =========================================================
 */

function throwAuthorizationError(
    response: Response
) {

    if (
        response.status
        === 401
    ) {

        throw new Error(
            "Your admin session is no longer authorized."
        );
    }


    if (
        response.status
        === 403
    ) {

        throw new Error(
            "You do not have permission to access this KOT."
        );
    }
}


/*
 * =========================================================
 * GET KOT BY ORDER NUMBER
 * =========================================================
 */

export async function getAdminKotByOrderNumber(
    orderNumber: string,
    authorization: string,
    signal?: AbortSignal
): Promise<AdminKot> {

    const response =
        await adminFetch(
            `/api/admin/kot/order/${encodeURIComponent(
                orderNumber
            )}`,
            authorization,
            {
                method: "GET",

                signal
            }
        );


    throwAuthorizationError(
        response
    );


    if (
        response.status
        === 404
    ) {

        throw new Error(
            await readErrorMessage(
                response,
                "KOT not found."
            )
        );
    }


    if (
        !response.ok
    ) {

        throw new Error(
            await readErrorMessage(
                response,
                "Unable to load the KOT."
            )
        );
    }


    const kot: AdminKot =
        await response.json();


    return kot;
}


/*
 * =========================================================
 * GET KOT BY KOT NUMBER
 * =========================================================
 */

export async function getAdminKotByNumber(
    kotNumber: string,
    authorization: string,
    signal?: AbortSignal
): Promise<AdminKot> {

    const response =
        await adminFetch(
            `/api/admin/kot/${encodeURIComponent(
                kotNumber
            )}`,
            authorization,
            {
                method: "GET",

                signal
            }
        );


    throwAuthorizationError(
        response
    );


    if (
        response.status
        === 404
    ) {

        throw new Error(
            await readErrorMessage(
                response,
                "KOT not found."
            )
        );
    }


    if (
        !response.ok
    ) {

        throw new Error(
            await readErrorMessage(
                response,
                "Unable to load the KOT."
            )
        );
    }


    const kot: AdminKot =
        await response.json();


    return kot;
}


/*
 * =========================================================
 * QUEUE DURABLE KOT REPRINT
 * =========================================================
 */

export async function queueAdminKotReprint(
    kotNumber: string,
    authorization: string
): Promise<AdminKotReprintResponse> {

    const response =
        await adminFetch(
            `/api/admin/kot/${encodeURIComponent(
                kotNumber
            )}/reprint`,
            authorization,
            {
                method: "POST"
            }
        );


    throwAuthorizationError(
        response
    );


    if (
        response.status
        === 404
    ) {

        throw new Error(
            await readErrorMessage(
                response,
                "KOT not found."
            )
        );
    }


    if (
        !response.ok
    ) {

        throw new Error(
            await readErrorMessage(
                response,
                "Unable to queue the KOT reprint."
            )
        );
    }


    return response.json();
}


/*
 * =========================================================
 * QUEUE KOT REPRINT BY ORDER NUMBER
 * =========================================================
 *
 * The durable backend reprint endpoint uses the KOT number.
 * Live Orders already has the order number, so this helper
 * resolves the KOT first and then queues the REPRINT job.
 */

export async function queueAdminKotReprintForOrder(
    orderNumber: string,
    authorization: string
): Promise<AdminKotReprintResponse> {

    const kot =
        await getAdminKotByOrderNumber(
            orderNumber,
            authorization
        );


    return queueAdminKotReprint(
        kot.kotNumber,
        authorization
    );
}


/*
 * =========================================================
 * RECORD KOT PRINT
 * =========================================================
 *
 * This records that an authenticated staff member initiated
 * a KOT print through the admin application.
 *
 * The backend:
 *
 * - locks the KOT
 * - records first print details when applicable
 * - updates the latest print details
 * - increments printCount
 * - returns the authoritative updated KOT
 *
 * Stage-1 browser printing does not guarantee that physical
 * paper was successfully produced. This records print
 * initiation through the application.
 *
 * This remains available for the emergency browser fallback.
 */

export async function recordAdminKotPrint(
    kotNumber: string,
    authorization: string
): Promise<AdminKot> {

    const response =
        await adminFetch(
            `/api/admin/kot/${encodeURIComponent(
                kotNumber
            )}/print`,
            authorization,
            {
                method: "POST"
            }
        );


    throwAuthorizationError(
        response
    );


    if (
        response.status
        === 404
    ) {

        throw new Error(
            await readErrorMessage(
                response,
                "KOT not found."
            )
        );
    }


    if (
        !response.ok
    ) {

        throw new Error(
            await readErrorMessage(
                response,
                "Unable to record KOT printing."
            )
        );
    }


    const kot: AdminKot =
        await response.json();


    return kot;
}
