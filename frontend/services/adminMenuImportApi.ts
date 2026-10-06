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


export function validateMenuImport(branchId: number, file: File, authorization: string, signal?: AbortSignal): Promise<MenuImportValidationResponse> {
    return submitMenuImport(branchId, file, authorization, "VALIDATE", signal);
}

export function importMenuFile(branchId: number, file: File, authorization: string, signal?: AbortSignal): Promise<MenuImportResultResponse> {
    return submitMenuImport(branchId, file, authorization, "IMPORT", signal);
}

// Covers the upload, HTTP acknowledgement, error bodies and result bodies. Parsing a
// synchronous 500-row workbook may take longer than a status GET, so allow one minute.
const SUBMISSION_TIMEOUT_MS = 60_000;
async function submitMenuImport<T>(branchId: number, file: File, authorization: string,
    operation: PendingMenuImportJob["operation"], signal?: AbortSignal): Promise<T> {
    if (signal?.aborted) throw signal.reason;
    const submissionId = crypto.randomUUID();
    const formData = new FormData();
    formData.append("file", file);
    formData.append("submissionId", submissionId);
    // Save before sending: even a completely lost response can recover an accepted job.
    rememberJob(branchId, {id: submissionId, operation, submission: true});
    const request = new AbortController();
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; request.abort(); }, SUBMISSION_TIMEOUT_MS);
    const abort = () => request.abort(signal?.reason);
    signal?.addEventListener("abort", abort, {once: true});
    let accepted: ImportJob | undefined;
    try {
        const path = `/api/admin/branches/${branchId}/menu/import${operation === "VALIDATE" ? "/validate" : ""}`;
        const response = await adminFetch(path, authorization, {method: "POST", body: formData, signal: request.signal});
        if (!response.ok) {
            // A definitive client rejection means this submission was not accepted.
            if (response.status >= 400 && response.status < 500) rememberJob(branchId, null);
            const fallback = response.status === 401 ? "Your admin session is no longer valid."
                : response.status === 403 ? "You do not have permission to upload menus for this branch."
                : response.status === 404 ? "Branch not found."
                : "The menu upload could not be completed.";
            throw new Error(await getErrorMessage(response, fallback));
        }
        if (response.status !== 202) {
            const result: T = await response.json();
            rememberJob(branchId, null);
            return result;
        }
        accepted = await response.json();
        if (!accepted || !/^[a-f0-9-]{36}$/i.test(accepted.id)) throw new Error("Invalid menu job acknowledgement.");
        rememberJob(branchId, {id: accepted.id, operation});
    } catch (failure) {
        if (getPendingMenuImportJob(branchId)?.id === submissionId) {
            const reason = timedOut ? "Menu upload timed out after one minute."
                : request.signal.aborted ? "Menu upload was stopped." : "The upload acknowledgement could not be read.";
            throw new Error(`${reason} It may still have completed. Use Resume status check before uploading again.`, {cause: failure});
        }
        if (timedOut) throw new Error("Menu upload timed out after one minute.", {cause: failure});
        throw failure;
    } finally {
        clearTimeout(timer);
        signal?.removeEventListener("abort", abort);
    }
    // The upload deadline ends here; status polling has its own bounded lifetime.
    return pollImportJob<T>(accepted!, branchId, authorization, signal);
}
interface ImportJob {id: string; status: "QUEUED" | "PROCESSING" | "SUCCEEDED" | "FAILED"; result: string | null; error: string | null}
export interface PendingMenuImportJob {id: string; operation: "VALIDATE" | "IMPORT"; submission?: boolean}
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
async function pollImportJob<T>(job: ImportJob, branchId: number, authorization: string, signal?: AbortSignal, submissionId?: string): Promise<T> {
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
                const resource = submissionId ? `submissions/${submissionId}` : `jobs/${job.id}`;
                const status = await adminFetch(`/api/admin/branches/${branchId}/menu/import/${resource}`, authorization,
                    {signal: request.signal});
                if (!status.ok) {
                    if ([401, 403].includes(status.status) || status.status === 404 && !submissionId || status.status === 409 && submissionId) {
                        resumable = false;
                        rememberJob(branchId, null);
                    }
                    if (status.status === 409 && submissionId)
                        throw new Error("This backend uses synchronous uploads, whose result cannot be recovered. The upload may have completed. Check the menu before uploading again.");
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
export async function resumeMenuImportJob(branchId: number, authorization: string, signal?: AbortSignal) {
    const pending = getPendingMenuImportJob(branchId);
    if (!pending) throw new Error("No pending menu job was found for this branch.");
    const result = await pollImportJob<MenuImportValidationResponse | MenuImportResultResponse>(
        {id: pending.id, status: "PROCESSING", result: null, error: null}, branchId, authorization, signal, pending.submission ? pending.id : undefined);
    return {operation: pending.operation, result};
}

/** Forget only the browser recovery record; this does not cancel an accepted backend job. */
export function discardMenuImportRecovery(branchId: number) { rememberJob(branchId, null); }
