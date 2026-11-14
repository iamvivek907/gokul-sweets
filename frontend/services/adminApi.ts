import type {AdminProfile} from "@/types/admin";
import {ADMIN_API_BASE_URL} from "@/lib/constants";

const API_BASE = ADMIN_API_BASE_URL;
let csrfToken: string | null = null;
let verifiedStaffId: number | null = null;
let sessionVersion = 0;

export const staffSessionCheckEvent = "gokul-admin-check-session";
export const staffCsrfRejectedEvent = "gokul-admin-csrf-rejected";
export const staffReauthenticationEvent = "gokul-admin-reauthenticate";

function rememberSession(profile: AdminProfile | null, response: Response) {
    verifiedStaffId = profile?.staffId ?? null;
    csrfToken = profile ? response.headers.get("X-Staff-CSRF") : null;
}
export class StaffEnrollmentRequired extends Error {
    constructor(readonly token: string) {super("Set up an authenticator before accessing the staff portal.");}
}
async function request(path: string, body: object) {
    return fetch(`${API_BASE}${path}`, {method: "POST", credentials: "include", cache: "no-store",
        signal: AbortSignal.timeout(15000), headers: {"Content-Type": "application/json"}, body: JSON.stringify(body)});
}
export async function authenticateAdmin(username: string, password: string, code?: string): Promise<AdminProfile> {
    const response = await request("/api/admin/auth/login", {username: username.trim().toLowerCase(), password, code});
    if (response.status === 428) {
        const result = await response.json() as {enrollmentToken: string};
        throw new StaffEnrollmentRequired(result.enrollmentToken);
    }
    if (response.status === 401 || response.status === 400) throw new Error("Invalid staff credentials or authenticator code.");
    if (response.status === 429) throw new Error("Too many sign-in attempts. Try again later.");
    if (!response.ok) throw new Error("Unable to sign in. Check that secure staff sessions are enabled.");
    const {profile} = await response.json() as {profile: AdminProfile};
    sessionVersion++;
    rememberSession(profile, response);
    return profile;
}
export async function setupStaffMfa(token: string): Promise<{secret: string; uri: string}> {
    const response = await request("/api/admin/auth/mfa/setup", {token});
    if (!response.ok) throw new Error("Enrollment expired. Sign in again.");
    return response.json();
}
export async function confirmStaffMfa(token: string, code: string): Promise<{profile: AdminProfile; recoveryCodes: string[]}> {
    const response = await request("/api/admin/auth/mfa/confirm", {token, code});
    if (!response.ok) throw new Error("Invalid authenticator code. Check your device clock and retry.");
    const result = await response.json() as {profile: AdminProfile; recoveryCodes: string[]};
    sessionVersion++;
    rememberSession(result.profile, response);
    return result;
}
export async function fetchAdminProfile(signal?: AbortSignal): Promise<AdminProfile | null> {
    const version = sessionVersion;
    const timeout = AbortSignal.timeout(10000);
    const requestSignal = signal ? AbortSignal.any([signal, timeout]) : timeout;
    const response = await fetch(`${API_BASE}/api/admin/auth/me`,
        {credentials: "include", cache: "no-store", signal: requestSignal});
    if (response.status !== 401 && !response.ok) {
        throw new Error("Unable to verify staff session. Try again.");
    }
    const profile = response.status === 401 ? null : await response.json() as AdminProfile;
    requestSignal.throwIfAborted();
    if (version !== sessionVersion) throw new Error("A newer staff session is active.");
    rememberSession(profile, response);
    return profile;
}
export function invalidateAdminSessionCheck() {
    sessionVersion++;
    csrfToken = null;
    verifiedStaffId = null;
}

export async function logoutAdmin(signal?: AbortSignal): Promise<AdminProfile | null> {
    const timeout = AbortSignal.timeout(10000);
    const requestSignal = signal ? AbortSignal.any([signal, timeout]) : timeout;
    try {
        const response = await adminFetch("/api/admin/auth/logout", "staff-session", {
            method: "POST", signal: requestSignal
        });
        if (response.ok) {
            signal?.throwIfAborted();
            invalidateAdminSessionCheck();
            return null;
        }
    } catch {
        // The server may have revoked the session before the response was lost.
    }

    signal?.throwIfAborted();
    const profile = await fetchAdminProfile(signal).catch(() => {
        throw new Error("Sign-out could not be confirmed. Check your connection and try again.");
    });
    if (!profile) invalidateAdminSessionCheck();
    return profile;
}
/** Scoped callers bind a write to the staff identity shown on their page. */
export async function adminFetch(path: string, authorization: string, init?: RequestInit): Promise<Response> {
    const version = sessionVersion;
    const headers = new Headers(init?.headers);
    const method = (init?.method ?? "GET").toUpperCase();
    const changing = !["GET", "HEAD", "OPTIONS"].includes(method);
    if (changing) {
        if (verifiedStaffId !== null && authorization.startsWith("staff-session:")
            && authorization !== `staff-session:${verifiedStaffId}`) {
            throw new Error("The active staff account changed. Reopen this page and try again.");
        }
        if (!csrfToken) {
            if (typeof window !== "undefined") window.dispatchEvent(new Event(staffSessionCheckEvent));
            throw new Error("Unable to verify the session for this change. Check your connection and try again.");
        }
        headers.set("X-Staff-CSRF", csrfToken);
    }
    const response = await fetch(`${API_BASE}${path}`, {...init, headers, credentials: "include", cache: "no-store"});
    if (typeof window !== "undefined" && version === sessionVersion) {
        if (response.status === 401) {
            window.dispatchEvent(new Event(staffSessionCheckEvent));
        } else if (response.status === 403 && changing
            && response.headers.get("Content-Type")?.includes("application/json")) {
            const body = await response.clone().json().catch(() => null);
            if (version === sessionVersion && body?.code === "STAFF_REAUTH_REQUIRED") {
                window.dispatchEvent(new Event(staffReauthenticationEvent));
                throw new Error("Confirm your sign-in, then try the original action again.");
            }
            if (version === sessionVersion && body?.code === "STAFF_CSRF_INVALID") {
                window.dispatchEvent(new Event(staffCsrfRejectedEvent));
                throw new Error("Refreshing your session for this change. Try the action again shortly.");
            }
        }
    }
    return response;
}
