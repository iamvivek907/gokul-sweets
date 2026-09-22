import {
    apiClient
} from "@/services/apiClient";

import type {
    Branch
} from "@/types/branch";


export async function getActiveBranches(
    signal?: AbortSignal
): Promise<Branch[]> {

    return apiClient<Branch[]>(
        "/api/branches",
        {
            method: "GET",
            signal
        }
    );
}


export async function getBranch(
    branchId: number,
    signal?: AbortSignal
): Promise<Branch> {

    return apiClient<Branch>(
        `/api/branches/${branchId}`,
        {
            method: "GET",
            signal
        }
    );
}