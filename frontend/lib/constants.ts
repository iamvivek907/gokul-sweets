export const APP_NAME = "Gokul Sweets";

export const APP_DESCRIPTION =
    "Fresh sweets, food and snacks available for convenient pickup.";

export const API_BASE_URL =
    process.env.NEXT_PUBLIC_API_URL ??
    "http://localhost:8080";

/**
 * Rollout switch for interpreting zone-less legacy API timestamps as IST.
 * Set NEXT_PUBLIC_IST_TIME_FIX_ENABLED=false at build time to roll back.
 * The backend's GOKUL_IST_TIME_FIX_ENABLED switch should be rolled out with it.
 */
export const IST_TIME_FIX_ENABLED =
    process.env.NEXT_PUBLIC_IST_TIME_FIX_ENABLED !== "false";

/** Business-facing dates and times for the current India-only storefront. */
export const BUSINESS_TIME_ZONE = "Asia/Kolkata";
