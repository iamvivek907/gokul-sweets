"use client";

import {useEffect, useRef, useState, useSyncExternalStore} from "react";
import Link from "next/link";
import CustomerIcon from "./CustomerIcon";
import {useCart} from "@/hooks/useCart";
import {T, useTranslation} from "@/lib/language";
import {dismissOrderingTour, OPEN_ORDERING_TOUR, orderingTourSeen, orderingTourServerSnapshot, subscribeOrderingTour} from "@/lib/orderingTour";
import {getPendingOrderSnapshot, getServerPendingOrderSnapshot, parsePendingOrder, subscribeToPendingOrder} from "@/lib/pendingOrderStorage";
import {getPendingPaymentSnapshot, getServerPendingPaymentSnapshot, parsePendingPayment, subscribeToPendingPayment} from "@/lib/pendingPaymentStorage";

const steps = [
    {title: "Choose your branch", icon: "pin" as const, description: "Open Branches and choose where you want to collect your order. Each branch has its own menu, prices and pickup slots.", hint: "Use the branch control in the header to check or change your branch."},
    {title: "Check pickup date & time", icon: "receipt" as const, description: "Check your pickup date and time in the menu or checkout. Change them if needed, and check that your items are available for that pickup.", hint: "An item may start service later in the day. Choose a matching pickup time; checkout asks you to confirm it again."},
    {title: "Add your favourites", icon: "menu" as const, description: "Use Add, choose a pack size or weight, and adjust the quantity. Open View cart to review your items and continue to checkout.", hint: "The menu starts on All. Search or Categories helps you find more items."},
    {title: "Pay, then collect", icon: "receipt" as const, description: "Verify your phone when asked, confirm pickup and review the full price before paying. Wait for payment confirmation, then find your order in Orders.", hint: "For pickup, show the order and its pickup code when it is ready. If payment is uncertain, check Orders before trying again."}
];

export default function OrderingTour({pathname, onActivityChange}: {pathname: string; onActivityChange: (active: boolean) => void}) {
    const seen = useSyncExternalStore(subscribeOrderingTour, orderingTourSeen, orderingTourServerSnapshot);
    const order = parsePendingOrder(useSyncExternalStore(subscribeToPendingOrder, getPendingOrderSnapshot, getServerPendingOrderSnapshot));
    const payment = parsePendingPayment(useSyncExternalStore(subscribeToPendingPayment, getPendingPaymentSnapshot, getServerPendingPaymentSnapshot));
    const {isEmpty} = useCart();
    const [open, setOpen] = useState(false);
    const [opener, setOpener] = useState<HTMLElement | null>(null);
    const begin = () => { setOpener(document.activeElement as HTMLElement | null); dismissOrderingTour(); setOpen(true); };
    useEffect(() => {
        const replay = () => {
            if (document.querySelector("dialog[open], [aria-modal=true]")) return;
            setOpener(document.activeElement as HTMLElement | null);
            dismissOrderingTour(); setOpen(true);
        };
        window.addEventListener(OPEN_ORDERING_TOUR, replay);
        return () => window.removeEventListener(OPEN_ORDERING_TOUR, replay);
    }, []);
    const invite = !seen && isEmpty && !order && payment?.paymentStatus !== "PENDING"
        && ["/", "/home", "/menu", "/branches"].includes(pathname);
    const active = invite || open;
    useEffect(() => {
        onActivityChange(active);
        return () => onActivityChange(false);
    }, [active, onActivityChange]);
    return <>
        {invite && <aside className="ordering-tour-invite" aria-label="Ordering guide">
            <div><strong><T text="First visit?" /></strong><span><T text="See how to book a pickup in four quick steps." /></span></div>
            <button type="button" onClick={begin}><T text="Show me how" /></button>
            <button type="button" className="ordering-tour-skip" onClick={dismissOrderingTour}><T text="Not now" /></button>
        </aside>}
        {open && <TourDialog opener={opener} onClose={() => setOpen(false)} />}
    </>;
}

function TourDialog({onClose, opener}: {onClose: () => void; opener: HTMLElement | null}) {
    const [step, setStep] = useState(0);
    const dialog = useRef<HTMLDialogElement>(null);
    const title = useRef<HTMLHeadingElement>(null);
    const translate = useTranslation();
    useEffect(() => {
        const surface = dialog.current;
        const overflow = document.body.style.overflow;
        surface?.showModal(); document.body.style.overflow = "hidden";
        return () => { surface?.close(); document.body.style.overflow = overflow; const target = opener?.isConnected ? opener : document.querySelector<HTMLButtonElement>(".ordering-tour-replay"); target?.focus({preventScroll: true}); };
    }, [opener]);
    useEffect(() => { title.current?.focus({preventScroll: true}); }, [step]);
    const current = steps[step], last = step === steps.length - 1;
    return <dialog ref={dialog} className="ordering-tour-dialog" aria-labelledby="ordering-tour-title" onCancel={event => { event.preventDefault(); onClose(); }}>
        <div className="ordering-tour-top"><span><T text="How to order" /> · {step + 1}/{steps.length}</span><button type="button" onClick={onClose}><T text="Skip tour" /></button></div>
        <div className="ordering-tour-content">
            <div className="ordering-tour-symbol"><CustomerIcon kind={current.icon} /></div>
            <h2 ref={title} tabIndex={-1} id="ordering-tour-title"><T text={current.title} /></h2>
            <p><T text={current.description} /></p><div className="ordering-tour-tip"><T text={current.hint} /></div>
        </div>
        <div className="ordering-tour-progress" aria-label={translate("Tour progress")}>
            {steps.map((item, index) => <span key={item.title} data-current={index === step} />)}
        </div>
        <div className="ordering-tour-actions">
            <button type="button" disabled={step === 0} onClick={() => setStep(value => value - 1)}><T text="Previous" /></button>
            <button type="button" className="ordering-tour-primary" onClick={() => last ? onClose() : setStep(value => value + 1)}><T text={last ? "Got it" : "Next"} /></button>
        </div>
        {last && <Link className="ordering-tour-menu-link" href="/menu" onClick={onClose}><T text="Browse menu" /> →</Link>}
    </dialog>;
}
