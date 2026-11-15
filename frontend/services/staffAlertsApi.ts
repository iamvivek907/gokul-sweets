import {ADMIN_API_BASE_URL} from "@/lib/constants";
import {applicationServerKey} from "@/lib/notificationAlerts";
import {setOrderPushBinding} from "@/lib/orderPushBinding";
import {adminFetch} from "@/services/adminApi";

export const STAFF_ALERT_DEVICE_RESTORED = "gokul-staff-alert-device-restored";
export const staffAlertDeviceKey = (staffId?: number) => `gokul-staff-push:${ADMIN_API_BASE_URL}:${staffId}`;

export const staffAlertPromptKey = (staffId: number) => `${staffAlertDeviceKey(staffId)}:setup-seen`;

export function readStaffAlertDevice(staffId: number) {
    try {
        const id = localStorage.getItem(staffAlertDeviceKey(staffId));
        return id && /^[0-9a-f-]{36}$/i.test(id) ? id : null;
    } catch {return null;}
}

export function supportsStaffPush() {
    return window.isSecureContext && "Notification" in window && "PushManager" in window && "serviceWorker" in navigator;
}

export function dismissStaffAlertSetup(staffId: number) {
    try {localStorage.setItem(staffAlertPromptKey(staffId), "1");} catch { /* This visit still honours dismissal. */ }
}

export type StaffAlertSettings = {enabled: boolean; environment: string; staffId: number; pushConfigured: boolean;
    applicationServerKey: string | null; deviceActive: boolean; emailConfigured: boolean; emailTestRouting?: boolean; reminderMinutes: number; escalationMinutes: number};
export type StaffAlert = {event: {id: number;enquiryId?:string|null;targetUrl?:string; orderNumber: string;customerOrderNumber?:number|null; branchId: number; kind: string; title: string; message: string; createdAt: string};
    readAt: string | null; actionRequired: boolean; pushState: string | null; emailState: string | null};
export type StaffInbox = {messages: StaffAlert[]; unreadCount: number; nextBefore: number | null; readThrough?: number};
export class StaffAlertRequestError extends Error {
    constructor(readonly status: number) {
        super(status === 401 ? "Sign in again to use staff alerts." : "Could not confirm this change. Check your connection and try again.");
    }
}
export async function staffAlertsRequest<T>(path = "", init?: RequestInit, staffId?: number): Promise<T> {
    const headers = new Headers(init?.headers);
    if (init?.body) headers.set("Content-Type", "application/json");
    const response = await adminFetch(`/api/admin/notifications${path}`, staffId === undefined ? "staff-session" : `staff-session:${staffId}`, {...init, headers});
    if (!response.ok) throw new StaffAlertRequestError(response.status);
    return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}


export function syncStaffAlertDevice(subscription: PushSubscription, staffId: number, signal: AbortSignal, registrationId?: string) {
    const keys = subscription.toJSON().keys;
    return staffAlertsRequest<{id: string | null}>("/push-subscriptions", {
        method: "POST", signal, body: JSON.stringify({
            endpoint: subscription.endpoint, publicKey: keys?.p256dh, authSecret: keys?.auth,
            expectedStaffId: staffId, registrationId, pausePrevious: !registrationId
        })
    }, staffId);
}

/** Start this call from the staff member's tap, before any asynchronous work. */
export async function enableStaffDeviceAlerts(settings: StaffAlertSettings, scope?: AbortSignal): Promise<NotificationPermission> {
    if (!settings.applicationServerKey) throw new Error("Staff notifications are unavailable.");
    scope?.throwIfAborted();
    const permission = Notification.permission === "default" ? await Notification.requestPermission() : Notification.permission;
    if (permission !== "granted") return permission;
    const timeout = AbortSignal.timeout(12000);
    const signal = scope ? AbortSignal.any([scope, timeout]) : timeout;
    let created: PushSubscription | null = null;
    let expire: () => void = () => {};
    const deadline = new Promise<never>((_, reject) => {
        expire = () => reject(signal.reason);
        signal.addEventListener("abort", expire, {once: true});
    });
    try {
        signal.throwIfAborted();
        const registration = await Promise.race([navigator.serviceWorker.register("/sw.js"), deadline]);
        if (!registration.active) await Promise.race([navigator.serviceWorker.ready, deadline]);
        let subscription = await Promise.race([registration.pushManager.getSubscription(), deadline]);
        if (!subscription) {
            subscription = await registration.pushManager.subscribe({userVisibleOnly: true, applicationServerKey: applicationServerKey(settings.applicationServerKey)});
            created = subscription;
        }
        signal.throwIfAborted();
        const keys = subscription.toJSON().keys;
        const result = await staffAlertsRequest<{id: string}>("/push-subscriptions", {
            method: "POST", signal, body: JSON.stringify({expectedStaffId: settings.staffId, endpoint: subscription.endpoint, publicKey: keys?.p256dh, authSecret: keys?.auth})
        }, settings.staffId);
        try {localStorage.setItem(staffAlertDeviceKey(settings.staffId), result.id);}
        catch {
            await staffAlertsRequest(`/push-subscriptions/${result.id}`, {method: "DELETE", signal: AbortSignal.timeout(8000)}, settings.staffId);
            throw new Error("This device could not save its alert registration. Try again from Staff notifications.");
        }
        await setOrderPushBinding("staff", String(settings.staffId), result.id, signal);
        dismissStaffAlertSetup(settings.staffId);
        window.dispatchEvent(new Event(STAFF_ALERT_DEVICE_RESTORED));
        return permission;
    } catch (error) {
        if (created) await created.unsubscribe().catch(() => false);
        throw error;
    } finally {signal.removeEventListener("abort", expire);}
}
