import {
    adminFetch
} from "@/services/adminApi";

import type {
    ProductionPlan,
    ProductionPlanItem
} from "@/types/adminProduction";

async function parseResponse<T>(
    response: Response,
    fallback: string
): Promise<T> {
    if (response.status === 401) {
        throw new Error("Your admin session is no longer valid.");
    }
    if (response.status === 403) {
        throw new Error("You do not have inventory access for this branch.");
    }
    if (!response.ok) {
        try {
            const body = await response.json();
            throw new Error(body?.message ?? body?.error ?? fallback);
        } catch (error) {
            if (error instanceof Error
                    && error.message !== "Unexpected end of JSON input") {
                throw error;
            }
            throw new Error(fallback);
        }
    }
    return response.json() as Promise<T>;
}

function endpoint(
    branchId: number,
    branchProductId: number,
    serviceDate: string,
    action: "produced" | "wastage" | "adjustment"
): string {
    return `/api/admin/inventory/branches/${branchId}/production/${branchProductId}/${serviceDate}/${action}`;
}

export async function getProductionPlan(
    branchId: number,
    serviceDate: string,
    authorization: string,
    signal?: AbortSignal
): Promise<ProductionPlan> {
    const response = await adminFetch(
        `/api/admin/inventory/branches/${branchId}/production?serviceDate=${serviceDate}`,
        authorization,
        {method: "GET", cache: "no-store", signal}
    );
    return parseResponse(response, "Unable to load the production plan.");
}

export async function recordProducedStock(
    branchId: number,
    branchProductId: number,
    serviceDate: string,
    quantity: number,
    note: string | null,
    authorization: string
): Promise<ProductionPlanItem> {
    const response = await adminFetch(
        endpoint(branchId, branchProductId, serviceDate, "produced"),
        authorization,
        {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({quantity, note})
        }
    );
    return parseResponse(response, "Unable to record production.");
}

export async function recordWastage(
    branchId: number,
    branchProductId: number,
    serviceDate: string,
    quantity: number,
    reason: string,
    authorization: string
): Promise<ProductionPlanItem> {
    const response = await adminFetch(
        endpoint(branchId, branchProductId, serviceDate, "wastage"),
        authorization,
        {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({quantity, reason})
        }
    );
    return parseResponse(response, "Unable to record wastage.");
}

export async function recordStockAdjustment(
    branchId: number,
    branchProductId: number,
    serviceDate: string,
    quantityDelta: number,
    reason: string,
    authorization: string
): Promise<ProductionPlanItem> {
    const response = await adminFetch(
        endpoint(branchId, branchProductId, serviceDate, "adjustment"),
        authorization,
        {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({quantityDelta, reason})
        }
    );
    return parseResponse(response, "Unable to correct stock.");
}
