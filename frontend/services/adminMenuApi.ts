import {
    adminFetch
} from "@/services/adminApi";

import type {
    AdminBranchProduct,
    AdminBranchProductUpdateRequest
} from "@/types/adminMenu";


export async function getAdminBranchMenu(
    branchId: number,
    authorization: string,
    signal?: AbortSignal
): Promise<AdminBranchProduct[]> {

    const response =
        await adminFetch(
            `/api/admin/branches/${branchId}/menu`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    if (response.status === 401) {

        throw new Error(
            "Your admin session is no longer valid."
        );
    }


    if (response.status === 403) {

        throw new Error(
            "You do not have permission to manage this branch menu."
        );
    }


    if (response.status === 404) {

        throw new Error(
            "Branch not found."
        );
    }


    if (!response.ok) {

        throw new Error(
            "Unable to load the branch menu."
        );
    }


    const menu:
        AdminBranchProduct[] =
        await response.json();


    return menu;
}


export async function updateAdminBranchProduct(
    branchId: number,
    productId: number,
    request: AdminBranchProductUpdateRequest,
    authorization: string
): Promise<AdminBranchProduct> {

    const response =
        await adminFetch(
            `/api/admin/branches/${branchId}/menu/products/${productId}`,
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


    if (response.status === 401) {

        throw new Error(
            "Your admin session is no longer valid."
        );
    }


    if (response.status === 403) {

        throw new Error(
            "You do not have permission to update this branch menu."
        );
    }


    if (response.status === 404) {

        throw new Error(
            "The selected product or branch could not be found."
        );
    }


    if (response.status === 400) {

        let message =
            "The menu update is invalid.";

        try {

            const errorBody =
                await response.json();

            if (
                typeof errorBody?.message
                === "string"
            ) {

                message =
                    errorBody.message;
            }

        } catch {
            // Keep default validation message.
        }


        throw new Error(
            message
        );
    }


    if (!response.ok) {

        throw new Error(
            "Unable to update the menu item."
        );
    }


    const updated:
        AdminBranchProduct =
        await response.json();


    return updated;
}