import type {
    CustomerDetails
} from "@/types/customer";

import type {
    PickupSelection,
    PickupSlot,
    PickupType
} from "@/types/pickup";


const PICKUP_SLOT_STORAGE_KEY =
    "gokul-selected-pickup-slot";

const PICKUP_SLOT_CHANGE_EVENT =
    "gokul-pickup-slot-change";

const CUSTOMER_STORAGE_KEY =
    "gokul-customer-details";

const CUSTOMER_CHANGE_EVENT =
    "gokul-customer-change";


/*
 * =========================================================
 * PICKUP SELECTION
 * =========================================================
 */

export function getPickupSlotSnapshot():
    string {

    return (
        localStorage.getItem(
            PICKUP_SLOT_STORAGE_KEY
        )
        ?? ""
    );
}


export function getServerPickupSlotSnapshot():
    string {

    return "";
}


export function subscribeToPickupSlot(
    callback: () => void
): () => void {

    window.addEventListener(
        "storage",
        callback
    );

    window.addEventListener(
        PICKUP_SLOT_CHANGE_EVENT,
        callback
    );


    return () => {

        window.removeEventListener(
            "storage",
            callback
        );

        window.removeEventListener(
            PICKUP_SLOT_CHANGE_EVENT,
            callback
        );
    };
}


function determinePickupType(
    slot: PickupSlot
): PickupType | null {

    if (!slot.active) {
        return null;
    }


    /*
     * Normal capacity ALWAYS gets used first.
     */
    if (
        slot.remainingCapacity > 0
    ) {

        return "NORMAL";
    }


    /*
     * Priority only becomes available when
     * normal capacity is completely exhausted.
     */
    if (
        slot.priorityEnabled
        &&
        slot.priorityRemainingCapacity > 0
    ) {

        return "PRIORITY";
    }


    return null;
}


export function parsePickupSlot(
    value: string
): PickupSelection | null {

    if (!value) {
        return null;
    }


    try {

        const parsed =
            JSON.parse(
                value
            ) as Partial<PickupSelection>;


        if (
            !parsed.date
            ||
            !parsed.slot
        ) {

            return null;
        }


        /*
         * New stored format.
         */
        if (
            parsed.pickupType === "NORMAL"
            ||
            parsed.pickupType === "PRIORITY"
        ) {

            return {
                date:
                    parsed.date,

                slot:
                    parsed.slot,

                pickupType:
                    parsed.pickupType
            };
        }


        /*
         * Backward compatibility:
         *
         * Older localStorage entries did not
         * contain pickupType.
         */
        const pickupType =
            determinePickupType(
                parsed.slot
            );


        if (!pickupType) {
            return null;
        }


        return {
            date:
                parsed.date,

            slot:
                parsed.slot,

            pickupType
        };

    } catch {

        return null;
    }
}


export function savePickupSlot(
    selection: PickupSelection
): void {

    localStorage.setItem(
        PICKUP_SLOT_STORAGE_KEY,
        JSON.stringify(
            selection
        )
    );


    window.dispatchEvent(
        new Event(
            PICKUP_SLOT_CHANGE_EVENT
        )
    );
}


export function clearPickupSlot():
    void {

    localStorage.removeItem(
        PICKUP_SLOT_STORAGE_KEY
    );


    window.dispatchEvent(
        new Event(
            PICKUP_SLOT_CHANGE_EVENT
        )
    );
}


/*
 * =========================================================
 * CUSTOMER DETAILS
 * =========================================================
 */

export function getCustomerSnapshot():
    string {

    return (
        localStorage.getItem(
            CUSTOMER_STORAGE_KEY
        )
        ?? ""
    );
}


export function getServerCustomerSnapshot():
    string {

    return "";
}


export function subscribeToCustomer(
    callback: () => void
): () => void {

    window.addEventListener(
        "storage",
        callback
    );

    window.addEventListener(
        CUSTOMER_CHANGE_EVENT,
        callback
    );


    return () => {

        window.removeEventListener(
            "storage",
            callback
        );

        window.removeEventListener(
            CUSTOMER_CHANGE_EVENT,
            callback
        );
    };
}


export function parseCustomerDetails(
    value: string
): CustomerDetails | null {

    if (!value) {
        return null;
    }


    try {

        return JSON.parse(
            value
        ) as CustomerDetails;

    } catch {

        return null;
    }
}


export function saveCustomerDetails(
    customer: CustomerDetails
): void {

    localStorage.setItem(
        CUSTOMER_STORAGE_KEY,
        JSON.stringify(
            customer
        )
    );


    window.dispatchEvent(
        new Event(
            CUSTOMER_CHANGE_EVENT
        )
    );
}


export function clearCustomerDetails():
    void {

    localStorage.removeItem(
        CUSTOMER_STORAGE_KEY
    );


    window.dispatchEvent(
        new Event(
            CUSTOMER_CHANGE_EVENT
        )
    );
}