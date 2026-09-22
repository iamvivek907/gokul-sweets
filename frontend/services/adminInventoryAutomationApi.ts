import {
    adminFetch
} from "@/services/adminApi";

import type {
    InventoryAutomationRule,
    InventoryAutomationRuleRequest,
    InventoryAutomationRun,
    InventoryAutomationWorkspace
} from "@/types/adminInventoryAutomation";

async function parseResponse<T>(response: Response, fallback: string): Promise<T> {
    if (response.status === 401) throw new Error("Your admin session is no longer valid.");
    if (response.status === 403) throw new Error("You do not have inventory access for this branch.");
    if (!response.ok) {
        let message = fallback;
        try {
            const body = await response.json();
            message = body?.message ?? body?.error ?? fallback;
        } catch {
            // Keep the safe fallback for non-JSON error responses.
        }
        throw new Error(message);
    }
    return response.json() as Promise<T>;
}

const basePath = (branchId: number) =>
    `/api/admin/inventory/branches/${branchId}/automation`;

export async function getInventoryAutomationWorkspace(
    branchId: number,
    authorization: string,
    signal?: AbortSignal
): Promise<InventoryAutomationWorkspace> {
    const response = await adminFetch(basePath(branchId), authorization, {
        method: "GET",
        cache: "no-store",
        signal
    });
    return parseResponse(response, "Unable to load inventory automation.");
}

export async function updateInventoryAutomationRule(
    branchId: number,
    branchProductId: number,
    request: InventoryAutomationRuleRequest,
    authorization: string
): Promise<InventoryAutomationRule> {
    const response = await adminFetch(
        `${basePath(branchId)}/rules/${branchProductId}`,
        authorization,
        {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(request)
        }
    );
    return parseResponse(response, "Unable to update the automation rule.");
}

export async function generateInventoryAllocations(
    branchId: number,
    fromDate: string,
    throughDate: string,
    authorization: string
): Promise<InventoryAutomationRun> {
    const response = await adminFetch(`${basePath(branchId)}/generate`, authorization, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({fromDate, throughDate})
    });
    return parseResponse(response, "Unable to generate inventory allocations.");
}
