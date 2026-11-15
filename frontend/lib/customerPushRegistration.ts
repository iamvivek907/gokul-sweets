import {clearOrderPushBinding, readOrderPushBinding, setOrderPushBinding, switchOrderPushOwner} from "@/lib/orderPushBinding";
import {apiClient} from "@/services/apiClient";
import {applicationServerKey, type AlertSettings} from "@/lib/notificationAlerts";
export const PUSH_REGISTRATION_CHANGED = "gokul-push-registration-changed";
export const pushDeviceKey = (scope: string) => `gokul-push-device:${scope}`;

/** Only call from an explicit tap: permission is requested before the first asynchronous boundary. */
export async function registerCustomerPush(settings: AlertSettings): Promise<"granted" | "denied" | "default"> {
    const permission = Notification.permission === "default" ? await Notification.requestPermission() : Notification.permission;
    if (permission !== "granted") return permission;
    if (!settings.applicationServerKey) throw new Error("Push unavailable");
    let created: PushSubscription | null = null;
    try {
        const registration = await navigator.serviceWorker.register("/sw.js");
        await navigator.serviceWorker.ready;
        let subscription = await registration.pushManager.getSubscription();
        if (!subscription) {
            subscription = await registration.pushManager.subscribe({userVisibleOnly: true, applicationServerKey: applicationServerKey(settings.applicationServerKey)});
            created = subscription;
        }
        await confirmCustomerPush(settings, subscription);
        window.dispatchEvent(new Event(PUSH_REGISTRATION_CHANGED));
        return permission;
    } catch (error) {
        if (created) await created.unsubscribe().catch(() => false);
        throw error;
    }
}

/** Confirm an explicit device opt-in against the verified account; never grants marketing consent. */
export async function confirmCustomerPush(settings: AlertSettings, subscription: PushSubscription, registrationId?: string, signal?: AbortSignal) {
    const deadline = () => signal ? AbortSignal.any([signal, AbortSignal.timeout(8000)]) : AbortSignal.timeout(8000);
    const current = await apiClient<AlertSettings>("/api/customer/identity/notification-alerts", {credentials: "include", signal: deadline()});
    if (current.scopeId !== settings.scopeId) throw new Error("Account changed");
    const keys = subscription.toJSON().keys;
    const result = await apiClient<{id: string}>("/api/customer/identity/push-subscriptions", {method: "POST", credentials: "include",
        signal: deadline(), body: JSON.stringify({registrationId, expectedSubject: settings.scopeId, endpoint: subscription.endpoint, publicKey: keys?.p256dh, authSecret: keys?.auth})});
    if (registrationId) {
        await setOrderPushBinding("customer", settings.scopeId, registrationId, signal);
        return;
    }
    try {
        localStorage.setItem(pushDeviceKey(settings.scopeId), result.id);
        await setOrderPushBinding("customer", settings.scopeId, result.id, signal);
    }
    catch {
        await apiClient<void>(`/api/customer/identity/push-subscriptions/${result.id}`, {method: "DELETE", credentials: "include"});
        await subscription.unsubscribe();
        throw new Error("Push registration cannot be remembered");
    }
}

/** Same UTF-8 payload and SHA-256 encoding as the read-only server status endpoint. */
export async function pushSubscriptionFingerprint(subscription: PushSubscription) {
    const keys = subscription.toJSON().keys;
    if (!keys?.p256dh || !keys.auth) throw new Error("Push subscription keys unavailable");
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${subscription.endpoint}\n${keys.p256dh}\n${keys.auth}`));
    return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
}


/** Pause a previous account only when this browser still holds its push subscription. */
export async function pausePreviousCustomerAlerts(scopeId: string, signal: AbortSignal) {
    await switchOrderPushOwner("customer", scopeId, signal).catch(() => {});
    if (!("Notification" in window) || Notification.permission !== "granted") return;
    const registration = await navigator.serviceWorker?.getRegistration();
    const subscription = await registration?.pushManager.getSubscription();
    signal.throwIfAborted();
    if (!subscription) return;
    const keys = subscription.toJSON().keys;
    await apiClient("/api/customer/identity/push-subscriptions", {
        method: "POST", credentials: "include", signal,
        body: JSON.stringify({expectedSubject: scopeId, pausePrevious: true, endpoint: subscription.endpoint, publicKey: keys?.p256dh, authSecret: keys?.auth})
    });
    let deviceId: string | null = null;
    try {deviceId = localStorage.getItem(pushDeviceKey(scopeId));} catch { /* No remembered device. */ }
    if (!deviceId) return;
    const status = await apiClient<{enabled: boolean; fingerprint: string | null}>(`/api/customer/identity/push-subscriptions/${encodeURIComponent(deviceId)}`, {credentials: "include", signal});
    if (status.enabled && status.fingerprint === await pushSubscriptionFingerprint(subscription)) {
        await setOrderPushBinding("customer", scopeId, deviceId, signal);
    }
}

export async function logoutCustomerWithAlerts() {
    const binding = await readOrderPushBinding("customer");
    let device = binding?.registrationId;
    if (!device && binding?.owner) {
        try {device = localStorage.getItem(pushDeviceKey(binding.owner));} catch { /* No remembered registration. */ }
    }
    const path = device ? `?deviceId=${encodeURIComponent(device)}` : "";
    await apiClient<void>(`/api/customer/identity/logout${path}`, {method: "POST", credentials: "include"});
    await clearOrderPushBinding("customer", binding?.owner);
}
