import type {
    OrderHistoryEntry
} from "@/types/orderHistory";


const STORAGE_KEY =
    "gokul-order-history";


const CHANGE_EVENT =
    "gokul-order-history-change";


function isBrowser(): boolean {

    return typeof window !==
        "undefined";
}


function parseEntries(
    raw: string | null
): OrderHistoryEntry[] {

    if (!raw) {

        return [];
    }


    try {

        const parsed: unknown =
            JSON.parse(
                raw
            );


        if (!Array.isArray(parsed)) {

            return [];
        }


        return parsed
            .filter(
                (
                    value
                ): value is OrderHistoryEntry => {

                    if (
                        typeof value !==
                        "object"
                        ||
                        value === null
                    ) {

                        return false;
                    }


                    const entry =
                        value as Record<
                            string,
                            unknown
                        >;


                    return (
                        typeof entry.orderNumber ===
                            "string"
                        &&
                        entry.orderNumber.length >
                            0
                        &&
                        typeof entry.createdAt ===
                            "string"
                    );
                }
            )
            .sort(
                (
                    first,
                    second
                ) =>
                    new Date(
                        second.createdAt
                    ).getTime()
                    -
                    new Date(
                        first.createdAt
                    ).getTime()
            );

    } catch {

        return [];
    }
}


export function getOrderHistorySnapshot():
    string {

    if (!isBrowser()) {

        return "[]";
    }


    return window.localStorage
        .getItem(
            STORAGE_KEY
        )
        ?? "[]";
}


export function getServerOrderHistorySnapshot():
    string {

    return "[]";
}


export function subscribeToOrderHistory(
    callback:
        () => void
): () => void {

    if (!isBrowser()) {

        return () => {
            // no-op on server
        };
    }


    function handleStorage(
        event: StorageEvent
    ): void {

        if (
            event.key ===
            STORAGE_KEY
        ) {

            callback();
        }
    }


    function handleCustomEvent():
        void {

        callback();
    }


    window.addEventListener(
        "storage",
        handleStorage
    );


    window.addEventListener(
        CHANGE_EVENT,
        handleCustomEvent
    );


    return () => {

        window.removeEventListener(
            "storage",
            handleStorage
        );


        window.removeEventListener(
            CHANGE_EVENT,
            handleCustomEvent
        );
    };
}


export function parseOrderHistory(
    snapshot: string
): OrderHistoryEntry[] {

    return parseEntries(
        snapshot
    );
}


export function addOrderToHistory(
    entry: OrderHistoryEntry
): void {

    if (!isBrowser()) {

        return;
    }


    const existing =
        parseEntries(
            window.localStorage
                .getItem(
                    STORAGE_KEY
                )
        );


    const withoutDuplicate =
        existing.filter(
            existingEntry =>
                existingEntry.orderNumber !==
                entry.orderNumber
        );


    const updated =
        [
            entry,
            ...withoutDuplicate
        ]
            .sort(
                (
                    first,
                    second
                ) =>
                    new Date(
                        second.createdAt
                    ).getTime()
                    -
                    new Date(
                        first.createdAt
                    ).getTime()
            );


    window.localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify(
            updated
        )
    );


    window.dispatchEvent(
        new Event(
            CHANGE_EVENT
        )
    );
}


export function removeOrderFromHistory(
    orderNumber: string
): void {

    if (!isBrowser()) {

        return;
    }


    const existing =
        parseEntries(
            window.localStorage
                .getItem(
                    STORAGE_KEY
                )
        );


    const updated =
        existing.filter(
            entry =>
                entry.orderNumber !==
                orderNumber
        );


    window.localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify(
            updated
        )
    );


    window.dispatchEvent(
        new Event(
            CHANGE_EVENT
        )
    );
}