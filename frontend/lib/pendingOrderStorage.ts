import type {
    PendingOrderSession
} from "@/types/pendingOrder";


const PENDING_ORDER_STORAGE_KEY =
    "gokul-pending-order";

const PENDING_ORDER_CHANGE_EVENT =
    "gokul-pending-order-change";


export function getPendingOrderSnapshot():
    string {

    if (
        typeof window === "undefined"
    ) {

        return "";
    }


    return (
        window.localStorage.getItem(
            PENDING_ORDER_STORAGE_KEY
        )
        ?? ""
    );
}


export function getServerPendingOrderSnapshot():
    string {

    return "";
}


export function subscribeToPendingOrder(
    callback: () => void
): () => void {

    if (
        typeof window === "undefined"
    ) {

        return () => {};
    }


    const handleStorage =
        (
            event: StorageEvent
        ) => {

            if (
                event.key ===
                PENDING_ORDER_STORAGE_KEY
            ) {

                callback();
            }
        };


    const handleLocalChange =
        () => {

            callback();
        };


    window.addEventListener(
        "storage",
        handleStorage
    );


    window.addEventListener(
        PENDING_ORDER_CHANGE_EVENT,
        handleLocalChange
    );


    return () => {

        window.removeEventListener(
            "storage",
            handleStorage
        );


        window.removeEventListener(
            PENDING_ORDER_CHANGE_EVENT,
            handleLocalChange
        );
    };
}


export function parsePendingOrder(
    value: string
): PendingOrderSession | null {

    if (!value) {
        return null;
    }


    try {

        const parsed =
            JSON.parse(
                value
            ) as Partial<PendingOrderSession>;


        if (
            typeof parsed.orderId !== "number"
            ||
            typeof parsed.orderNumber !== "string"
            ||
            typeof parsed.orderStatus !== "string"
            ||
            typeof parsed.branchId !== "number"
            ||
            typeof parsed.pickupSlotId !== "number"
            ||
            typeof parsed.totalAmount !== "number"
            ||
            typeof parsed.reservationExpiresAt !== "string"
            ||
            typeof parsed.createdAt !== "string"
            ||
            typeof parsed.cartFingerprint !== "string"
        ) {

            return null;
        }


        return {
            orderId:
                parsed.orderId,

            orderNumber:
                parsed.orderNumber,

            orderStatus:
                parsed.orderStatus,

            branchId:
                parsed.branchId,

            pickupSlotId:
                parsed.pickupSlotId,

            totalAmount:
                parsed.totalAmount,

            reservationExpiresAt:
                parsed.reservationExpiresAt,

            createdAt:
                parsed.createdAt,

            cartFingerprint:
                parsed.cartFingerprint
        };

    } catch {

        return null;
    }
}


export function savePendingOrder(
    order: PendingOrderSession
): void {

    if (
        typeof window === "undefined"
    ) {

        return;
    }


    window.localStorage.setItem(
        PENDING_ORDER_STORAGE_KEY,
        JSON.stringify(
            order
        )
    );


    window.dispatchEvent(
        new Event(
            PENDING_ORDER_CHANGE_EVENT
        )
    );
}


export function clearPendingOrder():
    void {

    if (
        typeof window === "undefined"
    ) {

        return;
    }


    window.localStorage.removeItem(
        PENDING_ORDER_STORAGE_KEY
    );


    window.dispatchEvent(
        new Event(
            PENDING_ORDER_CHANGE_EVENT
        )
    );
}
