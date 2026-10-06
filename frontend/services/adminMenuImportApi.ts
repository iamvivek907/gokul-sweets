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


    const result: MenuImportValidationResponse = await readImportResult(response,branchId,authorization,"VALIDATE",signal);


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


    const result: MenuImportResultResponse = await readImportResult(response,branchId,authorization,"IMPORT",signal);


    return result;
}
interface ImportJob {id: string; status: "QUEUED" | "PROCESSING" | "SUCCEEDED" | "FAILED"; result: string | null; error: string | null}
export interface PendingMenuImportJob {id: string; operation: "VALIDATE" | "IMPORT"}
const pendingKey = (branchId: number) => `gokul-menu-import-job:${branchId}`;

export function getPendingMenuImportJob(branchId: number): PendingMenuImportJob | null {
    try {
        const stored = sessionStorage.getItem(pendingKey(branchId));
        if (!stored) return null;
        const job = JSON.parse(stored) as PendingMenuImportJob;
        return /^[a-f0-9-]{36}$/i.test(job.id) && ["VALIDATE", "IMPORT"].includes(job.operation) ? job : null;
    } catch { return null; }
}
function rememberJob(branchId: number, job: PendingMenuImportJob | null) {
    try {
        if (job) sessionStorage.setItem(pendingKey(branchId), JSON.stringify(job));
        else sessionStorage.removeItem(pendingKey(branchId));
    } catch { /* Status polling still works when browser storage is unavailable. */ }
}
function waitForPoll(signal: AbortSignal): Promise<void> {
    return new Promise((resolve, reject) => {
        const abort = () => { clearTimeout(timer); reject(signal.reason); };
        const timer = setTimeout(() => { signal.removeEventListener("abort", abort); resolve(); }, 2000);
        if (signal.aborted) abort();
        else signal.addEventListener("abort", abort, {once: true});
    });
}
async function pollImportJob<T>(job: ImportJob, branchId: number, authorization: string, signal?: AbortSignal): Promise<T> {
    const deadline = new AbortController();
    let timedOut = false;
    const deadlineTimer = setTimeout(() => { timedOut = true; deadline.abort(); }, 10 * 60 * 1000);
    const abortOverall = () => deadline.abort(signal?.reason);
    if (signal?.aborted) abortOverall();
    else signal?.addEventListener("abort", abortOverall, {once: true});
    const overall = deadline.signal;
    let resumable = true;
    try {
        while (job.status === "QUEUED" || job.status === "PROCESSING") {
            await waitForPoll(overall);
            const request = new AbortController();
            const requestTimer = setTimeout(() => request.abort(), 8000);
            const abortRequest = () => request.abort(overall.reason);
            if (overall.aborted) abortRequest();
            else overall.addEventListener("abort", abortRequest, {once: true});
            try {
                const status = await adminFetch(`/api/admin/branches/${branchId}/menu/import/jobs/${job.id}`, authorization,
                    {signal: request.signal});
                if (!status.ok) {
                    if ([401, 403, 404].includes(status.status)) {
                        resumable = false;
                        rememberJob(branchId, null);
                    }
                    throw new Error(`Unable to read menu job ${job.id}.`);
                }
                // Both timers remain active until the response body has been consumed.
                job = await status.json();
            } finally {
                clearTimeout(requestTimer);
                overall.removeEventListener("abort", abortRequest);
            }
        }
        rememberJob(branchId, null);
        if (job.status !== "SUCCEEDED" || !job.result)
            throw new Error(job.error ?? "The menu job failed. Check the file and try again.");
        return JSON.parse(job.result) as T;
    } catch (failure) {
        if (resumable && (job.status === "QUEUED" || job.status === "PROCESSING")) {
            const reason = timedOut ? "Status checking reached its ten-minute limit."
                : overall.aborted ? "Status checking was stopped." : "Unable to read the job status.";
            throw new Error(`${reason} Menu job ${job.id} may still be running. Use Resume status check before uploading again.`, {cause: failure});
        }
        throw failure;
    } finally {
        clearTimeout(deadlineTimer);
        signal?.removeEventListener("abort", abortOverall);
    }
}
async function readImportResult<T>(response: Response, branchId: number, authorization: string,
    operation: PendingMenuImportJob["operation"], signal?: AbortSignal): Promise<T> {
    if (response.status !== 202) return response.json();
    const job: ImportJob = await response.json();
    rememberJob(branchId, {id: job.id, operation});
    return pollImportJob(job, branchId, authorization, signal);
}
export async function resumeMenuImportJob(branchId: number, authorization: string, signal?: AbortSignal) {
    const pending = getPendingMenuImportJob(branchId);
    if (!pending) throw new Error("No pending menu job was found for this branch.");
    const result = await pollImportJob<MenuImportValidationResponse | MenuImportResultResponse>(
        {id: pending.id, status: "PROCESSING", result: null, error: null}, branchId, authorization, signal);
    return {operation: pending.operation, result};
}
