export const MIN_MANUAL_PAYMENT_CHECK_MS = 10_000;

export function isTemporaryPaymentFailure(status: number): boolean {
    return status === 0 || status === 429 || status >= 500;
}

export function nextPaymentPollDelayMs(successes: number, failures: number, jitter = 0): number {
    const base = failures > 0 ? Math.min(120_000, 30_000 * 2 ** Math.min(failures - 1, 2))
        : successes < 2 ? 15_000 : 30_000;
    return base + Math.floor(base * 0.15 * Math.max(0, Math.min(1, jitter)));
}

export function retryAfterDelayMs(value: string | null, nowMs: number): number | null {
    if (!value) return null;
    const seconds = /^\d+$/.test(value.trim()) ? Number(value.trim()) : NaN;
    const duration = Number.isFinite(seconds) ? seconds * 1000 : Date.parse(value) - nowMs;
    return Number.isFinite(duration) && duration > 0 ? Math.min(duration, 300_000) : null;
}
