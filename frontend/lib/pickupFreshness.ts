import type {PickupSelection} from "../types/pickup";
export function indiaToday(now = new Date()): string {
 const parts = new Intl.DateTimeFormat("en-GB", {timeZone:"Asia/Kolkata",year:"numeric",month:"2-digit",day:"2-digit"}).formatToParts(now);
 const get = (type: string) => parts.find(part => part.type === type)?.value;
 return `${get("year")}-${get("month")}-${get("day")}`;
}
export function validPickupDate(date: string, today: string, days: number): boolean {
 if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return false;
 const value = new Date(`${date}T12:00:00+05:30`), maximum = new Date(`${today}T12:00:00+05:30`);
 if (!Number.isFinite(value.getTime()) || indiaToday(value) !== date) return false;
 maximum.setUTCDate(maximum.getUTCDate()+days);
 return date >= today && date <= indiaToday(maximum);
}
export function pickupIsFresh(selection: PickupSelection, now: Date): boolean {
 return selection.date === selection.slot.slotDate && selection.slot.active && selection.date >= indiaToday(now) && Date.parse(`${selection.date}T${selection.slot.startTime}+05:30`) > now.getTime();
}
