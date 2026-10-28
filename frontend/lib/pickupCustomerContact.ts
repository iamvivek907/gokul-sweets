export function pickupCustomerPhone(value?: string | null): string | null {
  if (!value || value.length > 40 || !/^[+\d\s().-]+$/.test(value)) return null;
  let phone = value.replace(/[\s().-]/g, "");
  if (/^0[1-9]\d{9}$/.test(phone)) phone = phone.slice(1);
  if (/^[1-9]\d{9}$/.test(phone)) phone = "+91" + phone;
  else if (/^91[1-9]\d{9}$/.test(phone)) phone = "+" + phone;
  return /^\+[1-9]\d{7,14}$/.test(phone) ? phone : null;
}
export function canCallPickupCustomer(status: string, fulfillment: string): boolean {
  return fulfillment === "PICKUP" && ["CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "PICKUP_WINDOW_EXPIRED", "NO_SHOW"].includes(status);
}
