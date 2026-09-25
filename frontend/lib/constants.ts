export const APP_NAME = "Gokul Sweets";

export const APP_DESCRIPTION =
    "Fresh sweets, food and snacks available for convenient pickup.";

/**
 * Both customer and admin requests use the same backend for this build.
 * NEXT_PUBLIC_* is embedded at build time: rebuild for each environment.
 * A production build must be given an explicit HTTPS backend origin.
 */
const customerApiUrl = process.env.NEXT_PUBLIC_API_URL;
const adminApiUrl = process.env.NEXT_PUBLIC_API_BASE_URL;

function apiOrigin(value: string): string {
    let url: URL;
    try { url = new URL(value); }
    catch { throw new Error("API origin must be an absolute URL."); }
    if (url.origin !== value || url.username || url.password ||
        (url.protocol !== "https:" &&
            !(process.env.NODE_ENV !== "production" &&
                url.protocol === "http:" && url.hostname === "localhost"))) {
        throw new Error("API origin must be an HTTPS origin (localhost HTTP is development-only).");
    }
    return url.origin;
}

if (customerApiUrl && adminApiUrl && customerApiUrl !== adminApiUrl) {
    throw new Error("Customer and admin API origins must match in this build.");
}
if (process.env.NODE_ENV === "production" && !customerApiUrl) {
    throw new Error("NEXT_PUBLIC_API_URL is required for a production build.");
}

export const API_BASE_URL = apiOrigin(customerApiUrl ?? adminApiUrl ?? "http://localhost:8080");
export const ADMIN_API_BASE_URL = API_BASE_URL;

/**
 * Rollout switch for interpreting zone-less legacy API timestamps as IST.
 * Set NEXT_PUBLIC_IST_TIME_FIX_ENABLED=false at build time to roll back.
 * The backend's GOKUL_IST_TIME_FIX_ENABLED switch should be rolled out with it.
 */
export const IST_TIME_FIX_ENABLED =
    process.env.NEXT_PUBLIC_IST_TIME_FIX_ENABLED !== "false";

/** Business-facing dates and times for the current India-only storefront. */
export const BUSINESS_TIME_ZONE = "Asia/Kolkata";
