export type AlertSettings = {soundEnabled: boolean; quietHoursEnabled: boolean; quietStartMinute: number;
    quietEndMinute: number; scopeId: string; pushConfigured: boolean; applicationServerKey: string | null};

export function inQuietHours(settings: Pick<AlertSettings, "quietHoursEnabled" | "quietStartMinute" | "quietEndMinute">, now = new Date()) {
    if (!settings.quietHoursEnabled) return false;
    const parts = new Intl.DateTimeFormat("en-GB", {timeZone: "Asia/Kolkata", hour: "2-digit", minute: "2-digit", hourCycle: "h23"}).formatToParts(now);
    const minute = Number(parts.find(part => part.type === "hour")?.value) * 60 + Number(parts.find(part => part.type === "minute")?.value);
    const {quietStartMinute: start, quietEndMinute: end} = settings;
    return start < end ? minute >= start && minute < end : minute >= start || minute < end;
}
export const minuteToTime = (minute: number) => `${String(Math.floor(minute / 60)).padStart(2, "0")}:${String(minute % 60).padStart(2, "0")}`;
export const timeToMinute = (value: string) => {const [hour, minute] = value.split(":").map(Number); return hour * 60 + minute;};

let audio: AudioContext | null = null;
/** Call only from a user's tap. No automatic permission or audio unlock. */
export async function activateChime() {
    if (!("AudioContext" in window)) throw new Error("Sound unsupported");
    audio ??= new AudioContext();
    await audio.resume();
    if (audio.state !== "running") throw new Error("Sound blocked");
    playChime();
}
export function playChime() {
    if (!audio || audio.state !== "running" || document.visibilityState !== "visible") return false;
    try {
        const oscillator = audio.createOscillator(), gain = audio.createGain();
        oscillator.frequency.value = 660;
        gain.gain.setValueAtTime(0.035, audio.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.001, audio.currentTime + 0.2);
        oscillator.connect(gain); gain.connect(audio.destination);
        oscillator.start(); oscillator.stop(audio.currentTime + 0.2);
        return true;
    } catch {return false;}
}
export function applicationServerKey(value: string): Uint8Array<ArrayBuffer> {
    const decoded = atob(value.replace(/-/g, "+").replace(/_/g, "/"));
    return Uint8Array.from(decoded, character => character.charCodeAt(0));
}
