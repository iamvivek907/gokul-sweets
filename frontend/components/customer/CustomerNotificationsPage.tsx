"use client";

import MobilePageBack from "./MobilePageBack";
import {useState} from "react";
import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";
import CustomerIdentityPanel, {type CustomerSession} from "./CustomerIdentityPanel";
import CustomerNotificationInbox from "./CustomerNotificationInbox";
import {useCustomerLocation} from "./CustomerNotificationLink";
import {T} from "@/lib/language";

export default function CustomerNotificationsPage() {
    const {features, error, retry} = useStorefrontConfiguration();
    const [session, setSession] = useState<CustomerSession | null>(null);
    const location = useCustomerLocation();
    const from = new URL(location, "https://storefront.invalid").searchParams.get("from");
    let origin = "/profile";
    if (from?.startsWith("/") && !from.startsWith("//") && !/[\\\u0000-\u0020]/.test(from)) {
        const destination = new URL(from, "https://storefront.invalid");
        if (destination.origin === "https://storefront.invalid" && destination.pathname !== "/notifications") {
            origin = destination.pathname + destination.search + (destination.hash === "#account-notifications" ? "" : destination.hash);
        }
    }
    return <div className="customer-notifications mx-auto max-w-3xl px-4 pb-24 pt-4 sm:py-8">
        <MobilePageBack href={origin} label="Back to previous page" className="notification-back mb-4 inline-flex min-h-11 items-center gap-2 rounded-full border border-[#eadfd6] bg-white px-4 text-sm font-semibold text-[#7a1625] shadow-sm"/>
        <h1 className="mb-4 text-2xl font-bold"><T text="Notifications" /></h1>
        {!features ? <p role="status">{error ?? "Loading notifications…"}{error && <button type="button" onClick={retry} className="ml-3 underline"><T text="Try again" /></button>}</p> : !features.notificationInbox ?
            <p><T text="Notifications are not available yet. Check Order history for current updates." /></p> : <>
                <div className={session?.authenticated ? "hidden" : ""}><CustomerIdentityPanel onSessionChange={setSession} /></div>
                {session?.authenticated && <CustomerNotificationInbox key={session.phone} />}
            </>}
    </div>;
}
