import type {AdminProfile} from "@/types/admin";
import {ADMIN_API_BASE_URL} from "@/lib/constants";

const API_BASE = ADMIN_API_BASE_URL;
let csrfToken: string | null = null;

function rememberCsrf(response: Response) {
    csrfToken = response.headers.get("X-Staff-CSRF");
}
export class StaffEnrollmentRequired extends Error {
    constructor(readonly token: string) {super("Set up an authenticator before accessing the staff portal.");}
}
async function request(path: string, body: object) {
    return fetch(`${API_BASE}${path}`, {method: "POST", credentials: "include", cache: "no-store",
        headers: {"Content-Type": "application/json"}, body: JSON.stringify(body)});
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
    rememberCsrf(response);
    return (await response.json() as {profile: AdminProfile}).profile;
}
export async function setupStaffMfa(token: string): Promise<{secret: string; uri: string}> {
    const response = await request("/api/admin/auth/mfa/setup", {token});
    if (!response.ok) throw new Error("Enrollment expired. Sign in again.");
    return response.json();
}
export async function confirmStaffMfa(token: string, code: string): Promise<{profile: AdminProfile; recoveryCodes: string[]}> {
    const response = await request("/api/admin/auth/mfa/confirm", {token, code});
    if (!response.ok) throw new Error("Invalid authenticator code. Check your device clock and retry.");
    rememberCsrf(response);
    return response.json();
}
export async function fetchAdminProfile(signal?: AbortSignal): Promise<AdminProfile | null> {
    const response = await fetch(`${API_BASE}/api/admin/auth/me`,
        {credentials: "include", cache: "no-store", signal});
    if (response.status === 401 || response.status === 403) {csrfToken = null; return null;}
    if (!response.ok) throw new Error("Unable to verify staff session. Try again.");
    rememberCsrf(response);
    return response.json();
}
export async function logoutAdmin(): Promise<void> {
    try {await adminFetch("/api/admin/auth/logout", "staff-session", {method: "POST"});}
    finally {csrfToken = null;}
}
/** The authorization argument remains for existing admin callers; it is only a presence marker. */
export async function adminFetch(path: string, _authorization: string, init?: RequestInit): Promise<Response> {
    const headers = new Headers(init?.headers);
    const method = (init?.method ?? "GET").toUpperCase();
    if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
        if (!csrfToken) throw new Error("Staff session expired. Sign in again.");
        headers.set("X-Staff-CSRF", csrfToken);
    }
    const response = await fetch(`${API_BASE}${path}`, {...init, headers, credentials: "include", cache: "no-store"});
    if (response.status === 401) {
        csrfToken = null;
        if (typeof window !== "undefined") window.dispatchEvent(new Event("gokul-admin-expired"));
    }
    return response;
}
