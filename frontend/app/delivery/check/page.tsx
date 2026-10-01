"use client";
import {T} from "@/lib/language";


import Link from "next/link";
import {useRef, useState, type FormEvent} from "react";
import {useRouter} from "next/navigation";
import AppShell from "@/components/layout/AppShell";
import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";
import {useCart} from "@/hooks/useCart";
import {apiClient} from "@/services/apiClient";
import {createCartFingerprint} from "@/lib/cartFingerprint";
import {getCartSnapshot, parseCart} from "@/lib/cartStorage";
import {addOrderToHistory} from "@/lib/orderHistoryStorage";
import {savePendingOrder} from "@/lib/pendingOrderStorage";
import {businessDateOffset, parseBusinessTimestamp} from "@/lib/businessTime";
import {createDeliveryOrder, previewDeliveryPrice, type DeliveryAcceptedQuote,
    type DeliveryCartCheck, type DeliveryOrderDraft} from "@/services/deliveryCheckoutApi";
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
    const [devicePoint, setDevicePoint] = useState<{latitude: number; longitude: number} | null>(null);
    const [checked, setChecked] = useState<{request: DeliveryCartCheck; fingerprint: string} | null>(null);
    const [selectedWindowId, setSelectedWindowId] = useState<number | null>(null);
    const [addressLine, setAddressLine] = useState("");
    const [customerName, setCustomerName] = useState("");
    const [customerPhone, setCustomerPhone] = useState("");
    const [accepted, setAccepted] = useState<{quote: DeliveryAcceptedQuote; draft: DeliveryOrderDraft; fingerprint: string} | null>(null);
    const [checkoutPending, setCheckoutPending] = useState(false);
    const [checkoutMessage, setCheckoutMessage] = useState("");
    const idempotencyKey = useRef<string | null>(null);
    const revision = useRef(0);
    const router = useRouter();
    const cart = useCart();

    function invalidateArea() {
        revision.current++;
        setQuote(null); setChecked(null); setSelectedWindowId(null); setAccepted(null);
        setCheckoutMessage(""); idempotencyKey.current = null;
    }

    function invalidatePrice() {
        revision.current++;
        setAccepted(null); setCheckoutMessage(""); idempotencyKey.current = null;
    }

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
        let point = devicePoint;
        if (features.deliveryAddressBoundaries) {
            if (pin.trim()) {
                const parts = pin.trim().split(",").map(value => Number(value.trim()));
                if (parts.length !== 2 || pin.trim().split(",").some(value => !value.trim())
                        || parts.some(value => !Number.isFinite(value))) {
                    setMessage("Enter a map pin as latitude, longitude, or use your device location."); return;
                }
                point = {latitude: parts[0], longitude: parts[1]};
            }
            if (!point || Math.abs(point.latitude) > 90 || Math.abs(point.longitude) > 180) {
                setMessage("A valid map pin is needed to check a reviewed delivery boundary. Pickup remains available."); return;
            }
        }
        setPending(true); setMessage(""); invalidateArea();
        const requestRevision = revision.current;
        try {
            if (features.deliveryCapacity && cart.branchId && cart.items.length && serviceDate) {
                const request = {branchId: cart.branchId, locality: area,
                        postalCode, serviceDate, latitude: features.deliveryAddressBoundaries ? point?.latitude : null,
                        longitude: features.deliveryAddressBoundaries ? point?.longitude : null,
                        items: cart.items.map(item => ({productId: item.product.id,
                            quantity: item.product.saleMode === "WEIGHT" ? null : item.quantity,
                            weightGrams: item.product.saleMode === "WEIGHT" ? item.weightGrams : null}))};
                const result = await apiClient<Quote>("/api/storefront/delivery/quote", {
                    method: "POST", body: JSON.stringify(request),
                    signal: AbortSignal.timeout(8000)
                });
                if (revision.current !== requestRevision) return;
                setQuote(result);
                if (features.deliveryCheckout && point && result.provisionalWindows.length > 0) {
                    setChecked({request: {...request, latitude: point.latitude, longitude: point.longitude},
                        fingerprint: createCartFingerprint(cart.items)});
                }
                setMessage(result.notice);
            } else {
                const result = await apiClient<Coverage>(`/api/storefront/delivery/coverage?locality=${encodeURIComponent(area)}&postalCode=${encodeURIComponent(postalCode)}`,
                    {signal: AbortSignal.timeout(8000)});
                if (revision.current !== requestRevision) return;
                setMessage(result.configuredAreas.length
                    ? "This area has a configured branch. Add items and choose an IST date to preview rider windows. Delivery is not confirmed."
                    : result.notice);
            }
        } catch {if (revision.current === requestRevision)
            setMessage("We could not check your area right now. Pickup is still available; try again later.");}
        finally {setPending(false); setDevicePoint(null);}
    }

    async function reviewPrice(event: FormEvent<HTMLFormElement>) {
        event.preventDefault(); setCheckoutMessage(""); setAccepted(null);
        if (!features?.deliveryCheckout || !checked || !selectedWindowId ||
            checked.request.branchId !== cart.branchId || checked.fingerprint !== createCartFingerprint(cart.items)) {
            setCheckoutMessage("Your cart or delivery check changed. Check your area again."); return;
        }
        const phone = customerPhone.replace(/\D/g, "");
        if (!/^[6-9][0-9]{9}$/.test(phone) || customerName.trim().length < 2 ||
            addressLine.trim().length < 2 || addressLine.trim().length > 300) {
            setCheckoutMessage("Enter your full address, name and a valid 10-digit Indian mobile number."); return;
        }
        const draft: DeliveryOrderDraft = {quote: checked.request, windowId: selectedWindowId,
            addressLine: addressLine.trim(), customerName: customerName.trim(), customerPhone: phone};
        const requestRevision = revision.current;
        setCheckoutPending(true);
        try {
            const price = await previewDeliveryPrice(draft);
            if (revision.current !== requestRevision ||
                checked.fingerprint !== createCartFingerprint(parseCart(getCartSnapshot()).items)) return;
            setAccepted({quote: price, draft, fingerprint: checked.fingerprint});
            idempotencyKey.current = null;
        } catch (error) {
            if (revision.current === requestRevision)
                setCheckoutMessage(error instanceof Error ? error.message : "Could not prepare a delivery price. Try again.");
        } finally {setCheckoutPending(false);}
    }

    async function placeOrder() {
        if (!features?.deliveryCheckout || !accepted || checkoutPending) return;
        if (accepted.draft.quote.branchId !== cart.branchId ||
            accepted.fingerprint !== createCartFingerprint(parseCart(getCartSnapshot()).items) ||
            (parseBusinessTimestamp(accepted.quote.expiresAt).getTime() <= Date.now() && !idempotencyKey.current)) {
            setAccepted(null); setCheckoutMessage("Your cart or price quote changed. Review the delivery price again."); return;
        }
        setCheckoutPending(true); setCheckoutMessage("");
        if (!idempotencyKey.current) idempotencyKey.current = crypto.randomUUID();
        try {
            const order = await createDeliveryOrder({...accepted.draft,
                acceptedQuoteToken: accepted.quote.token}, idempotencyKey.current);
            addOrderToHistory({orderNumber: order.orderNumber, createdAt: order.createdAt});
            savePendingOrder({orderId: order.id, orderNumber: order.orderNumber, orderStatus: order.orderStatus,
                branchId: order.branchId, pickupSlotId: null, fulfillmentType: "DELIVERY",
                totalAmount: order.totalAmount, reservationExpiresAt: order.reservationExpiresAt,
                createdAt: order.createdAt, cartFingerprint: accepted.fingerprint});
            router.push(`/checkout/payment/${encodeURIComponent(order.orderNumber)}`);
        } catch (error) {
            setCheckoutMessage(error instanceof Error ? error.message : "Could not reserve delivery. Please try again.");
        } finally {setCheckoutPending(false);}
    }

    function useLocation() {
        if (!window.isSecureContext || !navigator.geolocation) {
            setLocationState("unavailable");
            return;
        }
        setLocationState("locating");
        navigator.geolocation.getCurrentPosition(
            position => {
                // Keep coordinates only in this page's memory until the next explicit
                // boundary check; never persist them in a cart, URL or analytics.
                if (features?.deliveryAddressBoundaries) setDevicePoint({
                    latitude: position.coords.latitude, longitude: position.coords.longitude});
                invalidateArea();
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
                    {error && <button type="button" onClick={retry}><T text="Try again" /></button>}
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
                                   onChange={event => {setLocality(event.target.value); setMessage(""); invalidateArea();}}
                                   minLength={2} maxLength={120} required placeholder="e.g. Hazratganj" />
                            {features.deliveryZones && <><label htmlFor="delivery-postal-code">Six-digit PIN code</label>
                                <input id="delivery-postal-code" name="postalCode" inputMode="numeric" autoComplete="postal-code"
                                       value={postalCode} onChange={event => {setPostalCode(event.target.value); invalidateArea();}}
                                       pattern="[0-9]{6}" required placeholder="e.g. 226001" /></>}
                            {features.deliveryCapacity && cart.items.length > 0 && <><label htmlFor="delivery-service-date">Delivery date (IST)</label>
                                <input id="delivery-service-date" name="serviceDate" type="date" value={serviceDate}
                                       min={features.today} max={businessDateOffset(Math.min(features.futureOrderingDays, 30))}
                                       onChange={event => {setServiceDate(event.target.value); invalidateArea();}} required />
                                <p className={styles.hint}>Checking {cart.items.length} item(s) from your current branch. A result does not reserve a rider.</p></>}
                            <label htmlFor="delivery-pin">{features.deliveryAddressBoundaries ? "Map pin coordinates (latitude, longitude)" : "Map pin or landmark (optional)"}</label>
                            <input id="delivery-pin" name="pin" value={pin} maxLength={160}
                                   onChange={event => {setPin(event.target.value); invalidateArea();}}
                                   placeholder={features.deliveryAddressBoundaries ? "e.g. 26.85, 80.94" : "Paste a map link or enter a landmark"} />
                            <p className={styles.hint}>{features.deliveryAddressBoundaries
                                ? "Your pin is sent once to check a reviewed boundary, without saving it. You may use device location instead."
                                : "The optional map pin or landmark stays on this page. When available, locality and PIN go to the server for a provisional check."}</p>
                            <button type="submit" disabled={pending} className={styles.action}>{pending ? "Checking area..." : "Check area"} <span aria-hidden="true">&rarr;</span></button>
                        </form>
                        {message && <p role="status" className={styles.result}>{message}</p>}
                        {quote && quote.provisionalWindows.length > 0 && <div className={styles.result}>
                            <strong>Configured rider windows (IST)</strong>
                            <ul>{quote.provisionalWindows.map(window => <li key={window.id}>
                                {window.serviceDate} · {window.startsAt.slice(0, 5)}–{window.endsAt.slice(0, 5)}
                            </li>)}</ul>
                            <p>These times are provisional. A rider and stock are held only after you confirm an available price.</p>
                        </div>}
                    </section>
                    <aside className={styles.card} aria-labelledby="device-location">
                        <span className={styles.step}>02 / OPTIONAL</span>
                        <h2 id="device-location">Use my location</h2>
                        <p>Your browser asks only when you choose this option. Your coordinates are sent for a one-time boundary check and stay in this page&apos;s memory.</p>
                        <button type="button" className={styles.outline} disabled={locationState === "locating"} onClick={useLocation}>
                            {locationState === "locating" ? "Finding location..." : "Use my location"}
                        </button>
                        {locationState === "received" && <p role="status" className={styles.result}>{features.deliveryAddressBoundaries
                            ? "Location ready in this page for one boundary check. Enter your locality and PIN above, then check your area."
                            : "Location received for this one-time check. Enter your locality above while delivery zones are being prepared."}</p>}
                        {locationState === "unavailable" && <p role="status" className={styles.result}>Location is unavailable or permission was declined. Enter your locality above instead.</p>}
                        <div className={styles.notice}><strong>{features.deliveryCheckout ? "Availability is checked at confirmation." : "Delivery is not confirmed yet."}</strong>
                            <p>{features.deliveryCheckout ? "Review the live price and window before placing an order. Pickup ordering remains available."
                                : "We'll confirm supported areas and a fulfilment branch when delivery opens. Pickup ordering is unaffected."}</p></div>
                    </aside>
                    {features.deliveryCheckout && checked && quote && quote.provisionalWindows.length > 0 &&
                        <section className={`${styles.card} ${styles.checkout}`} aria-labelledby="delivery-checkout">
                            <span className={styles.step}>03 / DELIVERY CHECKOUT</span>
                            <h2 id="delivery-checkout">Choose a window, then review the price.</h2>
                            <p>Rider windows remain provisional until your order is created. All times are IST.</p>
                            <form onSubmit={reviewPrice}>
                                <fieldset className={styles.windows}>
                                    <legend>Available windows</legend>
                                    {quote.provisionalWindows.map(window => <label key={window.id} className={styles.window}>
                                        <input type="radio" name="delivery-window" value={window.id}
                                               checked={selectedWindowId === window.id}
                                               onChange={() => {setSelectedWindowId(window.id); invalidatePrice();}}
                                               required />
                                        <span>{window.serviceDate} · {window.startsAt.slice(0, 5)}–{window.endsAt.slice(0, 5)} IST</span>
                                    </label>)}
                                </fieldset>
                                <label htmlFor="delivery-address">Full delivery address</label>
                                <input id="delivery-address" autoComplete="street-address" value={addressLine} required minLength={2} maxLength={300}
                                       onChange={event => {setAddressLine(event.target.value); invalidatePrice();}}
                                       placeholder="House or flat number, street and landmark" />
                                <label htmlFor="delivery-name"><T text="Your name" /></label>
                                <input id="delivery-name" autoComplete="name" value={customerName} required minLength={2} maxLength={150}
                                       onChange={event => {setCustomerName(event.target.value); invalidatePrice();}} />
                                <label htmlFor="delivery-phone">Indian mobile number</label>
                                <input id="delivery-phone" type="tel" inputMode="tel" autoComplete="tel" value={customerPhone}
                                       onChange={event => {setCustomerPhone(event.target.value); invalidatePrice();}}
                                       required placeholder="10-digit mobile number" />
                                <button type="submit" disabled={checkoutPending} className={styles.action}>
                                    {checkoutPending ? "Checking current price..." : "Review delivery price"} <span aria-hidden="true">&rarr;</span>
                                </button>
                            </form>
                            {checkoutMessage && <p role="alert" className={styles.result}>{checkoutMessage}</p>}
                            {accepted && <div className={styles.price} aria-live="polite">
                                <p className={styles.step}>YOUR PRICE / {accepted.quote.currency}</p>
                                <ul>{accepted.quote.items.map((item, index) => <li key={index}>
                                    <span>{item.productName}</span><strong>₹{item.totalAmount}</strong>
                                </li>)}</ul>
                                <div className={styles.total}><span>Food before tax</span><strong>₹{accepted.quote.subtotal}</strong></div>
                                <div className={styles.total}><span>Food tax</span><strong>₹{accepted.quote.taxAmount}</strong></div>
                                <div className={styles.total}><span>Delivery fee</span><strong>₹{accepted.quote.deliveryFee}</strong></div>
                                <div className={styles.total}><span>Total including tax and delivery</span><strong>₹{accepted.quote.totalAmount}</strong></div>
                                <p>Price valid until {new Intl.DateTimeFormat("en-IN", {timeZone: "Asia/Kolkata", hour: "numeric", minute: "2-digit"})
                                    .format(new Date(accepted.quote.expiresAt))} IST. The final amount is checked again when you place the order.</p>
                                <button type="button" className={styles.action} onClick={placeOrder} disabled={checkoutPending}>
                                    {checkoutPending ? "Reserving delivery..." : "Confirm delivery and continue to payment"}
                                </button>
                            </div>}
                        </section>}
                </div>}
            </div>
        </main>
    </AppShell>;
}
