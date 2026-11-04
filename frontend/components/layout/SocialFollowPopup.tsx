"use client";

import SocialFollowLinks from "@/components/customer/SocialFollowLinks";
import {T} from "@/lib/language";
import styles from "./SocialFollowPopup.module.css";

export default function SocialFollowPopup({eligible = false, reference = false}: {
    eligible?: boolean;
    reference?: boolean;
}) {
    if (!eligible) return null;
    if (reference) {
        return <div className={styles.postOrder}><SocialFollowLinks /></div>;
    }

    return <aside className="customer-social-thanks" aria-label="Stay connected">
        <div>
            <strong><T text="Thank you for choosing Gokul"/></strong>
            <p><T text="Follow us for fresh arrivals and festive favourites."/></p>
        </div>
        <div>
            <a href="https://www.instagram.com/_gokulsweets" target="_blank" rel="noopener noreferrer">Instagram ↗</a>
            <a href="https://www.facebook.com/visitgokulsweets" target="_blank" rel="noopener noreferrer">Facebook ↗</a>
        </div>
    </aside>;
}
