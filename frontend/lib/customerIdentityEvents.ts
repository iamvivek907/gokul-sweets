const eventName = "gokul-customer-identity-changed";
const revisionKey = "gokul-customer-identity-revision";

/** Publish only an invalidation token, never customer data. */
export function notifyCustomerIdentityChanged() {
 // Subscribers must see this revision when handling the synchronous event.
 try { localStorage.setItem(revisionKey, crypto.randomUUID()); }
 catch { /* Local invalidation must still run when storage is unavailable. */ }
 window.dispatchEvent(new Event(eventName));
}

export function subscribeCustomerIdentityChanges(onChange: () => void, {revalidateOnResume = true}: {revalidateOnResume?: boolean} = {}) {
 const storage = (event: StorageEvent) => { if (event.key === revisionKey || event.key === null) onChange(); };
 const resume = () => { if (revalidateOnResume && document.visibilityState === "visible") onChange(); };
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
