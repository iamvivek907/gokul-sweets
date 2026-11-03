"use client";

import {T} from "@/lib/language";
import styles from "./SocialFollowPopup.module.css";

/** An optional inline invitation after confirmed payment; never covers ordering controls. */
export default function SocialFollowPopup({eligible = false}: {eligible?: boolean}) {
    if (!eligible) return null;
    return <aside className={`customer-social-thanks ${styles.card}`} aria-label="Stay connected">
        <div className={styles.intro}>
            <span className={styles.mark} aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M12 3v18M3 12h18M6 6l12 12M18 6 6 18" strokeLinecap="round"/></svg>
            </span>
            <div><strong><T text="Thank you for choosing Gokul"/></strong><p><T text="Follow us for fresh arrivals and festive favourites."/></p></div>
        </div>
        <div className={styles.links}>
            {[
                ["Instagram", "https://www.instagram.com/_gokulsweets"],
                ["Facebook", "https://www.facebook.com/visitgokulsweets"],
            ].map(([label, href]) => <a key={label} href={href} target="_blank" rel="noopener noreferrer">
                {label}<span className={styles.legacyArrow} aria-hidden="true"> ↗</span><svg className={styles.linkIcon} aria-hidden="true" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M4 12 12 4M5 4h7v7" strokeLinecap="round" strokeLinejoin="round"/></svg>
            </a>)}
        </div>
    </aside>;
}
