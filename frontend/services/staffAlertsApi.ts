import {ADMIN_API_BASE_URL} from "@/lib/constants";
import {applicationServerKey} from "@/lib/notificationAlerts";
import {bindStaffOrderPush, clearFailedStaffOrderPushBinding, clearOrderPushBinding, clearStaffOrderPushSetup, readOrderPushBinding, readStaffPushCancellation, signOutStaffOrderPushBinding, stageStaffOrderPushSetup, switchOrderPushOwner, writeStaffPushCancellation} from "@/lib/orderPushBinding";
import {adminFetch, logoutAdmin} from "@/services/adminApi";

export const STAFF_ALERT_DEVICE_RESTORED = "gokul-staff-alert-device-restored";
export const staffAlertDeviceKey = (staffId?: number) => `gokul-staff-push:${ADMIN_API_BASE_URL}:${staffId}`;

export const staffAlertPromptKey = (staffId: number) => `${staffAlertDeviceKey(staffId)}:setup-seen`;
const staffAlertCancellationKey = (staffId: number) => `${staffAlertDeviceKey(staffId)}:cancellation`;
const validRegistrationId = (id: unknown): id is string => typeof id === "string" && /^[0-9a-f-]{36}$/i.test(id);

async function pendingStaffAlertCancellation(staffId: number) {
    let saved: unknown;
    try {saved = JSON.parse(localStorage.getItem(staffAlertCancellationKey(staffId)) ?? "null");} catch { /* The worker marker can still be read. */ }
    const cached = await readStaffPushCancellation(String(staffId));
    return [...new Set([...cached, ...(Array.isArray(saved) ? saved : [])].filter(validRegistrationId))];
}

async function saveStaffAlertCancellation(staffId: number, ids: string[]) {
    try {
        if (ids.length) localStorage.setItem(staffAlertCancellationKey(staffId), JSON.stringify(ids));
        else localStorage.removeItem(staffAlertCancellationKey(staffId));
    } catch { /* CacheStorage can still record the intent. */ }
    await writeStaffPushCancellation(String(staffId), ids).catch(() => {});
}

export function readStaffAlertDevice(staffId: number) {
    try {
        const id = localStorage.getItem(staffAlertDeviceKey(staffId));
        return validRegistrationId(id) ? id : null;
    } catch {return null;}
}

async function staffAlertDeviceIds(staffId: number) {
    const remembered = readStaffAlertDevice(staffId);
    const binding = await readOrderPushBinding("staff");
    const owned = binding?.owner === String(staffId) ? binding : null;
    const bound = owned?.registrationId, staged = owned?.setupId;
    return {remembered, bound: validRegistrationId(bound) ? bound : null, staged: validRegistrationId(staged) ? staged : null};
}

export async function resolveStaffAlertDevice(staffId: number) {
    const {remembered, bound, staged} = await staffAlertDeviceIds(staffId);
    return bound ?? remembered ?? staged ?? (await pendingStaffAlertCancellation(staffId))[0] ?? null;
}

export async function resolveConfirmedStaffAlertDevice(staffId: number) {
    if ((await pendingStaffAlertCancellation(staffId)).length) return null;
    const {remembered, bound} = await staffAlertDeviceIds(staffId);
    return bound ?? remembered;
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
            if (await resolveConfirmedStaffAlertDevice(staffId) !== registrationId) throw new Error("This phone’s alert registration changed.");
            await verifyStaffOrderPushSignIn(staffId, signal);
            // A restore may release queued pushes before the response confirms the binding.
            if (!await bindStaffOrderPush(String(staffId), registrationId, signal, true)) {
                throw new Error("The active staff account changed. Reopen this page and try again.");
            }
        }
        return syncStaffPushRequest(subscription, staffId, signal, registrationId);
    }, signal);
}

async function verifyStaffOrderPushSignIn(staffId: number, signal: AbortSignal) {
    const binding = await readOrderPushBinding("staff");
    if (binding?.owner !== String(staffId) || !binding.signedOut) return;
    const settings = await staffAlertsRequest<StaffAlertSettings>("/settings", {signal}, staffId);
    if (settings.staffId !== staffId) throw new Error("The active staff account changed. Reopen this page and try again.");
}

function syncStaffPushRequest(subscription: PushSubscription, staffId: number, signal: AbortSignal, registrationId?: string) {
    const keys = subscription.toJSON().keys;
    return staffAlertsRequest<{id: string | null}>("/push-subscriptions", {
        method: "POST", signal, body: JSON.stringify({
            endpoint: subscription.endpoint, publicKey: keys?.p256dh, authSecret: keys?.auth,
            expectedStaffId: staffId, registrationId, pausePrevious: !registrationId
        })
    }, staffId);
}

async function staffSetupFingerprint(subscription: PushSubscription) {
    const keys = subscription.toJSON().keys;
    const data = new TextEncoder().encode(JSON.stringify([subscription.endpoint, keys?.p256dh, keys?.auth]));
    const digest = await crypto.subtle.digest("SHA-256", data);
    return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
}

/** Resolve a lost setup response only for the same signed-in account and browser subscription. */
export function recoverStaffAlertSetup(subscription: PushSubscription, staffId: number, signal: AbortSignal) {
    return withStaffAlertDevice(async () => {
        const owner = String(staffId);
        if ((await pendingStaffAlertCancellation(staffId)).length) return null;
        const binding = await readOrderPushBinding("staff");
        if (binding?.owner !== owner || !binding.setupId || !binding.setupFingerprint
            || binding.setupFingerprint !== await staffSetupFingerprint(subscription)) return null;
        const {setupId, setupFingerprint} = binding;
        await verifyStaffOrderPushSignIn(staffId, signal);
        if (!await stageStaffOrderPushSetup(owner, setupId, setupFingerprint, owner, signal)) return null;
        try {
            const result = await syncStaffPushRequest(subscription, staffId, signal, setupId);
            if (result.id !== setupId || !await bindStaffOrderPush(owner, setupId, signal, false, setupId)) return null;
            try {localStorage.setItem(staffAlertDeviceKey(staffId), setupId);} catch { /* The confirmed binding is retained. */ }
            return setupId;
        } catch (error) {
            if (error instanceof StaffAlertRequestError) {
                if (error.status === 410) await clearStaffOrderPushSetup(owner, setupId);
                if ([404, 410].includes(error.status)) return null;
            }
            throw error;
        }
    }, signal);
}

export function confirmStaffAlertDevice(staffId: number, registrationId: string, signal: AbortSignal) {
    return withStaffAlertDevice(async () => {
        if (await resolveConfirmedStaffAlertDevice(staffId) !== registrationId) return false;
        await verifyStaffOrderPushSignIn(staffId, signal);
        return bindStaffOrderPush(String(staffId), registrationId, signal);
    }, signal);
}

async function finishStaffAlertCancellation(staffId: number, ids: string[], scope?: AbortSignal) {
    const timeout = AbortSignal.timeout(8000);
    const signal = scope ? AbortSignal.any([scope, timeout]) : timeout;
    const results = await Promise.allSettled(ids.map(id =>
        staffAlertsRequest(`/push-subscriptions/${encodeURIComponent(id)}`, {method: "DELETE", signal}, staffId)
    ));
    const failure = results.find(result => result.status === "rejected");
    if (failure?.status === "rejected") throw failure.reason;
    const current = readStaffAlertDevice(staffId);
    if (current && ids.includes(current)) {
        try {localStorage.removeItem(staffAlertDeviceKey(staffId));} catch { /* Revocation succeeded. */ }
    }
    const owner = String(staffId);
    const binding = await readOrderPushBinding("staff");
    if (binding?.owner === owner && [binding.registrationId, binding.setupId].some(id => id && ids.includes(id))) {
        await clearOrderPushBinding("staff", owner, binding.registrationId ?? undefined);
    }
    await saveStaffAlertCancellation(staffId, []);
    dismissStaffAlertSetup(staffId);
}

/** Resume a previous Disable before attempting to restore push on this browser. */
export async function retryStaffAlertCancellation(staffId: number, signal: AbortSignal) {
    if (!(await pendingStaffAlertCancellation(staffId)).length) return;
    await withStaffAlertDevice(async () => {
        const ids = await pendingStaffAlertCancellation(staffId);
        if (ids.length) await finishStaffAlertCancellation(staffId, ids, signal);
    }, signal);
}

export function disableStaffDeviceAlerts(staffId: number) {
    return withStaffAlertDevice(async () => {
        const {remembered, bound, staged} = await staffAlertDeviceIds(staffId);
        const pending = await pendingStaffAlertCancellation(staffId);
        const ids = [...new Set([staged, bound, remembered, ...pending].filter(validRegistrationId))];
        if (!ids.length) throw new Error("Could not identify this phone’s alert registration. Push disable could not be confirmed.");
        await saveStaffAlertCancellation(staffId, ids);
        dismissStaffAlertSetup(staffId);
        await finishStaffAlertCancellation(staffId, ids);
    });
}

export function logoutStaffAlertDevice(staffId: number | undefined, signal: AbortSignal) {
    return withStaffAlertDevice(async () => {
        const owner = staffId === undefined ? undefined : String(staffId);
        const ids = staffId === undefined ? null : await staffAlertDeviceIds(staffId);
        const profile = await logoutAdmin(signal, ids?.bound ?? ids?.remembered, staffId, ids?.staged);
        if (!profile) await signOutStaffOrderPushBinding(owner);
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
        const cancellation = await pendingStaffAlertCancellation(settings.staffId);
        if (cancellation.length) await finishStaffAlertCancellation(settings.staffId, cancellation, signal);
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
        if (previousOwner !== owner || previousBinding?.signedOut) {
            const current = await staffAlertsRequest<StaffAlertSettings>("/settings", {signal}, settings.staffId);
            if (current.staffId !== settings.staffId) throw new Error("The active staff account changed. Reopen this page and try again.");
        }
        const fingerprint = await staffSetupFingerprint(subscription);
        const existingSetupId = previousBinding?.owner === owner && previousBinding.setupFingerprint === fingerprint
            ? previousBinding.setupId : undefined;
        const stage = async (id: string, retrying: boolean) => {
            setupId = id;
            // An earlier request may have committed even if this attempt cannot reach the server.
            registrationUncertain = retrying;
            if (!await stageStaffOrderPushSetup(owner, id, fingerprint, previousOwner, signal)) {
                throw new Error("The active staff account changed. Reopen this page and try again.");
            }
        };
        const register = (id: string, requestSignal: AbortSignal) =>
            staffAlertsRequest<{id: string; created?: boolean}>("/push-subscriptions",
                {method: "POST", signal: requestSignal, body: JSON.stringify({expectedStaffId: settings.staffId,
                    setupId: id, endpoint: subscription.endpoint, publicKey: keys?.p256dh, authSecret: keys?.auth})}, settings.staffId);
        const enroll = async (id: string, retrying: boolean) => {
            await stage(id, retrying);
            if (retrying) {
                try {
                    const recovered = await syncStaffPushRequest(subscription, settings.staffId, signal, id);
                    if (recovered.id === id) return {id, created: false};
                } catch (error) {
                    if (!(error instanceof StaffAlertRequestError && error.status === 404)) throw error;
                }
            }
            try {
                return await register(id, AbortSignal.any([signal, AbortSignal.timeout(5000)]));
            } catch (error) {
                if (!uncertainStaffSetup(error)) throw error;
                registrationUncertain = true;
                signal.throwIfAborted();
                return register(id, signal);
            }
        };
        let result;
        try {
            result = await enroll(existingSetupId ?? crypto.randomUUID(), Boolean(existingSetupId));
        } catch (error) {
            if (!existingSetupId || !(error instanceof StaffAlertRequestError && error.status === 410)) throw error;
            result = await enroll(crypto.randomUUID(), false);
        }
        registrationId = result.id;
        newRegistration = result.created ?? result.id !== previousDevice;
        await switchOrderPushOwner("staff", owner, signal, true, previousOwner).catch(() => {});
        if (!await bindStaffOrderPush(owner, result.id, signal, false, setupId)) {
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
