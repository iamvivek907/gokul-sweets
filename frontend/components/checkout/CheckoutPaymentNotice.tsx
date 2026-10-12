"use client";
import {useEffect, useState} from "react";
import {T} from "@/lib/language";
export default function CheckoutPaymentNotice() {
    const [visible, setVisible] = useState(false);
    useEffect(() => {
        const notice = new URLSearchParams(location.search).get("paymentRecovery");
        if (notice === "failed" || notice === "expired") {
            const timer = setTimeout(() => setVisible(true), 0);
            return () => clearTimeout(timer);
        }
    }, []);
    return visible ? <div className="mobile-checkout-payment-notice" role="status">
        <p><strong><T text="Ready to try again" /></strong><br /><T text="The previous payment didn’t complete. Your items are saved; review the current total before paying again." /></p>
        <button type="button" onClick={() => setVisible(false)} aria-label="Dismiss payment notice">×</button>
    </div> : null;
}
