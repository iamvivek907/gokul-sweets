"use client";

import Link from "next/link";
import {useState, type FormEvent} from "react";
import AppShell from "@/components/layout/AppShell";
import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";
import {useCart} from "@/hooks/useCart";
import {apiClient} from "@/services/apiClient";
import styles from "./page.module.css";

type LocationState = "idle" | "locating" | "received" | "unavailable";
type Coverage = {configuredAreas: {branchId: number; branchName: string}[]; orderable: boolean; notice: string};
type Quote = {provisionalWindows: {id: number; serviceDate: string; startsAt: string; endsAt: string}[];
    orderable: boolean; notice: string};

export default function DeliveryCheckPage() {
    const {features, error, retry} = useStorefrontConfiguration();
    const [locality, setLocality] = useState("");
    const [pin, setPin] = useState("");
    const [postalCode, setPostalCode] = useState("");
    const [serviceDate, setServiceDate] = useState("");
    const [pending, setPending] = useState(false);
    const [quote, setQuote] = useState<Quote | null>(null);
    const [message, setMessage] = useState("");
    const [locationState, setLocationState] = useState<LocationState>("idle");
    const cart = useCart();

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const area = locality.trim();
        if (area.length < 2 || area.length > 120) {
            setMessage("Enter a locality between 2 and 120 characters.");
            return;
        }
        if (!features?.deliveryZones) {
            setMessage(`${area} is ready to check. Delivery availability is not confirmed yet.`);
            return;
        }
        setPending(true); setMessage(""); setQuote(null);
        try {
            if (features.deliveryCapacity && cart.branchId && cart.items.length && serviceDate) {
                const result = await apiClient<Quote>("/api/storefront/delivery/quote", {
                    method: "POST", body: JSON.stringify({branchId: cart.branchId, locality: area,
                        postalCode, serviceDate, items: cart.items.map(item => ({productId: item.product.id,
                            quantity: item.product.saleMode === "WEIGHT" ? null : item.quantity,
                            weightGrams: item.product.saleMode === "WEIGHT" ? item.weightGrams : null}))}),
                    signal: AbortSignal.timeout(8000)
                });
                setQuote(result);
                setMessage(result.notice);
            } else {
                const result = await apiClient<Coverage>(`/api/storefront/delivery/coverage?locality=${encodeURIComponent(area)}&postalCode=${encodeURIComponent(postalCode)}`,
                    {signal: AbortSignal.timeout(8000)});
                setMessage(result.configuredAreas.length
                    ? "This area has a configured branch. Add items and choose an IST date to preview rider windows. Delivery is not confirmed."
                    : result.notice);
            }
        } catch {setMessage("We could not check your area right now. Pickup is still available; try again later.");}
        finally {setPending(false);}
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
                                   onChange={event => {setLocality(event.target.value); setMessage(""); setQuote(null);}}
                                   minLength={2} maxLength={120} required placeholder="e.g. Hazratganj" />
                            {features.deliveryZones && <><label htmlFor="delivery-postal-code">Six-digit PIN code</label>
                                <input id="delivery-postal-code" name="postalCode" inputMode="numeric" autoComplete="postal-code"
                                       value={postalCode} onChange={event => {setPostalCode(event.target.value); setQuote(null);}}
                                       pattern="[0-9]{6}" required placeholder="e.g. 226001" /></>}
                            {features.deliveryCapacity && cart.items.length > 0 && <><label htmlFor="delivery-service-date">Delivery date (IST)</label>
                                <input id="delivery-service-date" name="serviceDate" type="date" value={serviceDate}
                                       onChange={event => {setServiceDate(event.target.value); setQuote(null);}} />
                                <p className={styles.hint}>Checking {cart.items.length} item(s) from your current branch. A result does not reserve a rider.</p></>}
                            <label htmlFor="delivery-pin">Map pin or landmark (optional)</label>
                            <input id="delivery-pin" name="pin" value={pin} maxLength={160}
                                   onChange={event => setPin(event.target.value)} placeholder="Paste a map link or enter a landmark" />
                            <p className={styles.hint}>The optional map pin or landmark stays on this page. When available, locality and PIN go to the server for a provisional check.</p>
                            <button type="submit" disabled={pending} className={styles.action}>{pending ? "Checking area..." : "Check area"} <span aria-hidden="true">&rarr;</span></button>
                        </form>
                        {message && <p role="status" className={styles.result}>{message}</p>}
                        {quote && quote.provisionalWindows.length > 0 && <div className={styles.result}>
                            <strong>Configured rider windows (IST)</strong>
                            <ul>{quote.provisionalWindows.map(window => <li key={window.id}>
                                {window.serviceDate} · {window.startsAt.slice(0, 5)}–{window.endsAt.slice(0, 5)}
                            </li>)}</ul>
                            <p>These times are provisional. Delivery checkout is not open yet.</p>
                        </div>}
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
