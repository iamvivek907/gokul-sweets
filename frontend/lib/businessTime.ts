import {BUSINESS_TIME_ZONE, IST_TIME_FIX_ENABLED} from "@/lib/constants";

const LEGACY_LOCAL_TIMESTAMP =
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?$/;

/**
 * Legacy Java LocalDateTime JSON has no offset, although these values represent
 * India business time. Explicit Z/offset timestamps already identify an instant.
 */
export function parseBusinessTimestamp(value: string): Date {
    const input = IST_TIME_FIX_ENABLED && LEGACY_LOCAL_TIMESTAMP.test(value)
        ? `${value}+05:30`
        : value;
    return new Date(input);
}

export function formatBusinessTimestamp(
    value: string,
    options: Intl.DateTimeFormatOptions
): string {
    const date = parseBusinessTimestamp(value);
    if (Number.isNaN(date.getTime())) return value;
    return new Intl.DateTimeFormat("en-IN", {
        ...options,
        timeZone: IST_TIME_FIX_ENABLED ? BUSINESS_TIME_ZONE : options.timeZone
    }).format(date);
}

/** A date-only pickup value is a calendar date in India, not a UTC instant. */
export function parseBusinessDate(value: string): Date {
    return new Date(`${value}T12:00:00+05:30`);
}

/** Calendar arithmetic starts from India's date, independent of browser/server zone. */
export function businessDateOffset(offsetDays: number, now = new Date()): string {
    const parts = new Intl.DateTimeFormat("en-GB", {
        timeZone: BUSINESS_TIME_ZONE, year: "numeric", month: "2-digit", day: "2-digit"
    }).formatToParts(now);
    const read = (part: string) => parts.find(value => value.type === part)?.value;
    const year = read("year");
    const month = read("month");
    const day = read("day");
    if (!year || !month || !day) throw new Error("India business date unavailable");
    const date = parseBusinessDate(`${year}-${month}-${day}`);
    date.setUTCDate(date.getUTCDate() + offsetDays);
    const shifted = new Intl.DateTimeFormat("en-GB", {
        timeZone: BUSINESS_TIME_ZONE, year: "numeric", month: "2-digit", day: "2-digit"
    }).formatToParts(date);
    const value = (part: string) => shifted.find(item => item.type === part)?.value;
    return `${value("year")}-${value("month")}-${value("day")}`;
}

/** Pickup LocalTime is an India wall-clock value, never the device's zone. */
export function formatBusinessTime(value: string): string {
    if (!IST_TIME_FIX_ENABLED) {
        const [hour, minute] = value.split(":").map(Number);
        const date = new Date();
        date.setHours(hour, minute, 0, 0);
        return new Intl.DateTimeFormat("en-IN", {hour: "numeric", minute: "2-digit"}).format(date);
    }
    const match = /^(\d{2}):(\d{2})(?::\d{2})?$/.exec(value);
    if (!match) return value;
    const hour = Number(match[1]);
    const minute = Number(match[2]);
    if (hour > 23 || minute > 59) return value;
    return new Intl.DateTimeFormat("en-IN", {
        hour: "numeric", minute: "2-digit", timeZone: "UTC"
    }).format(new Date(Date.UTC(2020, 0, 1, hour, minute)));
}
