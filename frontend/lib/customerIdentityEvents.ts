const eventName = "gokul-customer-identity-changed";
const revisionKey = "gokul-customer-identity-revision";

/** Publish only an invalidation token, never customer data. */
export function notifyCustomerIdentityChanged() {
 window.dispatchEvent(new Event(eventName));
 try { localStorage.setItem(revisionKey, crypto.randomUUID()); }
 catch { /* Resume revalidation still works when storage is unavailable. */ }
}

export function subscribeCustomerIdentityChanges(onChange: () => void) {
 const storage = (event: StorageEvent) => { if (event.key === revisionKey || event.key === null) onChange(); };
 const resume = () => { if (document.visibilityState === "visible") onChange(); };
 const pageshow = (event: PageTransitionEvent) => { if (event.persisted) resume(); };
 window.addEventListener(eventName, onChange);
 window.addEventListener("storage", storage);
 window.addEventListener("focus", resume);
 window.addEventListener("pageshow", pageshow);
 document.addEventListener("visibilitychange", resume);
 return () => {
  window.removeEventListener(eventName, onChange);
  window.removeEventListener("storage", storage);
  window.removeEventListener("focus", resume);
  window.removeEventListener("pageshow", pageshow);
  document.removeEventListener("visibilitychange", resume);
 };
}
