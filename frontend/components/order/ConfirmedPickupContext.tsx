"use client";

import {useEffect, useState} from "react";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {formatBusinessTime} from "@/lib/businessTime";
import {pickupDateLabel} from "@/components/layout/PickupJourneyContext";
import {getCustomerOrder} from "@/services/orderApi";
import type {CustomerOrderResponse} from "@/types/order";

/** A paid or pending order is always labelled using the server's branch and slot. */
export default function ConfirmedPickupContext({orderNumber, order: providedOrder}: {
    orderNumber: string; order?: CustomerOrderResponse | null;
}) {
    const features = useStorefrontFeatures();
    const [loaded, setLoaded] = useState<{number: string; order: CustomerOrderResponse} | null>(null);
    const [error, setError] = useState(false);
    useEffect(() => {
        if (!features?.persistentPickupContext || providedOrder) return;
        const controller = new AbortController();
        getCustomerOrder(orderNumber, controller.signal)
            .then(order => {if (!controller.signal.aborted) {setLoaded({number: orderNumber, order}); setError(false);}})
            .catch(() => {if (!controller.signal.aborted) setError(true);});
        return () => controller.abort();
    }, [features?.persistentPickupContext, providedOrder, orderNumber]);

    if (!features?.persistentPickupContext) return null;
    const order = providedOrder ?? (loaded?.number === orderNumber ? loaded.order : null);
    return <aside aria-label="Order pickup plan" className="mb-5 rounded-2xl border border-[#eadfd6] bg-white p-4 text-sm">
        <p className="text-xs font-semibold uppercase tracking-wide text-[#756763]">Pickup for this order</p>
        {order ? <><p className="mt-1 font-bold">{order.branchName}</p>
            <p className="mt-1">{pickupDateLabel(order.pickupDate)} · {formatBusinessTime(order.pickupStartTime)}–{formatBusinessTime(order.pickupEndTime)} IST</p>
            <p className="mt-1 text-xs text-[#756763]">{order.pickupType === "PRIORITY" ? "Priority" : "Normal"} pickup · Confirm changes with the shop</p></>
            : <p role="status" className="mt-1 text-[#756763]">{error ? "Pickup details unavailable. Check the order page before collecting." : "Checking confirmed pickup details..."}</p>}
    </aside>;
}
