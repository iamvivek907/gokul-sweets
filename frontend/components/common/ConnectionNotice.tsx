"use client";
import {T} from "@/lib/language";


import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";

/** A background outage must never replace the mounted cart, form or payment page. */
export default function ConnectionNotice() {
    const {features, error, retry} = useStorefrontConfiguration();
    if (!features || !error) return null;
    return <aside role="status" className="mx-4 my-3 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#dbcdbd] bg-[#fffaf2] p-4 text-[#172e2c]">
        <div><strong><T text="Online ordering is taking longer to connect." /></strong>
            <p className="mt-1 text-sm">Your cart is saved. We’ll reconnect automatically; please check availability before ordering.</p></div>
        <button type="button" onClick={retry} className="min-h-11 rounded-lg border border-[#143936] px-4 font-semibold"><T text="Try again" /></button>
    </aside>;
}
