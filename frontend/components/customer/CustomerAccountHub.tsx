"use client";
import {notifyCustomerIdentityChanged} from "@/lib/customerIdentityEvents";
import {orderDisplayNumber} from "@/lib/orderDisplayNumber";
import dynamic from "next/dynamic";
const OccasionRequests=dynamic(()=>import("@/components/occasion/OccasionRequests"),{loading:()=> <p role="status">Loading your requests…</p>});
import ReorderDialog from "./ReorderDialog";
import CustomerRewards from "./CustomerRewards";
import AddressLocationAssist from "./AddressLocationAssist";
import AccountTierMark from "./AccountTierMark";
import MobilePageBack from "./MobilePageBack";
import {usePhoneViewport} from "@/hooks/usePhoneViewport";
import {T} from "@/lib/language";

import {formatWeight} from "@/lib/orderQuantity";

import CustomerNotificationLink from "./CustomerNotificationLink";
import LogoutConfirmation from "./LogoutConfirmation";
import {Suspense, useCallback, useEffect, useRef, useState} from "react";
import Link from "next/link";
import {useRouter} from "next/navigation";
import {apiClient,ApiError} from "@/services/apiClient";
import {getMenu} from "@/services/menuApi";
import {getVerifiedOrderPage, type VerifiedOrderPage} from "@/services/orderApi";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {accountMilestones, currentMilestone} from "@/lib/accountMilestones";
import type {CustomerSession} from "@/components/customer/CustomerIdentityPanel";
import type {CustomerOrderSummaryResponse, CustomerOrderResponse} from "@/types/order";
import type {MenuProduct} from "@/types/menu";
import {formatOrderCurrency, formatOrderDate, formatOrderTime, getOrderStatusPresentation} from "@/lib/orderTracking";

const base = "/api/customer/identity/account";
type Address = {id: number; label: string; addressLine: string; locality: string; postalCode: string};
type Account = {completedOrders?:number;paidOrders: number; favouriteProductIds: number[]; addresses: Address[];
    preferences: {dietaryNotes: string | null; preferredBranchId: number | null}};
export type AccountSection = "badges" | "orders" | "favourites" | "addresses" | "preferences" | "details" | "notifications";

export default function CustomerAccountHub({session, onSessionChange, initialSection}: {initialSection?:AccountSection;session: CustomerSession | null;
    onSessionChange: (session: CustomerSession) => void}) {
    const features = useStorefrontFeatures();
    const phone=usePhoneViewport();
    const modern=features?.futuristicStorefrontV2===true||features?.checkoutExperienceV2===true;
    const compact=phone===true&&(features?.futuristicStorefrontV2===true||features?.checkoutExperienceV2===true);
    const enabled = features?.customerAccountHub === true;
    const {branch} = useSelectedBranch();
    const router = useRouter();
    const [account, setAccount] = useState<Account | null>(null);
    const [orders, setOrders] = useState<CustomerOrderSummaryResponse[]>([]);
    const detailDialog = useRef<HTMLDialogElement>(null);
    const [selectedOrder, setSelectedOrder] = useState<CustomerOrderResponse | null>(null);
    const [detailError, setDetailError] = useState("");
    const [detailLoading, setDetailLoading] = useState(false);
    const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState("");
    const [dietary, setDietary] = useState("");
    const [address, setAddress] = useState({label: "", addressLine: "", locality: "", postalCode: ""});
    const [editingAddressId, setEditingAddressId] = useState<number | null>(null);
    const [reorder,setReorder]=useState<CustomerOrderSummaryResponse|null>(null);
    const closeReorder=useCallback(()=>setReorder(null),[]);
    const [pages,setPages]=useState<VerifiedOrderPage[]>([]);
    const [pageIndex,setPageIndex]=useState(0);
    const [paging,setPaging]=useState(false);
    const pageRequest=useRef<AbortController|null>(null);
    useEffect(()=>()=>pageRequest.current?.abort(),[]);
    const [menu, setMenu] = useState<MenuProduct[]>([]);
    const [activeSection, setActiveSection] = useState<AccountSection>(initialSection??"badges");
    const [orderType,setOrderType]=useState<"pickup"|"bulk">("pickup");
    const [logoutOpen, setLogoutOpen] = useState(false);
    const [editingDetails, setEditingDetails] = useState(false);
    const [nameDraft, setNameDraft] = useState(session?.name ?? "");

    useEffect(() => {
        const fromHash = () => {
            const selected = ({"#account-milestones": "badges", "#account-orders": "orders",
                "#account-favourites": "favourites", "#account-addresses": "addresses",
                "#account-preferences": "preferences", "#account-details": "details", "#account-notifications": "notifications"} as Record<string, AccountSection>)[window.location.hash];
            if (selected === "notifications") {router.replace("/notifications?from=%2Fprofile"); return;}
            if (selected) setActiveSection(selected);
        };
        fromHash();
        window.addEventListener("hashchange", fromHash);
        return () => window.removeEventListener("hashchange", fromHash);
    }, [router]);

    const reload = useCallback(async () => {
        pageRequest.current?.abort();setPaging(false);
        const [next, history] = await Promise.all([
            apiClient<Account>(base, {credentials: "include",signal:AbortSignal.timeout(8000)}), getVerifiedOrderPage(null,AbortSignal.timeout(8000))
        ]);
        setAccount(next); setDietary(next.preferences.dietaryNotes ?? ""); setOrders(history.orders);setPages([history]);setPageIndex(0);
        setStatus("ready");
    }, []);

    useEffect(() => {
        if (!enabled || !session?.authenticated) return;
        let active = true;
        void Promise.all([apiClient<Account>(base, {credentials: "include",signal:AbortSignal.timeout(8000)}), getVerifiedOrderPage(null,AbortSignal.timeout(8000))])
            .then(([next, history]) => {if (active) {setAccount(next); setDietary(next.preferences.dietaryNotes ?? ""); setOrders(history.orders);setPages([history]);setPageIndex(0); setStatus("ready");}})
            .catch(() => {if (active) setStatus("error");});
        return () => {active = false;};
    }, [enabled, session?.authenticated, session?.phone]);

    if (!enabled || !session?.authenticated) return null;
    if (status === "loading") return <p role="status" className="mt-6 text-sm"><T text="Loading your account…" /></p>;
    if (status === "error" || !account) return <section className="mt-6 rounded-3xl border border-[#eadfd6] bg-white p-6">
        <p role="alert"><T text="Your account could not load. Your orders remain safe." /></p>
        <button type="button" onClick={() => {setStatus("loading"); void reload().catch(() => setStatus("error"));}}
            className="mt-3 text-sm font-semibold text-[#7a1625] underline"><T text="Try again" /></button></section>;

    async function perform(action: () => Promise<unknown>, success: string) {
        setBusy(true); setMessage("");
        try {await action(); await reload(); setMessage(success);}
        catch {setMessage("We could not save that change. Please try again.");}
        finally {setBusy(false);}
    }

    async function showOrderDetails(orderNumber: string) {
        setSelectedOrder(null); setDetailError(""); setDetailLoading(true);
        detailDialog.current?.showModal();
        try {
            const detail = await apiClient<CustomerOrderResponse>(
                `/api/customer/identity/orders/${encodeURIComponent(orderNumber)}`, {credentials: "include"});
            if (detailDialog.current?.open) setSelectedOrder(detail);
        } catch {
            if (detailDialog.current?.open) setDetailError("Order details could not load. Please try again.");
        } finally {setDetailLoading(false);}
    }

    async function changeOrderPage(index:number) {
        if(paging)return;
        if(pages[index]){setPageIndex(index);setOrders(pages[index].orders);requestAnimationFrame(()=>document.getElementById("account-orders")?.scrollIntoView({block:"start"}));return;}
        const before=pages[pageIndex]?.nextBefore;if(index!==pageIndex+1||!before)return;
        const controller=new AbortController();pageRequest.current=controller;setPaging(true);setMessage("");
        try{const next=await getVerifiedOrderPage(before,AbortSignal.any([controller.signal,AbortSignal.timeout(8000)]));
            if(controller.signal.aborted)return;
            setPages(current=>[...current,next]);setOrders(next.orders);setPageIndex(index);requestAnimationFrame(()=>document.getElementById("account-orders")?.scrollIntoView({block:"start"}));
        }catch(error){if(!controller.signal.aborted){if(error instanceof ApiError&&[401,403].includes(error.status)){setOrders([]);setPages([]);setStatus("error");}else setMessage("The next page could not load. Your current orders are saved. Try Next again.");}}
        finally{if(!controller.signal.aborted)setPaging(false);}
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
            notifyCustomerIdentityChanged();
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
            notifyCustomerIdentityChanged();
            return true;
        } catch {setMessage("Could not sign out. Please try again."); setBusy(false); return false;}
    }

    const earned = currentMilestone(account.completedOrders??0);
    const displayName = session.name?.trim() || (compact?"Your Gokul account":"Gokul guest");
    const initials = displayName.split(/\s+/).slice(0, 2).map(part => part[0]?.toUpperCase()).join("");
    const maskedPhone = session.phone ? `+91 •••••• ${session.phone.replace(/\D/g, "").slice(-4)}` : "Phone verified";
    return <div className={`account-hub mt-6 ${initialSection?"account-focused":"account-overview"}`}>
        {initialSection&&<MobilePageBack href="/profile" label="Back to profile" className="account-focused-back"/>}
        {reorder&&features&&<ReorderDialog key={`${session.phone}:${reorder.orderNumber}`} order={reorder} phone={session.phone} features={features} compact={compact} onClose={closeReorder}/>}
        <LogoutConfirmation open={logoutOpen} onClose={() => setLogoutOpen(false)} onConfirm={signOut} />
        <header className="account-cover relative overflow-hidden rounded-3xl p-6 sm:p-9">
            <div className="account-cover-art" aria-hidden="true"><span>✦</span><span>✦</span><span>✦</span></div>
            <div className="relative z-10 flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
                <div className="flex min-w-0 items-center gap-4 sm:gap-6">
                    <div className="account-avatar" aria-hidden="true">{initials}</div>
                    <div className="min-w-0"><p className="account-cover-kicker"><T text="Your Gokul profile" /></p>
                        <h1 className="mt-1 truncate text-2xl font-bold sm:text-4xl">{displayName}<AccountTierMark orders={account.completedOrders??0}/></h1>
                        <p className="mt-2 text-sm">{maskedPhone}</p>
                        <button type="button" onClick={() => {setNameDraft(session.name ?? ""); setEditingDetails(true); showSection("details");}}
                            className="mt-4 rounded-full border border-[#f6dcae] px-4 py-2 text-sm font-semibold text-[#fff9ed] hover:bg-white/15"><T text="Edit details" /></button></div>
                </div>
                <div className="account-earned" aria-label={earned ? `Earned badge: ${earned.title}` : "No badge earned yet"}>
                    <span className="account-earned-symbol" aria-hidden="true">{earned ? "✓" : "○"}</span>
                    <div><span className="account-earned-label">{earned ? "Earned badge" : "Your first badge"}</span>
                        <strong>{earned?.title ?? "One order away"}</strong></div>
                </div>
            </div>
        </header>
        {features?.gokulRewards?<CustomerRewards overview/>:<>
        <section className="mobile-profile-rewards" aria-label="Rewards"><div><T text="Rewards" /><strong><T text="Coming soon" /></strong></div><p><T text="Earned points are not available yet. A balance will appear here when the rewards programme is launched." /></p></section>
        </>}
        {!session.name?.trim()&&<aside className="account-name-invitation"><div><strong><T text="What should we call you?"/></strong><p><T text="Add your name for a warmer welcome. It is optional."/></p></div><button type="button" onClick={()=>{setEditingDetails(true);showSection("details");}}><T text="Add your name"/></button></aside>}
        <div className="account-layout mt-6">
            <nav className="account-navigation" aria-label="Profile sections">
                {([['badges', 'Badges'], ['orders', 'Order history'], ['favourites', 'Favourites'],
                    ['addresses', 'My addresses'], ['preferences', 'Preferences'], ['details', 'Profile details']] as const)
                    .map(([section, label]) => <button key={section} type="button" aria-pressed={activeSection === section}
                        onClick={() => compact?router.push(`/profile/${section}`):showSection(section)}><span>{label}</span><svg className="account-nav-chevron" aria-hidden="true" viewBox="0 0 24 24"><path d="m9 6 6 6-6 6" fill="none" stroke="currentColor" strokeWidth="1.8"/></svg></button>)}
                {features?.notificationInbox && <CustomerNotificationLink><T text="Notification inbox" /></CustomerNotificationLink>}
                {!compact&&<Link href="/profile/privacy"><T text="Privacy and data" /></Link>}
            </nav>
            <div id="account-content" className="account-panels min-w-0 scroll-mt-28 space-y-6" aria-live="polite">
                {activeSection === "badges" && <section id="account-milestones" className="account-milestones rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8">
                    <div className="flex flex-wrap items-end justify-between gap-4">
                        <div><p className="text-xs font-bold uppercase tracking-[0.15em] text-[#c88a20]">Gokul journey</p>
                            <h2 className="mt-2 text-2xl font-bold text-[#241715]"><T text="Your badges" /></h2>
                            <p className="mt-2 text-sm text-[#756763]"><T text="Earned from completed paid orders on your account." /></p></div>
                        <div className="account-order-count"><strong>{account.completedOrders??0}</strong><span><T text="completed visits"/></span></div>
                    </div>
                    <div className="mt-6 grid gap-3 sm:grid-cols-3">{accountMilestones.map(milestone => {
                        const unlocked = (account.completedOrders??0) >= milestone.orders;
                        return <div key={milestone.orders} className={`account-badge ${unlocked ? "is-earned" : "is-locked"}`}>
                            <span className="account-badge-icon" aria-label={unlocked ? "Unlocked" : "Locked"}>{unlocked ? "✓" : "○"}</span>
                            <strong>{milestone.title}</strong><span>{milestone.description}</span>
                        </div>;
                    })}</div>
                    <p className="mt-4 text-xs text-[#756763]"><T text="Badges recognise visits. They are not points or discounts." /></p>
                </section>}
        {activeSection === "orders" && <section id="account-orders" className="profile-history rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8">
            {features?.occasionEnquiries && <nav className="mobile-profile-order-types" aria-label="Order history types"><button type="button" aria-pressed={orderType==="pickup"} onClick={()=>setOrderType("pickup")}><T text="Pickup orders" /></button><button type="button" aria-pressed={orderType==="bulk"} onClick={()=>setOrderType("bulk")}><T text="Bulk order requests" /></button></nav>}
            {orderType==="bulk"&&features?.occasionEnquiries?<Suspense fallback={<p role="status">Loading your requests…</p>}><OccasionRequests key={session.phone} embeddedSession={session}/></Suspense>:<><div className="profile-history-heading flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-xl font-bold text-[#241715]"><T text="Your orders" /></h2>
                <p className="mt-1 text-sm text-[#756763]"><T text="Only orders placed while signed in to this account." /></p></div>
                <span className="text-sm text-[#756763]"><T text="All branches" /></span></div>
            {orders.length ? <div className="profile-history-list mt-4 space-y-3">{orders.map(order => {
                if(!modern)return <div key={order.orderNumber} className="flex flex-wrap items-center justify-between gap-3 border-t border-[#eadfd6] pt-3"><div><button type="button" onClick={()=>{void showOrderDetails(order.orderNumber);}} className="text-left font-semibold text-[#7a1625] underline">{orderDisplayNumber(order)}</button><p className="text-xs text-[#756763]">{order.branchName} · {order.orderStatus.replaceAll("_"," ")}</p></div><div className="flex gap-2"><button type="button" onClick={()=>{void showOrderDetails(order.orderNumber);}} className="min-h-11 rounded-xl border border-[#eadfd6] px-4 text-sm font-semibold text-[#7a1625]"><T text="Details"/></button><button type="button" disabled={busy} onClick={()=>{setReorder(order);}} className="min-h-11 rounded-xl border border-[#eadfd6] px-4 text-sm font-semibold text-[#7a1625] disabled:opacity-50"><T text="Reorder"/></button></div></div>;
                const presentation=getOrderStatusPresentation(order.orderStatus,order.fulfillmentType);
                const date=order.pickupDate??order.deliveryDate;
                return <article key={order.orderNumber} className="profile-order-card">
                    <div className="profile-order-top"><strong className="profile-order-number" title={order.orderNumber}>{order.customerOrderNumber!=null?`Order #${order.customerOrderNumber}`:`Reference …${order.orderNumber.slice(-6)}`}</strong><span className={`profile-order-status tone-${presentation.tone}`}><T text={presentation.label}/></span></div>
                    <p className="profile-order-branch">{order.branchName}</p>
                    <div className="profile-order-meta"><span>{order.fulfillmentType==="DELIVERY"?"Delivery":"Pickup"}{date?` · ${formatOrderDate(date)}`:""}</span><strong>{formatOrderCurrency(order.totalAmount)}</strong></div>
                    <div className="profile-order-actions"><button type="button" onClick={()=>{void showOrderDetails(order.orderNumber);}}><T text="Details"/><span aria-hidden="true"> →</span></button><button type="button" disabled={busy} onClick={()=>{setReorder(order);}}><T text="Reorder"/></button></div>
                </article>;
            })}</div> : <p className="mt-5 text-sm text-[#756763]"><T text="No orders belong to this verified account yet." /></p>}
            <dialog ref={detailDialog} onClose={() => {setSelectedOrder(null); setDetailError("");}}
                className="account-order-dialog m-auto max-h-[85dvh] w-[min(38rem,calc(100vw-2rem))] overflow-y-auto rounded-3xl border border-[#d9e5dc] bg-[#fffaf2] p-0 text-[#172e2c] shadow-2xl backdrop:bg-[#092725b3]"
                aria-label="Order details">
                <div className="flex items-start justify-between gap-4 bg-[#143936] px-5 py-5 text-white sm:px-7">
                    <div><p className="text-xs font-bold uppercase tracking-[0.15em] text-[#f3bca9]"><T text="Your order" /></p>
                        <h3 className="mt-1 font-serif text-3xl"><T text="Order details" /></h3></div>
                    <button type="button" onClick={() => detailDialog.current?.close()} className="grid min-h-11 min-w-11 place-items-center rounded-full border border-white/50 text-xl hover:bg-white/15 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white" aria-label="Close order details">×</button></div>
                <div className="px-5 py-5 sm:px-7 sm:py-6">
                {detailLoading && <p role="status"><T text="Loading order details…" /></p>}
                {detailError && <p role="alert" className="text-[#9e2732]">{detailError}</p>}
                {selectedOrder && <div className="space-y-5 text-sm">
                    <div><p className="text-xs font-bold uppercase tracking-[0.12em] text-[#59706a]"><T text="Order number" /></p>
                        <p className="selectable-text mt-1 break-all font-semibold">{orderDisplayNumber(selectedOrder)}</p>{compact&&<Link className="inline-flex min-h-11 items-center rounded-xl border px-4 text-sm font-bold mt-3" href={`/orders/${encodeURIComponent(selectedOrder.orderNumber)}`}>Manage order & refund</Link>}</div>
                    <div className="flex flex-wrap items-center gap-2"><strong className="text-base">{selectedOrder.branchName}</strong>
                        <span className="rounded-full bg-[#e5f0e8] px-3 py-1 text-xs font-bold uppercase tracking-wide text-[#143936]">{selectedOrder.orderStatus.replaceAll("_", " ")}</span></div>
                    <div className="rounded-2xl border border-[#d9e5dc] bg-white p-4"><p className="text-xs font-bold uppercase tracking-[0.12em] text-[#59706a]">{selectedOrder.fulfillmentType === "DELIVERY" ? "Delivery" : "Pickup"} schedule</p>
                        <p className="mt-1 font-semibold">{selectedOrder.pickupDate
                            ? formatOrderDate(selectedOrder.pickupDate) : selectedOrder.deliveryDate ? formatOrderDate(selectedOrder.deliveryDate) : "Date pending"}
                            {selectedOrder.pickupStartTime ? ` · ${formatOrderTime(selectedOrder.pickupStartTime)}` : ""}</p></div>
                    <div><h4 className="mb-2 text-xs font-bold uppercase tracking-[0.12em] text-[#59706a]"><T text="Items" /></h4>
                        <ul className="divide-y divide-[#d9e5dc] border-y border-[#d9e5dc]">{selectedOrder.items.map(item =>
                            <li key={item.id} className="flex justify-between gap-3 py-3"><span><strong className="font-semibold">{item.productName}</strong><small className="mt-1 block text-[#59706a]">{item.weightGrams ? formatWeight(item.weightGrams) : `${item.quantity} pieces`}</small></span><span className="selectable-text shrink-0 font-semibold">{formatOrderCurrency(item.lineTotal)}</span></li>)}</ul></div>
                    {(selectedOrder.convenienceFee??0)>0 && <p className="flex justify-between gap-3"><span><T text="Convenience fee" />{" "}<small className="block text-[#59706a]"><T text="Includes" />{" "}{formatOrderCurrency(selectedOrder.convenienceFeeTax??0)}{" "}<T text="fee tax" /></small></span><span>{formatOrderCurrency(selectedOrder.convenienceFee??0)}</span></p>}
                    <p className="flex justify-between border-t border-[#d9e5dc] pt-4 text-base font-bold"><span><T text="Order total" /></span><span className="selectable-text">{formatOrderCurrency(selectedOrder.totalAmount)}</span></p>
                </div>}
                </div>
            </dialog>
            <nav className="profile-history-pagination" aria-label="Order history pages"><button type="button" disabled={paging||pageIndex===0} onClick={()=>void changeOrderPage(pageIndex-1)}>Previous</button><span aria-live="polite">Page {pageIndex+1}</span><button type="button" disabled={paging||!pages[pageIndex]?.nextBefore} onClick={()=>void changeOrderPage(pageIndex+1)}>{paging?"Loading…":"Next"}</button></nav>
        </>}</section>}
        {activeSection === "preferences" && <section id="account-preferences" className="rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8"><h2 className="text-xl font-bold text-[#241715]"><T text="Your preferences" /></h2>
            <p className="mt-1 text-sm text-[#756763]"><T text="Dietary notes are for your reference; check ingredients with the branch for each order." /></p>
            <label htmlFor="dietary-notes" className="mt-5 block text-sm font-semibold"><T text="Dietary notes" /></label>
            <textarea id="dietary-notes" maxLength={300} value={dietary} onChange={event => setDietary(event.target.value)} rows={3} className="mt-2 w-full rounded-xl border border-[#eadfd6] p-3 text-sm" placeholder="Optional notes" />
            <button type="button" disabled={busy || dietary === (account.preferences.dietaryNotes ?? "")} onClick={() => {void perform(() => apiClient<void>(`${base}/preferences`, {method: "PUT", credentials: "include", body: JSON.stringify({...account.preferences, dietaryNotes: dietary})}), "Preferences saved.");}}
                className="mt-3 min-h-11 rounded-xl bg-[#7a1625] px-5 text-sm font-semibold text-white disabled:opacity-50"><T text="Save preferences" /></button>
            <p className="mt-5 text-xs text-[#756763]">{account.preferences.preferredBranchId === branch?.id && branch ? `${branch.name} is your saved branch.` : "Choose your branch from the site header when ordering."}</p>
            {branch && account.preferences.preferredBranchId !== branch.id && <button type="button" disabled={busy} onClick={() => {void perform(() => apiClient<void>(`${base}/preferences`, {method: "PUT", credentials: "include", body: JSON.stringify({dietaryNotes: dietary, preferredBranchId: branch.id})}), "Preferred branch saved.");}} className="mt-3 min-h-11 text-sm font-semibold text-[#7a1625] underline"><T text="Save" />{" "}{branch.name}{" "}<T text="as preferred" /></button>}</section>}
            {activeSection === "addresses" && <section id="account-addresses" className="rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8"><h2 className="text-xl font-bold text-[#241715]"><T text="Saved addresses" /></h2>
                <p className="mt-1 text-sm text-[#756763]"><T text="Saved for your account. Delivery coverage and final address are checked separately at checkout." /></p>
                {account.addresses.map(saved => <div key={saved.id} className="mt-4 flex flex-wrap items-start justify-between gap-4 border-t border-[#eadfd6] pt-3"><div><strong className="text-sm">{saved.label}</strong><p className="text-sm text-[#756763]">{saved.addressLine}, {saved.locality} {saved.postalCode}</p></div>
                    <div className="flex gap-3"><button type="button" disabled={busy} onClick={() => {setEditingAddressId(saved.id); setAddress({label: saved.label, addressLine: saved.addressLine, locality: saved.locality, postalCode: saved.postalCode});}} className="text-sm font-semibold text-[#7a1625]"><T text="Edit" /></button>
                    <button type="button" disabled={busy} onClick={() => {void perform(() => apiClient<void>(`${base}/addresses/${saved.id}`, {method: "DELETE", credentials: "include"}), "Address removed.");}} className="text-sm font-semibold text-[#7a1625]"><T text="Remove" /></button></div></div>)}
                {compact&&<AddressLocationAssist onSuggestion={value=>{setAddress(current=>({...current,label:current.label||"Home",addressLine:value.addressLine,locality:value.locality,postalCode:value.postalCode}));}}/>}
                <form className="mt-5 grid gap-2" onSubmit={event => {event.preventDefault(); void perform(async () => {await apiClient<Address>(editingAddressId === null ? `${base}/addresses` : `${base}/addresses/${editingAddressId}`, {method: editingAddressId === null ? "POST" : "PUT", credentials: "include", body: JSON.stringify(address)}); setEditingAddressId(null); setAddress({label: "", addressLine: "", locality: "", postalCode: ""});}, editingAddressId === null ? "Address saved." : "Address updated.");}}>
                    <h3 className="text-sm font-semibold">{editingAddressId === null ? "Add an address" : "Edit address"}</h3>
                    {(["label", "addressLine", "locality", "postalCode"] as const).map(field => <label key={field} className="text-xs font-semibold capitalize">{field === "addressLine" ? "Address line" : field === "postalCode" ? "Postal code" : field}<input required maxLength={field === "postalCode" ? 6 : field === "label" ? 40 : field === "locality" ? 100 : 180} value={address[field]} onChange={event => setAddress(current => ({...current, [field]: event.target.value}))} className="mt-1 block min-h-11 w-full rounded-xl border border-[#eadfd6] px-3 text-sm" /></label>)}
                    <button type="submit" disabled={busy || editingAddressId === null && account.addresses.length >= 5} className="min-h-11 rounded-xl bg-[#7a1625] px-5 text-sm font-semibold text-white disabled:opacity-50">{editingAddressId === null ? "Save address" : "Save changes"}</button>
                    {editingAddressId !== null && <button type="button" onClick={() => {setEditingAddressId(null); setAddress({label: "", addressLine: "", locality: "", postalCode: ""});}} className="min-h-11 text-sm font-semibold text-[#7a1625]"><T text="Cancel" /></button>}</form></section>}
        {activeSection === "favourites" && <section id="account-favourites" className="rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8"><h2 className="text-xl font-bold text-[#241715]"><T text="Saved favourites" /></h2>
            <p className="mt-1 text-sm text-[#756763]"><T text="Save an item from the current branch menu. Availability and prices are checked again when ordering." /></p>
            {account.favouriteProductIds.length > 0 && <div className="mt-4 space-y-2">{account.favouriteProductIds.map(id => <div key={id} className="flex items-center justify-between gap-3 border-t border-[#eadfd6] pt-2"><span className="text-sm">{menu.find(product => product.id === id)?.name ?? "Saved item (not on this branch menu)"}</span><button type="button" disabled={busy} onClick={() => {void perform(() => apiClient<void>(`${base}/favourites/${id}`, {method: "DELETE", credentials: "include"}), "Favourites updated.");}} className="min-h-11 text-sm font-semibold text-[#7a1625]"><T text="Remove" /></button></div>)}</div>}
            {branch ? <><button type="button" disabled={busy} onClick={() => {setBusy(true); void getMenu(branch.id).then(categories => setMenu(categories.flatMap(category => category.products))).catch(() => setMessage("Menu unavailable. Please try again.")).finally(() => setBusy(false));}} className="mt-4 min-h-11 rounded-xl border border-[#eadfd6] px-4 text-sm font-semibold text-[#7a1625]"><T text="Browse" />{" "}{branch.name}{" "}<T text="items" /></button>
                {menu.length > 0 && <div className="mt-4 grid gap-2 sm:grid-cols-2">{menu.filter(product => product.available).slice(0, 30).map(product => <div key={product.id} className="flex items-center justify-between gap-3 border-t border-[#eadfd6] py-2"><span className="text-sm">{product.name}</span><button type="button" disabled={busy} onClick={() => {void perform(() => apiClient<void>(`${base}/favourites/${product.id}`, {method: account.favouriteProductIds.includes(product.id) ? "DELETE" : "PUT", credentials: "include"}), "Favourites updated.");}} className="min-h-11 text-sm font-semibold text-[#7a1625]">{account.favouriteProductIds.includes(product.id) ? "Remove" : "Save"}</button></div>)}</div>}</> : <Link href="/menu" className="mt-4 inline-block text-sm font-semibold text-[#7a1625] underline"><T text="Choose a branch →" /></Link>}
        </section>}
        {activeSection === "details" && <section id="account-details" className="rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8">
            <div className="flex flex-wrap items-start justify-between gap-4">
                <div><h2 className="text-xl font-bold text-[#241715]"><T text="Profile details" /></h2>
                    <p className="mt-1 text-sm text-[#756763]"><T text="Your contact and saved choices." /></p></div>
                {!editingDetails && <button type="button" onClick={() => {setNameDraft(session.name ?? ""); setEditingDetails(true);}}
                    className="rounded-full border border-[#d8c6ba] px-5 py-2 text-sm font-semibold text-[#173c39]"><T text="Edit" /></button>}
            </div>
            <dl className="mt-6 grid gap-5 sm:grid-cols-2">
                <div><dt className="text-xs font-bold uppercase tracking-wide text-[#756763]"><T text="Name" /></dt>
                    <dd className="mt-2 font-semibold text-[#241715]">{displayName}</dd></div>
                <div><dt className="text-xs font-bold uppercase tracking-wide text-[#756763]"><T text="Verified phone" /></dt>
                    <dd className="mt-2 font-semibold text-[#241715]">{maskedPhone}</dd></div>
                <div><dt className="text-xs font-bold uppercase tracking-wide text-[#756763]"><T text="Saved addresses" /></dt>
                    <dd className="mt-2 font-semibold text-[#241715]">{account.addresses.length}</dd></div>
                <div><dt className="text-xs font-bold uppercase tracking-wide text-[#756763]"><T text="Preferred branch" /></dt>
                    <dd className="mt-2 font-semibold text-[#241715]">{account.preferences.preferredBranchId === branch?.id ? branch?.name : "Manage in Preferences"}</dd></div>
            </dl>
            {editingDetails && <form className="mt-7 border-t border-[#eadfd6] pt-6" onSubmit={event => {event.preventDefault(); void saveDetails();}}>
                <label htmlFor="account-name-edit" className="text-sm font-semibold text-[#241715]"><T text="Your name" /></label>
                <input id="account-name-edit" autoComplete="name" required minLength={2} maxLength={80} value={nameDraft}
                    onChange={event => setNameDraft(event.target.value)} className="mt-2 block min-h-11 w-full rounded-xl border border-[#d8c6ba] px-4 text-base" />
                <p className="mt-3 text-sm text-[#756763]"><T text="Your verified phone stays linked to your account. Saved addresses and preferences can be edited in their sections." /></p>
                <div className="mt-4 flex flex-wrap gap-3">
                    <button type="submit" disabled={busy || nameDraft.trim() === (session.name ?? "")}
                        className="min-h-11 rounded-xl bg-[#173c39] px-5 text-sm font-semibold text-white disabled:opacity-50"><T text="Save name" /></button>
                    <button type="button" onClick={() => {setEditingDetails(false); setNameDraft(session.name ?? "");}}
                        className="min-h-11 rounded-xl border border-[#d8c6ba] px-5 text-sm font-semibold"><T text="Cancel" /></button>
                </div>
            </form>}
            <div className="mt-7 flex flex-wrap items-center gap-3 border-t border-[#eadfd6] pt-5">
                <button type="button" onClick={() => showSection("addresses")} className="inline-flex min-h-11 items-center justify-center rounded-xl bg-[#173c39] px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-[#28544e] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#173c39]"><T text="Manage addresses" /></button>
                <button type="button" onClick={() => showSection("preferences")} className="inline-flex min-h-11 items-center justify-center rounded-xl border border-[#bacdc8] bg-[#f4f8f5] px-5 py-2.5 text-sm font-semibold text-[#173c39] transition-colors hover:bg-[#e6f0eb] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#173c39]"><T text="Manage preferences" /></button>
                <button type="button" disabled={busy} onClick={() => setLogoutOpen(true)} className="account-details-signout inline-flex min-h-11 items-center justify-center rounded-xl border border-[#eadfd6] px-5 py-2.5 text-sm font-semibold text-[#7a1625] transition-colors hover:bg-[#fff4ee] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#7a1625] disabled:opacity-50"><T text="Sign out" /></button>
            </div>
        </section>}
        {message && <p role="status" aria-live="polite" className="rounded-xl border border-[#eadfd6] bg-white p-4 text-sm">{message}</p>}
            </div>
        </div>
        {!initialSection&&activeSection!=="orders"&&<button type="button" disabled={busy} onClick={() => setLogoutOpen(true)} className="mobile-account-logout"><T text="Log out" /></button>}
    </div>;
}
