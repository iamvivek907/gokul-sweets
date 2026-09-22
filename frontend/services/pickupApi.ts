import {
    apiClient
} from "@/services/apiClient";

import type {
    PickupSlot
} from "@/types/pickup";


export async function getPickupSlots(
    branchId: number,
    date: string,
    signal?: AbortSignal
): Promise<PickupSlot[]> {

    const query =
        new URLSearchParams({
            date
        });


    const path =
        `/api/branches/${branchId}/pickup-slots?${query.toString()}`;


    console.log(
        "Loading pickup slots:",
        path
    );


    return apiClient<PickupSlot[]>(
        path,
        {
            method: "GET",
            signal
        }
    );
}