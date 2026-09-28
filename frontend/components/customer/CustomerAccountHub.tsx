"use client";

import {useCallback, useEffect, useState} from "react";
import Link from "next/link";
import {useRouter} from "next/navigation";
import {apiClient} from "@/services/apiClient";
import {getMenu} from "@/services/menuApi";
import {getVerifiedCustomerOrders} from "@/services/orderApi";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useCart} from "@/hooks/useCart";
import {previewCartSwitch, type CartSwitchPreview} from "@/services/cartSwitchPreview";
import {getCartSnapshot} from "@/lib/cartStorage";
import {clearPickupSlot} from "@/lib/checkoutStorage";
import {saveCart} from "@/lib/cartStorage";
import {accountMilestones, currentMilestone} from "@/lib/accountMilestones";
import type {CustomerSession} from "@/components/customer/CustomerIdentityPanel";
import type {CustomerOrderSummaryResponse, CustomerOrderResponse} from "@/types/order";
import type {MenuProduct} from "@/types/menu";

const base = "/api/customer/identity/account";
type Address = {id: number; label: string; addressLine: string; locality: string; postalCode: string};
type Account = {paidOrders: number; favouriteProductIds: number[]; addresses: Address[];
    preferences: {dietaryNotes: string | null; preferredBranchId: number | null}};
type Preview = {orderNumber: string; items: Array<{product: MenuProduct; quantity: number; weightGrams: number | null}>;
    changed: string[]; unavailable: string[]; stock: CartSwitchPreview; cartSnapshot: string; date: string};
type AccountSection = "badges" | "orders" | "favourites" | "addresses" | "preferences" | "details";

export default function CustomerAccountHub({session, onSessionChange}: {session: CustomerSession | null;
    onSessionChange: (session: CustomerSession) => void}) {
    const features = useStorefrontFeatures();
    const enabled = features?.customerAccountHub === true;
    const {branch} = useSelectedBranch();
    const {items: cartItems, branchId: cartBranchId} = useCart();
    const router = useRouter();
    const [account, setAccount] = useState<Account | null>(null);
    const [orders, setOrders] = useState<CustomerOrderSummaryResponse[]>([]);
    const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState("");
    const [dietary, setDietary] = useState("");
    const [address, setAddress] = useState({label: "", addressLine: "", locality: "", postalCode: ""});
    const [editingAddressId, setEditingAddressId] = useState<number | null>(null);
    const [preview, setPreview] = useState<Preview | null>(null);
    const [menu, setMenu] = useState<MenuProduct[]>([]);
    const [activeSection, setActiveSection] = useState<AccountSection>("badges");
    const [editingDetails, setEditingDetails] = useState(false);
    const [nameDraft, setNameDraft] = useState(session?.name ?? "");

    useEffect(() => {
        const fromHash = () => {
            const selected = ({"#account-milestones": "badges", "#account-orders": "orders",
                "#account-favourites": "favourites", "#account-addresses": "addresses",
                "#account-preferences": "preferences", "#account-details": "details"} as Record<string, AccountSection>)[window.location.hash];
            if (selected) setActiveSection(selected);
        };
        fromHash();
        window.addEventListener("hashchange", fromHash);
        return () => window.removeEventListener("hashchange", fromHash);
    }, []);

    const reload = useCallback(async () => {
        const [next, history] = await Promise.all([
            apiClient<Account>(base, {credentials: "include"}), getVerifiedCustomerOrders()
        ]);
        setAccount(next); setDietary(next.preferences.dietaryNotes ?? ""); setOrders(history);
        setStatus("ready");
    }, []);

    useEffect(() => {
        if (!enabled || !session?.authenticated) return;
        let active = true;
        void Promise.all([apiClient<Account>(base, {credentials: "include"}), getVerifiedCustomerOrders()])
            .then(([next, history]) => {if (active) {setAccount(next); setDietary(next.preferences.dietaryNotes ?? ""); setOrders(history); setStatus("ready");}})
            .catch(() => {if (active) setStatus("error");});
        return () => {active = false;};
    }, [enabled, session?.authenticated, session?.phone]);

    if (!enabled || !session?.authenticated) return null;
    if (status === "loading") return <p role="status" className="mt-6 text-sm">Loading your account…</p>;
    if (status === "error" || !account) return <section className="mt-6 rounded-3xl border border-[#eadfd6] bg-white p-6">
        <p role="alert">Your account could not load. Your orders remain safe.</p>
        <button type="button" onClick={() => {setStatus("loading"); void reload().catch(() => setStatus("error"));}}
            className="mt-3 text-sm font-semibold text-[#7a1625] underline">Try again</button></section>;

    async function perform(action: () => Promise<unknown>, success: string) {
        setBusy(true); setMessage("");
        try {await action(); await reload(); setMessage(success);}
        catch {setMessage("We could not save that change. Please try again.");}
        finally {setBusy(false);}
    }

    async function prepareReorder(order: CustomerOrderSummaryResponse) {
        if (!branch || branch.name !== order.branchName || cartItems.length && cartBranchId !== branch.id || !features?.today) {
            setMessage("Select the order's branch and resolve your current cart before reordering. Your cart has not changed.");
            return;
        }
        setBusy(true); setPreview(null); setMessage("");
        try {
            const [detail, categories] = await Promise.all([apiClient<CustomerOrderResponse>(`/api/customer/identity/orders/${encodeURIComponent(order.orderNumber)}`, {credentials: "include"}), getMenu(branch.id)]);
            const products = categories.flatMap(category => category.products);
            const unavailable: string[] = [], changed: string[] = [];
            const lines = detail.items.flatMap(item => {
                const product = products.find(candidate => candidate.id === item.productId);
                if (!product?.available || product.saleMode !== item.saleMode ||
                    product.saleMode === "WEIGHT" && (item.weightGrams == null || item.weightGrams < (product.minimumWeightGrams ?? 250) ||
                    (item.weightGrams - (product.minimumWeightGrams ?? 250)) % (product.weightStepGrams ?? 50) !== 0)) {
                    unavailable.push(item.productName); return [];
                }
                if (Number(product.price) !== Number(item.unitPrice)) changed.push(item.productName);
                return [{product, quantity: item.quantity, weightGrams: item.weightGrams}];
            });
            if (!detail.items.length) unavailable.push("No items in this order");
            const cartSnapshot = getCartSnapshot();
            const combined = [...cartItems];
            for (const line of lines) {
                const index = combined.findIndex(item => item.product.id === line.product.id);
                if (index >= 0) combined[index] = line.product.saleMode === "WEIGHT"
                    ? line : {...line, quantity: combined[index].quantity + line.quantity};
                else combined.push(line);
            }
            const stock = await previewCartSwitch(branch.id, features.today, combined, AbortSignal.timeout(10000));
            if (getCartSnapshot() !== cartSnapshot) throw new Error("Cart changed");
            unavailable.push(...stock.lines.filter(line => !line.orderable).map(line => line.item.product.name));
            if (!stock.availableSlots) unavailable.push("No available pickup slots today");
            setMenu(products);
            setPreview({orderNumber: order.orderNumber, items: lines, changed,
                unavailable: [...new Set(unavailable)], stock, cartSnapshot, date: features.today});
        } catch {setMessage("We could not recheck this order. Please try again.");}
        finally {setBusy(false);}
    }

    async function confirmReorder() {
        if (!preview || !branch || preview.unavailable.length || cartItems.length && cartBranchId !== branch.id) return;
        setBusy(true);
        try {
            if (getCartSnapshot() !== preview.cartSnapshot) {
                setPreview(null); setMessage("Your cart changed. Review the order again."); return;
            }
            const latest = await previewCartSwitch(branch.id, preview.date, preview.stock.lines.map(line => line.item), AbortSignal.timeout(10000));
            if (latest.conflicts || JSON.stringify(latest) !== JSON.stringify(preview.stock)) {
                setPreview(null); setMessage("Price, stock or pickup times changed. Review the order again."); return;
            }
            const merged = latest.lines.map(line => ({...line.item, product: line.proposed!}));
            if (getCartSnapshot() !== preview.cartSnapshot) {
                setPreview(null); setMessage("Your cart changed. Review the order again."); return;
            }
            saveCart({branchId: branch.id, items: merged});
            clearPickupSlot();
            router.push("/cart");
        } catch {setMessage("The menu could not be refreshed. Your cart has not changed.");}
        finally {setBusy(false);}
    }

    function showSection(section: AccountSection) {
        setActiveSection(section);
        setMessage("");
        window.requestAnimationFrame(() => document.getElementById("account-content")?.scrollIntoView({block: "start", behavior: "smooth"}));
    }

    async function saveDetails() {
        const name = nameDraft.trim();
        if (name.length < 2 || name.length > 80) {setMessage("Enter your name (2 to 80 characters)."); return;}
        setBusy(true); setMessage("");
        try {
            await apiClient<void>("/api/customer/identity/me/name", {method: "PUT", credentials: "include", body: JSON.stringify({name})});
            const updated = await apiClient<CustomerSession>("/api/customer/identity/me", {credentials: "include"});
            if (!updated.authenticated) throw new Error("Session expired");
            onSessionChange(updated);
            window.dispatchEvent(new Event("gokul-customer-identity-changed"));
            setEditingDetails(false);
            setMessage("Your name was updated.");
        } catch {setMessage("Your name could not be saved. Please try again.");}
        finally {setBusy(false);}
    }

    async function signOut() {
        setBusy(true); setMessage("");
        try {
            await apiClient<void>("/api/customer/identity/logout", {method: "POST", credentials: "include"});
            onSessionChange({authenticated: false});
            window.dispatchEvent(new Event("gokul-customer-identity-changed"));
        } catch {setMessage("Could not sign out. Please try again."); setBusy(false);}
    }

    const earned = currentMilestone(account.paidOrders);
    const displayName = session.name?.trim() || "Gokul guest";
    const initials = displayName.split(/\s+/).slice(0, 2).map(part => part[0]?.toUpperCase()).join("");
    const maskedPhone = session.phone ? `+91 •••••• ${session.phone.replace(/\D/g, "").slice(-4)}` : "Phone verified";
    return <div className="account-hub mt-6">
        <header className="account-cover relative overflow-hidden rounded-3xl p-6 sm:p-9">
            <div className="account-cover-art" aria-hidden="true"><span>✦</span><span>✦</span><span>✦</span></div>
            <div className="relative z-10 flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
                <div className="flex min-w-0 items-center gap-4 sm:gap-6">
                    <div className="account-avatar" aria-hidden="true">{initials}</div>
                    <div className="min-w-0"><p className="account-cover-kicker">Your Gokul profile</p>
                        <h1 className="mt-1 truncate text-2xl font-bold sm:text-4xl">{displayName}</h1>
                        <p className="mt-2 text-sm">{maskedPhone}</p>
                        <button type="button" onClick={() => {setNameDraft(session.name ?? ""); setEditingDetails(true); showSection("details");}}
                            className="mt-4 rounded-full border border-[#f6dcae] px-4 py-2 text-sm font-semibold text-[#fff9ed] hover:bg-white/15">Edit details</button></div>
                </div>
                <div className="account-earned" aria-label={earned ? `Earned badge: ${earned.title}` : "No badge earned yet"}>
                    <span className="account-earned-symbol" aria-hidden="true">{earned ? "✓" : "○"}</span>
                    <div><span className="account-earned-label">{earned ? "Earned badge" : "Your first badge"}</span>
                        <strong>{earned?.title ?? "One order away"}</strong></div>
                </div>
            </div>
        </header>
        <div className="account-layout mt-6">
            <nav className="account-navigation" aria-label="Profile sections">
                {([['badges', 'Badges'], ['orders', 'Order history'], ['favourites', 'Favourites'],
                    ['addresses', 'My addresses'], ['preferences', 'Preferences'], ['details', 'Profile details']] as const)
                    .map(([section, label]) => <button key={section} type="button" aria-pressed={activeSection === section}
                        onClick={() => showSection(section)}>{label}</button>)}
                <Link href="/profile/privacy">Privacy and data</Link>
            </nav>
            <div id="account-content" className="account-panels min-w-0 scroll-mt-28 space-y-6" aria-live="polite">
                {activeSection === "badges" && <section id="account-milestones" className="account-milestones rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8">
                    <div className="flex flex-wrap items-end justify-between gap-4">
                        <div><p className="text-xs font-bold uppercase tracking-[0.15em] text-[#c88a20]">Gokul journey</p>
                            <h2 className="mt-2 text-2xl font-bold text-[#241715]">Your badges</h2>
                            <p className="mt-2 text-sm text-[#756763]">Earned from completed paid orders on your account.</p></div>
                        <div className="account-order-count"><strong>{account.paidOrders}</strong><span>paid {account.paidOrders === 1 ? "order" : "orders"}</span></div>
                    </div>
                    <div className="mt-6 grid gap-3 sm:grid-cols-3">{accountMilestones.map(milestone => {
                        const unlocked = account.paidOrders >= milestone.orders;
                        return <div key={milestone.orders} className={`account-badge ${unlocked ? "is-earned" : "is-locked"}`}>
                            <span className="account-badge-icon" aria-label={unlocked ? "Unlocked" : "Locked"}>{unlocked ? "✓" : "○"}</span>
                            <strong>{milestone.title}</strong><span>{milestone.description}</span>
                        </div>;
                    })}</div>
                    <p className="mt-4 text-xs text-[#756763]">Badges recognise visits. They are not points or discounts.</p>
                </section>}
        {activeSection === "orders" && <section id="account-orders" className="rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8">
            <div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-xl font-bold text-[#241715]">Your orders</h2>
                <p className="mt-1 text-sm text-[#756763]">Only orders placed while signed in to this account.</p></div>
                <Link href="/orders" className="text-sm font-semibold text-[#7a1625] underline">All orders →</Link></div>
            {orders.length ? <div className="mt-4 space-y-3">{orders.slice(0, 3).map(order => <div key={order.orderNumber} className="flex flex-wrap items-center justify-between gap-3 border-t border-[#eadfd6] pt-3">
                <div><Link href={`/orders/${encodeURIComponent(order.orderNumber)}`} className="font-semibold text-[#7a1625] underline">{order.orderNumber}</Link>
                <p className="text-xs text-[#756763]">{order.branchName} · {order.orderStatus.replaceAll("_", " ")}</p></div>
                <button type="button" disabled={busy} onClick={() => {void prepareReorder(order);}} className="min-h-11 rounded-xl border border-[#eadfd6] px-4 text-sm font-semibold text-[#7a1625] disabled:opacity-50">Reorder</button></div>)}</div> : <p className="mt-5 text-sm text-[#756763]">No orders belong to this verified account yet.</p>}
            {preview && <div className="mt-5 rounded-2xl border border-[#eadfd6] bg-[#fff8ef] p-5" role="status"><h3 className="font-bold text-[#241715]">Review this reorder</h3>
                <p className="mt-2 text-sm text-[#756763]">{preview.items.map(line => line.product.name).join(", ") || "No available items"}</p>
                {preview.changed.length > 0 && <p className="mt-2 text-sm text-[#7a1625]">Prices changed: {preview.changed.join(", ")}. Current menu prices will apply.</p>}
                {preview.unavailable.length > 0 && <p className="mt-2 text-sm text-[#9e2732]">Unavailable or changed: {preview.unavailable.join(", ")}. Browse the menu to choose alternatives.</p>}
                <div className="mt-3 flex flex-wrap gap-3"><button type="button" disabled={busy || preview.unavailable.length > 0} onClick={() => {void confirmReorder();}} className="min-h-11 rounded-xl bg-[#7a1625] px-5 text-sm font-semibold text-white disabled:opacity-50">Add to cart</button>
                    <button type="button" onClick={() => setPreview(null)} className="min-h-11 rounded-xl border border-[#eadfd6] px-4 text-sm">Cancel</button></div></div>}
        </section>}
        {activeSection === "preferences" && <section id="account-preferences" className="rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8"><h2 className="text-xl font-bold text-[#241715]">Your preferences</h2>
            <p className="mt-1 text-sm text-[#756763]">Dietary notes are for your reference; check ingredients with the branch for each order.</p>
            <label htmlFor="dietary-notes" className="mt-5 block text-sm font-semibold">Dietary notes</label>
            <textarea id="dietary-notes" maxLength={300} value={dietary} onChange={event => setDietary(event.target.value)} rows={3} className="mt-2 w-full rounded-xl border border-[#eadfd6] p-3 text-sm" placeholder="Optional notes" />
            <button type="button" disabled={busy || dietary === (account.preferences.dietaryNotes ?? "")} onClick={() => {void perform(() => apiClient<void>(`${base}/preferences`, {method: "PUT", credentials: "include", body: JSON.stringify({...account.preferences, dietaryNotes: dietary})}), "Preferences saved.");}}
                className="mt-3 min-h-11 rounded-xl bg-[#7a1625] px-5 text-sm font-semibold text-white disabled:opacity-50">Save preferences</button>
            <p className="mt-5 text-xs text-[#756763]">{account.preferences.preferredBranchId === branch?.id && branch ? `${branch.name} is your saved branch.` : "Choose your branch from the site header when ordering."}</p>
            {branch && account.preferences.preferredBranchId !== branch.id && <button type="button" disabled={busy} onClick={() => {void perform(() => apiClient<void>(`${base}/preferences`, {method: "PUT", credentials: "include", body: JSON.stringify({dietaryNotes: dietary, preferredBranchId: branch.id})}), "Preferred branch saved.");}} className="mt-3 min-h-11 text-sm font-semibold text-[#7a1625] underline">Save {branch.name} as preferred</button>}</section>}
            {activeSection === "addresses" && <section id="account-addresses" className="rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8"><h2 className="text-xl font-bold text-[#241715]">Saved addresses</h2>
                <p className="mt-1 text-sm text-[#756763]">Saved for your account. Delivery coverage and final address are checked separately at checkout.</p>
                {account.addresses.map(saved => <div key={saved.id} className="mt-4 flex flex-wrap items-start justify-between gap-4 border-t border-[#eadfd6] pt-3"><div><strong className="text-sm">{saved.label}</strong><p className="text-sm text-[#756763]">{saved.addressLine}, {saved.locality} {saved.postalCode}</p></div>
                    <div className="flex gap-3"><button type="button" disabled={busy} onClick={() => {setEditingAddressId(saved.id); setAddress({label: saved.label, addressLine: saved.addressLine, locality: saved.locality, postalCode: saved.postalCode});}} className="text-sm font-semibold text-[#7a1625]">Edit</button>
                    <button type="button" disabled={busy} onClick={() => {void perform(() => apiClient<void>(`${base}/addresses/${saved.id}`, {method: "DELETE", credentials: "include"}), "Address removed.");}} className="text-sm font-semibold text-[#7a1625]">Remove</button></div></div>)}
                <form className="mt-5 grid gap-2" onSubmit={event => {event.preventDefault(); void perform(async () => {await apiClient<Address>(editingAddressId === null ? `${base}/addresses` : `${base}/addresses/${editingAddressId}`, {method: editingAddressId === null ? "POST" : "PUT", credentials: "include", body: JSON.stringify(address)}); setEditingAddressId(null); setAddress({label: "", addressLine: "", locality: "", postalCode: ""});}, editingAddressId === null ? "Address saved." : "Address updated.");}}>
                    <h3 className="text-sm font-semibold">{editingAddressId === null ? "Add an address" : "Edit address"}</h3>
                    {(["label", "addressLine", "locality", "postalCode"] as const).map(field => <label key={field} className="text-xs font-semibold capitalize">{field === "addressLine" ? "Address line" : field === "postalCode" ? "Postal code" : field}<input required maxLength={field === "postalCode" ? 6 : field === "label" ? 40 : field === "locality" ? 100 : 180} value={address[field]} onChange={event => setAddress(current => ({...current, [field]: event.target.value}))} className="mt-1 block min-h-11 w-full rounded-xl border border-[#eadfd6] px-3 text-sm" /></label>)}
                    <button type="submit" disabled={busy || editingAddressId === null && account.addresses.length >= 5} className="min-h-11 rounded-xl bg-[#7a1625] px-5 text-sm font-semibold text-white disabled:opacity-50">{editingAddressId === null ? "Save address" : "Save changes"}</button>
                    {editingAddressId !== null && <button type="button" onClick={() => {setEditingAddressId(null); setAddress({label: "", addressLine: "", locality: "", postalCode: ""});}} className="min-h-11 text-sm font-semibold text-[#7a1625]">Cancel</button>}</form></section>}
        {activeSection === "favourites" && <section id="account-favourites" className="rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8"><h2 className="text-xl font-bold text-[#241715]">Saved favourites</h2>
            <p className="mt-1 text-sm text-[#756763]">Save an item from the current branch menu. Availability and prices are checked again when ordering.</p>
            {account.favouriteProductIds.length > 0 && <div className="mt-4 space-y-2">{account.favouriteProductIds.map(id => <div key={id} className="flex items-center justify-between gap-3 border-t border-[#eadfd6] pt-2"><span className="text-sm">{menu.find(product => product.id === id)?.name ?? "Saved item (not on this branch menu)"}</span><button type="button" disabled={busy} onClick={() => {void perform(() => apiClient<void>(`${base}/favourites/${id}`, {method: "DELETE", credentials: "include"}), "Favourites updated.");}} className="min-h-11 text-sm font-semibold text-[#7a1625]">Remove</button></div>)}</div>}
            {branch ? <><button type="button" disabled={busy} onClick={() => {setBusy(true); void getMenu(branch.id).then(categories => setMenu(categories.flatMap(category => category.products))).catch(() => setMessage("Menu unavailable. Please try again.")).finally(() => setBusy(false));}} className="mt-4 min-h-11 rounded-xl border border-[#eadfd6] px-4 text-sm font-semibold text-[#7a1625]">Browse {branch.name} items</button>
                {menu.length > 0 && <div className="mt-4 grid gap-2 sm:grid-cols-2">{menu.filter(product => product.available).slice(0, 30).map(product => <div key={product.id} className="flex items-center justify-between gap-3 border-t border-[#eadfd6] py-2"><span className="text-sm">{product.name}</span><button type="button" disabled={busy} onClick={() => {void perform(() => apiClient<void>(`${base}/favourites/${product.id}`, {method: account.favouriteProductIds.includes(product.id) ? "DELETE" : "PUT", credentials: "include"}), "Favourites updated.");}} className="min-h-11 text-sm font-semibold text-[#7a1625]">{account.favouriteProductIds.includes(product.id) ? "Remove" : "Save"}</button></div>)}</div>}</> : <Link href="/menu" className="mt-4 inline-block text-sm font-semibold text-[#7a1625] underline">Choose a branch →</Link>}
        </section>}
        {activeSection === "details" && <section id="account-details" className="rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8">
            <div className="flex flex-wrap items-start justify-between gap-4">
                <div><h2 className="text-xl font-bold text-[#241715]">Profile details</h2>
                    <p className="mt-1 text-sm text-[#756763]">Your contact and saved choices.</p></div>
                {!editingDetails && <button type="button" onClick={() => {setNameDraft(session.name ?? ""); setEditingDetails(true);}}
                    className="rounded-full border border-[#d8c6ba] px-5 py-2 text-sm font-semibold text-[#173c39]">Edit</button>}
            </div>
            <dl className="mt-6 grid gap-5 sm:grid-cols-2">
                <div><dt className="text-xs font-bold uppercase tracking-wide text-[#756763]">Name</dt>
                    <dd className="mt-2 font-semibold text-[#241715]">{displayName}</dd></div>
                <div><dt className="text-xs font-bold uppercase tracking-wide text-[#756763]">Verified phone</dt>
                    <dd className="mt-2 font-semibold text-[#241715]">{maskedPhone}</dd></div>
                <div><dt className="text-xs font-bold uppercase tracking-wide text-[#756763]">Saved addresses</dt>
                    <dd className="mt-2 font-semibold text-[#241715]">{account.addresses.length}</dd></div>
                <div><dt className="text-xs font-bold uppercase tracking-wide text-[#756763]">Preferred branch</dt>
                    <dd className="mt-2 font-semibold text-[#241715]">{account.preferences.preferredBranchId === branch?.id ? branch?.name : "Manage in Preferences"}</dd></div>
            </dl>
            {editingDetails && <form className="mt-7 border-t border-[#eadfd6] pt-6" onSubmit={event => {event.preventDefault(); void saveDetails();}}>
                <label htmlFor="account-name-edit" className="text-sm font-semibold text-[#241715]">Your name</label>
                <input id="account-name-edit" autoComplete="name" required minLength={2} maxLength={80} value={nameDraft}
                    onChange={event => setNameDraft(event.target.value)} className="mt-2 block min-h-11 w-full rounded-xl border border-[#d8c6ba] px-4 text-base" />
                <p className="mt-3 text-sm text-[#756763]">Your verified phone stays linked to your account. Saved addresses and preferences can be edited in their sections.</p>
                <div className="mt-4 flex flex-wrap gap-3">
                    <button type="submit" disabled={busy || nameDraft.trim() === (session.name ?? "")}
                        className="min-h-11 rounded-xl bg-[#173c39] px-5 text-sm font-semibold text-white disabled:opacity-50">Save name</button>
                    <button type="button" onClick={() => {setEditingDetails(false); setNameDraft(session.name ?? "");}}
                        className="min-h-11 rounded-xl border border-[#d8c6ba] px-5 text-sm font-semibold">Cancel</button>
                </div>
            </form>}
            <div className="mt-7 flex flex-wrap items-center gap-3 border-t border-[#eadfd6] pt-5">
                <button type="button" onClick={() => showSection("addresses")} className="inline-flex min-h-11 items-center justify-center rounded-xl bg-[#173c39] px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-[#28544e] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#173c39]">Manage addresses</button>
                <button type="button" onClick={() => showSection("preferences")} className="inline-flex min-h-11 items-center justify-center rounded-xl border border-[#bacdc8] bg-[#f4f8f5] px-5 py-2.5 text-sm font-semibold text-[#173c39] transition-colors hover:bg-[#e6f0eb] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#173c39]">Manage preferences</button>
                <button type="button" disabled={busy} onClick={() => {void signOut();}} className="inline-flex min-h-11 items-center justify-center rounded-xl border border-[#eadfd6] px-5 py-2.5 text-sm font-semibold text-[#7a1625] transition-colors hover:bg-[#fff4ee] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#7a1625] disabled:opacity-50">Sign out</button>
            </div>
        </section>}
        {message && <p role="status" aria-live="polite" className="rounded-xl border border-[#eadfd6] bg-white p-4 text-sm">{message}</p>}
            </div>
        </div>
    </div>;
}
