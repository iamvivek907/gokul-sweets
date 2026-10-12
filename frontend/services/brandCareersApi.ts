import {adminFetch} from "./adminApi";
export async function brandAdmin<T>(path: string, method = "GET", body?: object | FormData, version?: number, signal?: AbortSignal): Promise<T> {
    const headers = new Headers();
    if (version !== undefined) headers.set("If-Match", String(version));
    if (body && !(body instanceof FormData)) headers.set("Content-Type", "application/json");
    const response = await adminFetch(path, "staff-session", {method, headers, signal,
        body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined});
    if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(response.status === 409 ? "Someone changed this record. Reload before saving again." : error.message ?? `Request failed (${response.status}).`);
    }
    if (response.status === 204 || response.headers.get("content-length") === "0") return undefined as T;
    const text = await response.text();
    return text ? JSON.parse(text) as T : undefined as T;
}
