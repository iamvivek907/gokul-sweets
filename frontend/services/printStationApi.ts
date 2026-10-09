import {adminFetch} from "@/services/adminApi";

export type PrinterProfile = {
    branchId: number; station: string; agentId: string; printerCode: string;
    protocol: string; target: string; port: number; baudRate: number; paperWidthMm: number; autoCut: boolean;
};
export type PrintStation = {
    configured: boolean; profile?: PrinterProfile; enabled?: boolean; online?: boolean;
    runtime?: {status?: string; message?: string; pendingJobId?: number; pendingState?: string;
        devices?: {usb?: string[]; bluetooth?: string[]}};
    command?: {id?: string}; commandResult?: {status?: string; message?: string};
};
export class PrintStationRequestError extends Error {
    constructor(message: string, readonly status: number) {super(message);}
}
export async function printStationRequest(authorization: string, branchId: number, station: string,
    operation = "", body?: object, signal?: AbortSignal): Promise<PrintStation> {
    const params = new URLSearchParams({branchId: String(branchId), station});
    const response = await adminFetch(`/api/admin/printing/station${operation}?${params}`, authorization,
        {method: body ? "POST" : "GET", headers: body ? {"Content-Type": "application/json"} : undefined,
            body: body ? JSON.stringify(body) : undefined, signal});
    if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new PrintStationRequestError(error.message || error.detail || "Unable to update printer setup. Check your permissions and connection.", response.status);
    }
    return response.json();
}
