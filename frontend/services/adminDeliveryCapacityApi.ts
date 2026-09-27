import {adminFetch} from "@/services/adminApi";

export interface DeliveryWindow {
    id: number;
    zoneId: number;
    serviceDate: string;
    startsAt: string;
    endsAt: string;
    riderCapacity: number;
    reservedCount: number;
    paused: boolean;
}
export type WindowConfiguration = Omit<DeliveryWindow, "id" | "zoneId" | "reservedCount">;

async function checked(response: Response) {
    if (response.status === 404) throw new Error("Delivery capacity is disabled or the zone was not found.");
    if (response.status === 401 || response.status === 403) throw new Error("You cannot manage delivery capacity.");
    if (!response.ok) throw new Error("Could not update delivery capacity. Check the date, hours and current reservations.");
    return response;
}

export async function listDeliveryWindows(branchId: number, zoneId: number, auth: string, signal?: AbortSignal) {
    const response = await checked(await adminFetch(`/api/admin/branches/${branchId}/delivery-zones/${zoneId}/windows`, auth, {signal}));
    return response.json() as Promise<DeliveryWindow[]>;
}

export async function saveDeliveryWindow(branchId: number, zoneId: number, auth: string, window: WindowConfiguration) {
    const response = await checked(await adminFetch(`/api/admin/branches/${branchId}/delivery-zones/${zoneId}/windows`, auth,
        {method: "PUT", headers: {"Content-Type": "application/json"}, body: JSON.stringify(window)}));
    return response.json() as Promise<DeliveryWindow>;
}
