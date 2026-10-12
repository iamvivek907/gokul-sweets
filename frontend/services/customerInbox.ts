import {apiClient} from "@/services/apiClient";
import {subscribeCustomerIdentityChanges} from "@/lib/customerIdentityEvents";

export type CustomerInbox = {
    unreadCount: number;
    messages: Array<{id: number; readAt: string | null; createdAt: string}>;
};

type Read = {controller: AbortController; promise: Promise<CustomerInbox>; consumers: number};
let pending: Read | null = null;

function invalidate() {
    pending?.controller.abort();
    pending = null;
}

// Only in-flight reads are shared. Private inbox data is never retained after settlement.
// Register before component effects so identity/read changes invalidate before their refreshes.
if (typeof window !== "undefined") {
    subscribeCustomerIdentityChanges(invalidate, {revalidateOnResume: false});
    window.addEventListener("gokul-inbox-changed", invalidate);
}

/** Share one live inbox read; cancelling one subscriber leaves other subscribers running. */
export function readCustomerInbox(signal: AbortSignal): Promise<CustomerInbox> {
    signal.throwIfAborted();
    if (!pending) {
        const controller = new AbortController();
        const read: Read = {controller, consumers: 0, promise: Promise.resolve({unreadCount: 0, messages: []})};
        read.promise = apiClient<CustomerInbox>("/api/customer/identity/notifications", {
            credentials: "include", cache: "no-store",
            signal: AbortSignal.any([controller.signal, AbortSignal.timeout(8000)])
        }).then(value => {controller.signal.throwIfAborted(); return value;})
            .finally(() => {if (pending === read) pending = null;});
        pending = read;
    }
    const read = pending;
    read.consumers++;
    return new Promise((resolve, reject) => {
        let finished = false;
        const release = () => {
            if (finished) return false;
            finished = true;
            signal.removeEventListener("abort", abort);
            if (--read.consumers === 0 && pending === read) invalidate();
            return true;
        };
        const abort = () => {if (release()) reject(signal.reason);};
        signal.addEventListener("abort", abort, {once: true});
        read.promise.then(value => {if (release()) resolve(value);}, error => {if (release()) reject(error);});
    });
}
