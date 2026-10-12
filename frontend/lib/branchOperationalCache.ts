import {apiClient} from "@/services/apiClient";
import type {Branch} from "@/types/branch";

const freshnessMs = 15000;
const entries = new Map<number, {branch: Branch; checkedAt: number}>();
const pending = new Map<number, Promise<Branch>>();

export function cachedOperationalBranch(id: number): Branch | null {
    const entry = entries.get(id);
    if (!entry || Date.now() - entry.checkedAt >= freshnessMs) {
        entries.delete(id);
        return null;
    }
    return entry.branch;
}

/** Share bounded reads across page remounts; never trust a stored branch as a live check. */
export function checkOperationalBranch(id: number, force = false): Promise<Branch> {
    const cached = force ? null : cachedOperationalBranch(id);
    if (cached) return Promise.resolve(cached);
    const existing = pending.get(id);
    if (existing) return existing;
    const request = apiClient<Branch>(`/api/branches/${id}`, {
        signal: AbortSignal.timeout(8000), cache: "no-store"
    }).then(branch => {
        entries.delete(id);
        entries.set(id, {branch, checkedAt: Date.now()});
        while (entries.size > 8) entries.delete(entries.keys().next().value!);
        return branch;
    }).catch(error => {entries.delete(id); throw error;}).finally(() => {if (pending.get(id) === request) pending.delete(id);});
    pending.set(id, request);
    return request;
}
