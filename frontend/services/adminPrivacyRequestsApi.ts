import {adminFetch} from "@/services/adminApi";

export type PrivacyReviewState = "RECEIVED" | "IN_REVIEW" | "NEEDS_REVERIFICATION";
export type PrivacyRequestEntry = {
    id: number;
    subjectId: string;
    kind: "EXPORT" | "DELETION_REVIEW";
    receivedAt: string;
    state: PrivacyReviewState;
};

async function parse(response: Response): Promise<never> {
    if (response.status === 404) throw new Error("Privacy request review is not enabled in this environment.");
    if (response.status === 401 || response.status === 403) throw new Error("You are not authorized to review privacy requests.");
    throw new Error("The privacy request queue could not be loaded. Please try again.");
}

export async function listPrivacyRequests(auth: string, page: number, signal?: AbortSignal): Promise<PrivacyRequestEntry[]> {
    const response = await adminFetch(`/api/admin/privacy-requests?page=${page}`, auth, {signal});
    if (!response.ok) return parse(response);
    return response.json() as Promise<PrivacyRequestEntry[]>;
}

export async function triagePrivacyRequest(auth: string, id: number, state: Exclude<PrivacyReviewState, "RECEIVED">): Promise<PrivacyRequestEntry> {
    const response = await adminFetch(`/api/admin/privacy-requests/${id}/triage`, auth, {
        method: "PATCH", headers: {"Content-Type": "application/json"}, body: JSON.stringify({state})
    });
    if (!response.ok) return parse(response);
    return response.json() as Promise<PrivacyRequestEntry>;
}
