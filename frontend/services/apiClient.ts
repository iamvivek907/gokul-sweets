import {API_BASE_URL} from "@/lib/constants";

interface ApiRequestOptions extends RequestInit {
    cacheMode?: "no-store" | "force-cache";
}

export interface ApiErrorBody {
    timestamp?: string;
    status?: number;
    error?: string;
    code?: string;
    message?: string;
    path?: string;
    details?: Record<string, unknown>;
}

export class ApiError extends Error {
    constructor(
        message: string,
        public readonly status: number,
        public readonly code: string,
        public readonly details: Record<string, unknown>
    ) {
        super(message);
        this.name = "ApiError";
    }
}

export async function apiClient<T>(
    path: string,
    options: ApiRequestOptions = {}
): Promise<T> {
    const {
        cacheMode = "no-store",
        headers,
        ...requestOptions
    } = options;

    const requestHeaders = new Headers(headers);

    /*
     * JSON bodies should receive application/json.
     *
     * FormData must NOT receive a Content-Type header here.
     * The browser automatically creates:
     *
     * multipart/form-data; boundary=...
     *
     * when the body is FormData.
     */
    if (
        requestOptions.body &&
        !(requestOptions.body instanceof FormData) &&
        !requestHeaders.has("Content-Type")
    ) {
        requestHeaders.set("Content-Type", "application/json");
    }

    let response: Response;

    try {
        response = await fetch(`${API_BASE_URL}${path}`, {
            ...requestOptions,
            cache: cacheMode,
            headers: requestHeaders
        });
    } catch (error) {
        if (
            error instanceof DOMException &&
            error.name === "AbortError"
        ) {
            throw error;
        }

        console.error("API connection failed:", {
            url: `${API_BASE_URL}${path}`,
            error
        });

        throw new ApiError(
            "Unable to connect to the restaurant server.",
            0,
            "CONNECTION_FAILED",
            {}
        );
    }

    if (!response.ok) {
        let body: ApiErrorBody = {};

        try {
            body = await response.json() as ApiErrorBody;
        } catch {
            // Non-JSON error response.
        }

        throw new ApiError(
            body.message ??
            body.error ??
            `Request failed (${response.status}).`,
            response.status,
            body.code ?? "REQUEST_FAILED",
            body.details ?? {}
        );
    }

    if (response.status === 204) {
        return undefined as T;
    }

    return response.json() as Promise<T>;
}