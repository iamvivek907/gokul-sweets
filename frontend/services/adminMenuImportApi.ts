import {
    adminFetch
} from "@/services/adminApi";

import type {
    MenuImportResultResponse,
    MenuImportValidationResponse
} from "@/types/adminMenuImport";


function getFilenameFromContentDisposition(
    contentDisposition: string | null
) {

    if (!contentDisposition) {
        return null;
    }


    const utf8Match =
        contentDisposition.match(
            /filename\*=UTF-8''([^;]+)/
        );


    if (
        utf8Match
        &&
        utf8Match[1]
    ) {

        return decodeURIComponent(
            utf8Match[1]
        );
    }


    const normalMatch =
        contentDisposition.match(
            /filename="?([^"]+)"?/
        );


    if (
        normalMatch
        &&
        normalMatch[1]
    ) {

        return normalMatch[1];
    }


    return null;
}


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

    } catch {
        // Ignore JSON parsing failure.
    }


    return fallback;
}


export async function downloadMenuImportTemplate(
    branchId: number,
    authorization: string
): Promise<void> {

    const response =
        await adminFetch(
            `/api/admin/branches/${branchId}/menu/import/template`,
            authorization,
            {
                method:
                    "GET"
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
            "You do not have permission to download the menu template for this branch."
        );
    }


    if (
        response.status
        === 404
    ) {

        throw new Error(
            "Branch not found."
        );
    }


    if (!response.ok) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to download the menu template."
            )
        );
    }


    const blob =
        await response.blob();


    const contentDisposition =
        response.headers.get(
            "Content-Disposition"
        );


    const filename =
        getFilenameFromContentDisposition(
            contentDisposition
        )
        ?? `gokul-menu-branch-${branchId}.xlsx`;


    const objectUrl =
        URL.createObjectURL(
            blob
        );


    try {

        const anchor =
            document.createElement(
                "a"
            );


        anchor.href =
            objectUrl;


        anchor.download =
            filename;


        document.body.appendChild(
            anchor
        );


        anchor.click();


        anchor.remove();

    } finally {

        URL.revokeObjectURL(
            objectUrl
        );
    }
}


export async function validateMenuImport(
    branchId: number,
    file: File,
    authorization: string,
    signal?: AbortSignal
): Promise<MenuImportValidationResponse> {

    const formData =
        new FormData();


    formData.append(
        "file",
        file
    );


    const response =
        await adminFetch(
            `/api/admin/branches/${branchId}/menu/import/validate`,
            authorization,
            {
                method:
                    "POST",

                body:
                    formData,

                signal
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
            "You do not have permission to validate menu imports for this branch."
        );
    }


    if (
        response.status
        === 404
    ) {

        throw new Error(
            "Branch not found."
        );
    }


    if (
        response.status
        === 400
    ) {

        throw new Error(
            await getErrorMessage(
                response,
                "The selected menu file could not be validated."
            )
        );
    }


    if (!response.ok) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to validate the menu file."
            )
        );
    }


    const result:
        MenuImportValidationResponse =
        await response.json();


    return result;
}


export async function importMenuFile(
    branchId: number,
    file: File,
    authorization: string,
    signal?: AbortSignal
): Promise<MenuImportResultResponse> {

    const formData =
        new FormData();


    formData.append(
        "file",
        file
    );


    const response =
        await adminFetch(
            `/api/admin/branches/${branchId}/menu/import`,
            authorization,
            {
                method:
                    "POST",

                body:
                    formData,

                signal
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
            "You do not have permission to import menu updates for this branch."
        );
    }


    if (
        response.status
        === 404
    ) {

        throw new Error(
            "Branch not found."
        );
    }


    if (
        response.status
        === 400
    ) {

        throw new Error(
            await getErrorMessage(
                response,
                "The menu file contains invalid data."
            )
        );
    }


    if (!response.ok) {

        throw new Error(
            await getErrorMessage(
                response,
                "Unable to import the menu file."
            )
        );
    }


    const result:
        MenuImportResultResponse =
        await response.json();


    return result;
}