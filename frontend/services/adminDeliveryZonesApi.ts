import {adminFetch} from "@/services/adminApi";

export interface DeliveryZone {
    id: number;
    branchId: number;
    locality: string;
    postalCode: string;
    opensAt: string;
    closesAt: string;
    active: boolean;
    riderPaused: boolean;
    productIds: number[];
}

export type ZoneConfiguration = Omit<DeliveryZone, "id" | "branchId">;

async function checked(response: Response) {
    if (response.status === 404) throw new Error("Delivery zone configuration is disabled.");
    if (response.status === 401 || response.status === 403) throw new Error("You cannot manage delivery zones.");
    if (!response.ok) throw new Error("Unable to save or load delivery zones. Check the zone and product settings.");
    return response;
}

export async function listDeliveryZones(branchId: number, auth: string, signal?: AbortSignal) {
    const response = await checked(await adminFetch(`/api/admin/branches/${branchId}/delivery-zones`, auth, {signal}));
    return response.json() as Promise<DeliveryZone[]>;
}

export async function saveDeliveryZone(branchId: number, auth: string, zone: ZoneConfiguration) {
    const response = await checked(await adminFetch(`/api/admin/branches/${branchId}/delivery-zones`, auth,
        {method: "PUT", headers: {"Content-Type": "application/json"}, body: JSON.stringify(zone)}));
    return response.json() as Promise<DeliveryZone>;
}
