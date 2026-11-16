import {ADMIN_API_BASE_URL} from "@/lib/constants";
import {applicationServerKey} from "@/lib/notificationAlerts";
import {bindStaffOrderPush, clearFailedStaffOrderPushBinding, clearOrderPushBinding, clearStaffOrderPushSetup, readOrderPushBinding, stageStaffOrderPushSetup, switchOrderPushOwner} from "@/lib/orderPushBinding";
import {adminFetch, logoutAdmin} from "@/services/adminApi";

export const STAFF_ALERT_DEVICE_RESTORED = "gokul-staff-alert-device-restored";
export const staffAlertDeviceKey = (staffId?: number) => `gokul-staff-push:${ADMIN_API_BASE_URL}:${staffId}`;

export const staffAlertPromptKey = (staffId: number) => `${staffAlertDeviceKey(staffId)}:setup-seen`;

export function readStaffAlertDevice(staffId: number) {
    try {
        const id = localStorage.getItem(staffAlertDeviceKey(staffId));
        return id && /^[0-9a-f-]{36}$/i.test(id) ? id : null;
    } catch {return null;}
}

export async function resolveStaffAlertDevice(staffId: number) {
    const remembered = readStaffAlertDevice(staffId);
    if (remembered) return remembered;
    const binding = await readOrderPushBinding("staff");
    const id = binding?.owner === String(staffId) ? binding.registrationId : null;
    return id && /^[0-9a-f-]{36}$/i.test(id) ? id : null;
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

let localDeviceOperation: Promise<unknown> = Promise.resolve();

/** One lock for the native subscription and this browser's staff registration across accounts. */
async function withStaffAlertDevice<T>(operation: () => Promise<T>, scope?: AbortSignal): Promise<T> {
    const wait = AbortSignal.timeout(25000);
    const signal = scope ? AbortSignal.any([scope, wait]) : wait;
    if (navigator.locks) {
        return navigator.locks.request("gokul-staff-push-setup", {signal}, operation);
    }
    const next = localDeviceOperation.then(() => {
        signal.throwIfAborted();
        return operation();
    });
    localDeviceOperation = next.catch(() => {});
    return next;
}

export function syncStaffAlertDevice(subscription: PushSubscription, staffId: number, signal: AbortSignal, registrationId?: string) {
    return withStaffAlertDevice(async () => {
        if (registrationId) {
            if (readStaffAlertDevice(staffId) !== registrationId) throw new Error("This phone’s alert registration changed.");
            // A restore may release queued pushes before the response confirms the binding.
            if (!await bindStaffOrderPush(String(staffId), registrationId, signal, true)) {
                throw new Error("The active staff account changed. Reopen this page and try again.");
            }
        }
        const keys = subscription.toJSON().keys;
        return staffAlertsRequest<{id: string | null}>("/push-subscriptions", {
            method: "POST", signal, body: JSON.stringify({
                endpoint: subscription.endpoint, publicKey: keys?.p256dh, authSecret: keys?.auth,
                expectedStaffId: staffId, registrationId, pausePrevious: !registrationId
            })
        }, staffId);
    }, signal);
}

export function confirmStaffAlertDevice(staffId: number, registrationId: string, signal: AbortSignal) {
    return withStaffAlertDevice(async () => readStaffAlertDevice(staffId) === registrationId
        && await bindStaffOrderPush(String(staffId), registrationId, signal), signal);
}

export function disableStaffDeviceAlerts(staffId: number) {
    return withStaffAlertDevice(async () => {
        const id = await resolveStaffAlertDevice(staffId);
        if (!id) throw new Error("Could not identify this phone’s alert registration. Push disable could not be confirmed.");
        await staffAlertsRequest(`/push-subscriptions/${encodeURIComponent(id)}`, {
            method: "DELETE", signal: AbortSignal.timeout(8000)
        }, staffId);
        if (readStaffAlertDevice(staffId) === id) {
            try {localStorage.removeItem(staffAlertDeviceKey(staffId));} catch { /* Revocation succeeded. */ }
        }
        await clearOrderPushBinding("staff", String(staffId), id);
        dismissStaffAlertSetup(staffId);
    });
}

export function logoutStaffAlertDevice(staffId: number | undefined, signal: AbortSignal) {
    return withStaffAlertDevice(async () => {
        const binding = await readOrderPushBinding("staff");
        const owner = staffId === undefined ? undefined : String(staffId);
        const remembered = staffId === undefined ? null : readStaffAlertDevice(staffId);
        const device = remembered ?? (binding?.owner === owner ? binding?.registrationId : null);
        const profile = await logoutAdmin(signal, device, staffId);
        if (!profile) await clearOrderPushBinding("staff", owner);
        return profile;
    }, signal);
}

/** Start this call from the staff member's tap, before any asynchronous work. */
export async function enableStaffDeviceAlerts(settings: StaffAlertSettings, scope?: AbortSignal): Promise<NotificationPermission> {
    const applicationKey = settings.applicationServerKey;
    if (!applicationKey) throw new Error("Staff notifications are unavailable.");
    scope?.throwIfAborted();
    const permission = Notification.permission === "default" ? await Notification.requestPermission() : Notification.permission;
    if (permission !== "granted") return permission;
    await withStaffAlertDevice(() => enrollStaffDevice(settings, applicationKey, scope), scope);
    return permission;
}

function uncertainStaffSetup(error: unknown) {
    return error instanceof TypeError || error instanceof SyntaxError
        || error instanceof DOMException && ["TimeoutError", "AbortError"].includes(error.name)
        || error instanceof StaffAlertRequestError && (error.status === 408 || error.status >= 500);
}

async function enrollStaffDevice(settings: StaffAlertSettings, applicationKey: string, scope?: AbortSignal) {
    const timeout = AbortSignal.timeout(12000);
    const signal = scope ? AbortSignal.any([scope, timeout]) : timeout;
    const owner = String(settings.staffId);
    const previousDevice = readStaffAlertDevice(settings.staffId);
    let created: PushSubscription | null = null;
    let registrationId: string | null = null;
    let newRegistration = false;
    let savedDevice = false;
    let registrationUncertain = false;
    let setupId: string | undefined;
    scope?.throwIfAborted();
    let expire: () => void = () => {};
    const deadline = new Promise<never>((_, reject) => {
        expire = () => reject(signal.reason);
        signal.addEventListener("abort", expire, {once: true});
    });
    try {
        signal.throwIfAborted();
        const previousBinding = await Promise.race([readOrderPushBinding("staff"), deadline]);
        const previousOwner = previousBinding?.owner ?? null;
        const registration = await Promise.race([navigator.serviceWorker.register("/sw.js"), deadline]);
        if (!registration.active) await Promise.race([navigator.serviceWorker.ready, deadline]);
        let subscription = await Promise.race([registration.pushManager.getSubscription(), deadline]);
        if (!subscription) {
            subscription = await Promise.race([
                registration.pushManager.subscribe({userVisibleOnly: true, applicationServerKey: applicationServerKey(applicationKey)}),
                deadline
            ]);
            created = subscription;
        }
        signal.throwIfAborted();
        const keys = subscription.toJSON().keys;
        if (previousOwner !== owner) {
            const current = await staffAlertsRequest<StaffAlertSettings>("/settings", {signal}, settings.staffId);
            if (current.staffId !== settings.staffId) throw new Error("The active staff account changed. Reopen this page and try again.");
        }
        const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(JSON.stringify([subscription.endpoint, keys?.p256dh, keys?.auth])));
        const fingerprint = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
        setupId = previousBinding?.owner === owner && previousBinding.setupFingerprint === fingerprint && previousBinding.setupId
            ? previousBinding.setupId : crypto.randomUUID();
        if (!await stageStaffOrderPushSetup(owner, setupId, fingerprint, previousOwner, signal)) {
            throw new Error("The active staff account changed. Reopen this page and try again.");
        }
        const body = JSON.stringify({expectedStaffId: settings.staffId, setupId,
            endpoint: subscription.endpoint, publicKey: keys?.p256dh, authSecret: keys?.auth});
        const register = (requestSignal: AbortSignal) =>
            staffAlertsRequest<{id: string; created?: boolean}>("/push-subscriptions",
                {method: "POST", signal: requestSignal, body}, settings.staffId);
        let result;
        try {
            result = await register(AbortSignal.any([signal, AbortSignal.timeout(5000)]));
        } catch (error) {
            if (!uncertainStaffSetup(error)) throw error;
            registrationUncertain = true;
            signal.throwIfAborted();
            result = await register(signal);
        }
        registrationId = result.id;
        newRegistration = result.created ?? result.id !== previousDevice;
        await switchOrderPushOwner("staff", owner, signal, true, previousOwner).catch(() => {});
        if (!await bindStaffOrderPush(owner, result.id, signal)) {
            throw new Error("The active staff account changed. Reopen this page and try again.");
        }
        signal.throwIfAborted();
        try {
            localStorage.setItem(staffAlertDeviceKey(settings.staffId), result.id);
            savedDevice = true;
        } catch {
            throw new Error("This device could not save its alert registration. Try again from Staff notifications.");
        }
        dismissStaffAlertSetup(settings.staffId);
        window.dispatchEvent(new Event(STAFF_ALERT_DEVICE_RESTORED));
    } catch (error) {
        if (setupId && (!registrationUncertain || registrationId)) {
            await clearStaffOrderPushSetup(owner, setupId).catch(() => {});
        }
        if (registrationId && newRegistration && (savedDevice || readStaffAlertDevice(settings.staffId) !== registrationId)) {
            if (savedDevice && readStaffAlertDevice(settings.staffId) === registrationId) {
                try {localStorage.removeItem(staffAlertDeviceKey(settings.staffId));} catch { /* Server cleanup still runs. */ }
            }
            await Promise.allSettled([
                clearFailedStaffOrderPushBinding(owner, registrationId),
                staffAlertsRequest(`/push-subscriptions/${encodeURIComponent(registrationId)}`, {
                    method: "DELETE", signal: AbortSignal.timeout(8000)
                }, settings.staffId),
                created?.unsubscribe()
            ]);
        } else if (created && !registrationId && !registrationUncertain) {
            await created.unsubscribe().catch(() => false);
        }
        throw error;
    } finally {signal.removeEventListener("abort", expire);}
}
