import {adminFetch} from "@/services/adminApi";

export async function adminManagementApi<T>(
    path: string, authorization: string, options: RequestInit = {}
): Promise<T> {
    const response = await adminFetch(path, authorization, options);
    if (!response.ok) {
        const body = await response.text();
        let message = `Request failed (${response.status}).`;
        try {
            const error: {message?: string} = JSON.parse(body);
            if (error.message) message = error.message;
        } catch {
            // Proxies can return non-JSON errors; retain the HTTP status.
        }
        throw new Error(message);
    }
    return response.status === 204 ? undefined as T : response.json();
}

export interface TaxCategory {
    id: number;
    code: string;
    name: string;
    hsnSacCode: string | null;
    cgstRate: number;
    sgstRate: number;
    igstRate: number;
    active: boolean;
}
