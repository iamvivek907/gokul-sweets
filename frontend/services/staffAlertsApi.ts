import {adminFetch} from "@/services/adminApi";

export type StaffAlertSettings = {enabled: boolean; environment: string; staffId: number; pushConfigured: boolean;
    applicationServerKey: string | null; deviceActive: boolean; emailConfigured: boolean; reminderMinutes: number; escalationMinutes: number};
export type StaffAlert = {event: {id: number; orderNumber: string; branchId: number; kind: string; title: string; message: string; createdAt: string};
    readAt: string | null; actionRequired: boolean; pushState: string | null; emailState: string | null};
export type StaffInbox = {messages: StaffAlert[]; unreadCount: number; nextBefore: number | null};
export async function staffAlertsRequest<T>(path = "", init?: RequestInit): Promise<T> {
    const headers = new Headers(init?.headers);
    if (init?.body) headers.set("Content-Type", "application/json");
    const response = await adminFetch(`/api/admin/notifications${path}`, "staff-session", {...init, headers});
    if (!response.ok) throw new Error(response.status === 401 ? "Sign in again to use staff alerts." : "Could not confirm this change. Check your connection and try again.");
    return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}
