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
export async function confirmCustomerPush(settings: AlertSettings, subscription: PushSubscription) {
    const current = await apiClient<AlertSettings>("/api/customer/identity/notification-alerts", {credentials: "include", signal: AbortSignal.timeout(8000)});
    if (current.scopeId !== settings.scopeId) throw new Error("Account changed");
    const keys = subscription.toJSON().keys;
    const result = await apiClient<{id: string}>("/api/customer/identity/push-subscriptions", {method: "POST", credentials: "include",
        signal: AbortSignal.timeout(8000), body: JSON.stringify({endpoint: subscription.endpoint, publicKey: keys?.p256dh, authSecret: keys?.auth})});
    try {localStorage.setItem(pushDeviceKey(settings.scopeId), result.id);}
    catch {
        await apiClient<void>(`/api/customer/identity/push-subscriptions/${result.id}`, {method: "DELETE", credentials: "include"});
        await subscription.unsubscribe();
        throw new Error("Push registration cannot be remembered");
    }
}
