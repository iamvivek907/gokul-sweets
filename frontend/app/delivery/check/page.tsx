"use client";

import Link from "next/link";
import {useState, type FormEvent} from "react";
import AppShell from "@/components/layout/AppShell";
import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";
import styles from "./page.module.css";

type LocationState = "idle" | "locating" | "received" | "unavailable";

export default function DeliveryCheckPage() {
    const {features, error, retry} = useStorefrontConfiguration();
    const [locality, setLocality] = useState("");
    const [pin, setPin] = useState("");
    const [message, setMessage] = useState("");
    const [locationState, setLocationState] = useState<LocationState>("idle");

    function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const area = locality.trim();
        if (area.length < 2 || area.length > 120) {
            setMessage("Enter a locality between 2 and 120 characters.");
            return;
        }
        // SCRUM-30 will send this input to its authoritative eligibility API.
        // Until then, the browser does not save, transmit or classify the area.
        setMessage(`${area} is ready to check. Delivery availability is not confirmed yet.`);
    }

    function useLocation() {
        if (!window.isSecureContext || !navigator.geolocation) {
            setLocationState("unavailable");
            return;
        }
        setLocationState("locating");
        navigator.geolocation.getCurrentPosition(
            () => {
                // The coordinates are intentionally discarded: there is no approved
                // delivery-zone endpoint yet, and no purpose for retaining them.
                setLocationState("received");
            },
            () => setLocationState("unavailable"),
            {enableHighAccuracy: false, timeout: 8000, maximumAge: 0}
        );
    }

    return <AppShell showSocialPopup={false}>
        <main className={styles.page}>
            <div className={styles.frame}>
                <Link className={styles.back} href="/menu">&larr; Back to menu</Link>
                <p className={styles.eyebrow}>Gokul Sweets / Delivery</p>
                <h1>Your favourites, <em>closer to you.</em></h1>
                <p className={styles.intro}>Tell us your area to prepare a delivery check. Pickup is still available at your selected branch.</p>
                {!features ? <div role={error ? "alert" : "status"} className={styles.card}>
                    <p>{error ?? "Checking storefront settings..."}</p>
                    {error && <button type="button" onClick={retry}>Try again</button>}
                </div> : !features.deliveryLocalityCheck ? <div className={styles.card}>
                    <h2>Delivery checks are coming soon</h2>
                    <p>You can browse the menu and order for pickup today.</p>
                    <Link href="/menu">Explore the menu &rarr;</Link>
                </div> : <div className={styles.grid}>
                    <section className={styles.card} aria-labelledby="manual-location">
                        <span className={styles.step}>01 / YOUR AREA</span>
                        <h2 id="manual-location">Enter your locality</h2>
                        <p>Location access is optional. You can enter your area even if you decline it.</p>
                        <form onSubmit={submit}>
                            <label htmlFor="delivery-locality">Locality or neighbourhood</label>
                            <input id="delivery-locality" name="locality" autoComplete="address-level3" value={locality}
                                   onChange={event => {setLocality(event.target.value); setMessage("");}}
                                   minLength={2} maxLength={120} required placeholder="e.g. Hazratganj" />
                            <label htmlFor="delivery-pin">Map pin or landmark (optional)</label>
                            <input id="delivery-pin" name="pin" value={pin} maxLength={160}
                                   onChange={event => setPin(event.target.value)} placeholder="Paste a map link or enter a landmark" />
                            <p className={styles.hint}>The pin stays on this page for now. No address or pin is submitted.</p>
                            <button type="submit" className={styles.action}>Prepare area check <span aria-hidden="true">&rarr;</span></button>
                        </form>
                        {message && <p role="status" className={styles.result}>{message}</p>}
                    </section>
                    <aside className={styles.card} aria-labelledby="device-location">
                        <span className={styles.step}>02 / OPTIONAL</span>
                        <h2 id="device-location">Use my location</h2>
                        <p>Your browser asks only when you choose this option. This preview does not store or send your coordinates.</p>
                        <button type="button" className={styles.outline} disabled={locationState === "locating"} onClick={useLocation}>
                            {locationState === "locating" ? "Finding location..." : "Use my location"}
                        </button>
                        {locationState === "received" && <p role="status" className={styles.result}>Location received for this one-time check. Enter your locality above while delivery zones are being prepared.</p>}
                        {locationState === "unavailable" && <p role="status" className={styles.result}>Location is unavailable or permission was declined. Enter your locality above instead.</p>}
                        <div className={styles.notice}><strong>Delivery is not confirmed yet.</strong><p>We&apos;ll confirm supported areas and a fulfilment branch when delivery opens. Pickup ordering is unaffected.</p></div>
                    </aside>
                </div>}
            </div>
        </main>
    </AppShell>;
}
