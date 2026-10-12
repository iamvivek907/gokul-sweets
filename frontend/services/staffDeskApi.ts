import { adminFetch } from "@/services/adminApi";
/** Time bounds include response-body reads. Every read can be cancelled on navigation. */
export async function staffDeskRequest<T>(path: string, authorization: string, init: RequestInit = {}, signal?: AbortSignal): Promise<T> {
    const controller = new AbortController();
    const abort = () => controller.abort();
    signal?.addEventListener("abort", abort, { once: true });
    if (signal?.aborted)
        controller.abort();
    const timeout = window.setTimeout(abort, 15000);
    try {
        const response = await adminFetch(path, authorization, { ...init, signal: controller.signal });
        if (!response.ok) {
            let message = "Could not update the order desk. Retry after checking your connection.";
            try {
                const body = await response.json();
                if (typeof body.message === "string")
                    message = body.message;
            }
            catch { /* Non-JSON proxy error. */ }
            throw new Error(message);
        }
        if (response.status === 204)
            return undefined as T;
        return await response.json() as T;
    }
    catch (error) {
        if (controller.signal.aborted && !signal?.aborted)
            throw new Error("The service is taking too long. Check the current order status before retrying an action.");
        throw error;
    }
    finally {
        clearTimeout(timeout);
        signal?.removeEventListener("abort", abort);
    }
}
export type DeskItem = {
    productId: number;
    productName: string;
    saleMode: string;
    quantity: number;
    weightGrams: number | null;
};
export type DeskRow = {
    orderNumber: string;
    customerOrderNumber?: number | null;
    customerName: string;
    fulfillmentType: string;
    orderStatus: string;
    bucket: string;
    date: string;
    start: string;
    end: string;
    preparationAt: string;
    earlyPreparation: boolean;
    items: DeskItem[];
};
export type DeskPlan = {
    orders: DeskRow[];
    slots: {
        date: string;
        start: string;
        end: string;
        fulfillmentType: string;
        total: number;
    }[];
    counts: Record<string, number>;
    page: number;
    total: number;
    generatedAt: string;
};
export type DemandRow = {
    date: string;
    productId: number;
    productName: string;
    saleMode: string;
    ordered: number;
    waiting: number;
    preparing: number;
    ready: number;
    completed: number;
    orderCount: number;
};
export type DeskPolicy = {
    productId: number;
    productName: string;
    earlyPreparationAllowed: boolean;
};
