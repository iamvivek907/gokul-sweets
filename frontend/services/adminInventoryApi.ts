import {
    adminFetch
} from "@/services/adminApi";

import type {
    BulkAllocationItemRequest,
    BulkReadinessItemRequest,
    CompleteInventoryItemRequest,
    InventoryAllocation,
    InventoryCatalogueFilter,
    InventoryCataloguePage,
    InventoryPolicy,
    InventoryPolicyRequest
} from "@/types/adminInventory";

async function parseResponse<T>(
    response: Response,
    fallback: string
): Promise<T> {
    if (response.status === 401) {
        throw new Error("Your admin session is no longer valid.");
    }
    if (response.status === 403) {
        throw new Error("You do not have permission to manage inventory for this branch.");
    }
    if (!response.ok) {
        try {
            const body = await response.json();
            throw new Error(body?.message ?? body?.error ?? fallback);
        } catch (error) {
            if (error instanceof Error && error.message !== "Unexpected end of JSON input") {
                throw error;
            }
            throw new Error(fallback);
        }
    }
    return response.json() as Promise<T>;
}

export async function getInventoryCatalogue(
    branchId: number,
    serviceDate: string,
    authorization: string,
    options: {
        search?: string;
        categoryId?: number;
        filter?: InventoryCatalogueFilter;
        page?: number;
        size?: number;
        signal?: AbortSignal;
    } = {}
): Promise<InventoryCataloguePage> {
    const params = new URLSearchParams({
        serviceDate,
        filter: options.filter ?? "ALL",
        page: String(options.page ?? 0),
        size: String(options.size ?? 25)
    });

    if (options.search?.trim()) params.set("search", options.search.trim());
    if (options.categoryId !== undefined) params.set("categoryId", String(options.categoryId));

    const response = await adminFetch(
        `/api/admin/inventory/branches/${branchId}/catalogue?${params.toString()}`,
        authorization,
        {method: "GET", cache: "no-store", signal: options.signal}
    );
    return parseResponse<InventoryCataloguePage>(response, "Unable to load inventory.");
}

export async function updateBulkInventoryPolicies(
    branchId: number,
    branchProductIds: number[],
    policy: InventoryPolicyRequest,
    authorization: string
): Promise<{updatedCount: number; results: InventoryPolicy[]}> {
    const response = await adminFetch(
        `/api/admin/inventory/branches/${branchId}/policies/bulk`,
        authorization,
        {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({branchProductIds, policy})
        }
    );
    return parseResponse(response, "Unable to update inventory policies.");
}

export async function approveBulkInventoryAllocations(
    branchId: number,
    serviceDate: string,
    items: BulkAllocationItemRequest[],
    authorization: string
): Promise<{updatedCount: number; results: InventoryAllocation[]}> {
    const response = await adminFetch(
        `/api/admin/inventory/branches/${branchId}/allocations/bulk`,
        authorization,
        {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({serviceDate, items})
        }
    );
    return parseResponse(response, "Unable to approve inventory allocations.");
}

export async function updateBulkInventoryReadiness(
    branchId: number,
    serviceDate: string,
    items: BulkReadinessItemRequest[],
    authorization: string
): Promise<{updatedCount: number; results: InventoryAllocation[]}> {
    const response = await adminFetch(
        `/api/admin/inventory/branches/${branchId}/readiness/bulk`,
        authorization,
        {
            method: "PATCH",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({serviceDate, items})
        }
    );
    return parseResponse(response, "Unable to update inventory readiness.");
}

export async function completeBulkInventorySetup(
    branchId: number,
    serviceDate: string,
    items: CompleteInventoryItemRequest[],
    authorization: string
): Promise<{updatedCount: number; results: InventoryAllocation[]}> {
    const response = await adminFetch(
        `/api/admin/inventory/branches/${branchId}/setup/complete`,
        authorization,
        {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({serviceDate, items})
        }
    );
    return parseResponse(response, "Unable to approve and mark inventory ready.");
}
