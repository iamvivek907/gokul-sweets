import {
    adminFetch
} from "@/services/adminApi";

import type {
    AdminBranch,
    AdminBranchCreateRequest,
    AdminBranchUpdateRequest
} from "@/types/adminBranches";


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


function throwAuthorizationError(
    response: Response
) {

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
            "You do not have permission to manage branches."
        );
    }
}


export async function getAdminBranches(
    authorization: string,
    signal?: AbortSignal
): Promise<AdminBranch[]> {

    const response =
        await adminFetch(
            "/api/admin/branches",
            authorization,
            {
                method: "GET",
                signal
            }
        );


    throwAuthorizationError(
        response
    );


    if (!response.ok) {

        throw new Error(
            await readErrorMessage(
                response,
                "Unable to load branches."
            )
        );
    }


    return response.json();
}


export async function getAdminBranch(
    branchId: number,
    authorization: string,
    signal?: AbortSignal
): Promise<AdminBranch> {

    const response =
        await adminFetch(
            `/api/admin/branches/${branchId}`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    throwAuthorizationError(
        response
    );


    if (!response.ok) {

        throw new Error(
            await readErrorMessage(
                response,
                "Unable to load the branch."
            )
        );
    }


    return response.json();
}


export async function createAdminBranch(
    request: AdminBranchCreateRequest,
    authorization: string
): Promise<AdminBranch> {

    const response =
        await adminFetch(
            "/api/admin/branches",
            authorization,
            {
                method: "POST",

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify(
                    request
                )
            }
        );


    throwAuthorizationError(
        response
    );


    if (!response.ok) {

        throw new Error(
            await readErrorMessage(
                response,
                "Unable to create the branch."
            )
        );
    }


    return response.json();
}


export async function updateAdminBranch(
    branchId: number,
    request: AdminBranchUpdateRequest,
    authorization: string
): Promise<AdminBranch> {

    const response =
        await adminFetch(
            `/api/admin/branches/${branchId}`,
            authorization,
            {
                method: "PUT",

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify(
                    request
                )
            }
        );


    throwAuthorizationError(
        response
    );


    if (!response.ok) {

        throw new Error(
            await readErrorMessage(
                response,
                "Unable to update the branch."
            )
        );
    }


    return response.json();
}


export async function updateAdminBranchActive(
    branchId: number,
    active: boolean,
    authorization: string
): Promise<AdminBranch> {

    const response =
        await adminFetch(
            `/api/admin/branches/${branchId}/active`,
            authorization,
            {
                method: "PATCH",

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify({
                    active
                })
            }
        );


    throwAuthorizationError(
        response
    );


    if (!response.ok) {

        throw new Error(
            await readErrorMessage(
                response,
                active
                    ? "Unable to activate the branch."
                    : "Unable to deactivate the branch."
            )
        );
    }


    return response.json();
}
