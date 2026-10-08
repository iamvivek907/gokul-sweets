"use client";

import {useEffect, useRef, useState, useSyncExternalStore} from "react";
import CustomerIcon from "./CustomerIcon";
import {useCart} from "@/hooks/useCart";
import {T, useTranslation} from "@/lib/language";
import {dismissOrderingTour, OPEN_ORDERING_TOUR, orderingTourSeen, orderingTourServerSnapshot, subscribeOrderingTour} from "@/lib/orderingTour";
import {getPendingOrderSnapshot, getServerPendingOrderSnapshot, parsePendingOrder, subscribeToPendingOrder} from "@/lib/pendingOrderStorage";
import {getPendingPaymentSnapshot, getServerPendingPaymentSnapshot, parsePendingPayment, subscribeToPendingPayment} from "@/lib/pendingPaymentStorage";

const steps = [
    {title: "Choose your branch", icon: "pin" as const, description: "Choose where you’ll collect your order from Branches or the header.", hint: "Each branch has its own menu and pickup slots."},
    {title: "Check pickup date & time", icon: "receipt" as const, description: "Pick a date and time. Add items available for that pickup.", hint: "Some items start service later. Confirm your pickup at checkout."},
    {title: "Add your favourites", icon: "menu" as const, description: "Tap Add, choose a size or weight, then review View cart.", hint: "Use Search or Categories to find more items."},
    {title: "Pay, then collect", icon: "receipt" as const, description: "Verify your phone, review the total and pay. Find your order in Orders.", hint: "Show your pickup code when ready. Unsure about payment? Check Orders first."}
];

export default function OrderingTour({pathname, onActivityChange}: {pathname: string; onActivityChange: (active: boolean) => void}) {
    const seen = useSyncExternalStore(subscribeOrderingTour, orderingTourSeen, orderingTourServerSnapshot);
    const order = parsePendingOrder(useSyncExternalStore(subscribeToPendingOrder, getPendingOrderSnapshot, getServerPendingOrderSnapshot));
    const payment = parsePendingPayment(useSyncExternalStore(subscribeToPendingPayment, getPendingPaymentSnapshot, getServerPendingPaymentSnapshot));
    const {isEmpty} = useCart();
    const translate = useTranslation();
    const [open, setOpen] = useState(false);
    const [offeredHere, setOfferedHere] = useState(false);
    const [opener, setOpener] = useState<HTMLElement | null>(null);
    const dismissInvitation = () => { setOfferedHere(false); dismissOrderingTour(); };
    const begin = () => { setOfferedHere(false); setOpener(document.activeElement as HTMLElement | null); dismissOrderingTour(); setOpen(true); };
    useEffect(() => {
        const replay = () => {
            if (document.querySelector("dialog[open], [aria-modal=true]")) return;
            setOpener(document.activeElement as HTMLElement | null);
            setOfferedHere(false); dismissOrderingTour(); setOpen(true);
        };
        window.addEventListener(OPEN_ORDERING_TOUR, replay);
        return () => window.removeEventListener(OPEN_ORDERING_TOUR, replay);
    }, []);
    const eligible = isEmpty && !order && payment?.paymentStatus !== "PENDING"
        && ["/", "/home", "/menu", "/branches"].includes(pathname);
    useEffect(() => {
        if (seen || !eligible) return;
        // Retain this one invitation after hydration while persisting exposure
        // immediately, even if the customer leaves without pressing a button.
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setOfferedHere(true);
        dismissOrderingTour();
    }, [seen, eligible]);
    const invite = offeredHere && eligible;
    const active = invite || open;
    useEffect(() => {
        onActivityChange(active);
        return () => onActivityChange(false);
    }, [active, onActivityChange]);
    return <>
        {invite && <aside className="ordering-tour-invite" aria-label="Ordering guide">
            <div><CustomerIcon kind="receipt" /><strong><T text="First visit?" /></strong></div>
            <button type="button" onClick={begin}><T text="Show me how" /></button>
            <button type="button" className="ordering-tour-skip" aria-label={translate("Dismiss ordering guide")} onClick={dismissInvitation}><span aria-hidden="true">×</span></button>
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
    return <dialog ref={dialog} className="ordering-tour-dialog" aria-labelledby="ordering-tour-title" onCancel={event => { event.preventDefault(); onClose(); }} onClick={event => {
        if (event.target !== event.currentTarget) return;
        const bounds = event.currentTarget.getBoundingClientRect();
        if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) onClose();
    }}>
        <div className="ordering-tour-top"><span><T text="How to order" /> <span className="ordering-tour-count">{step + 1}/{steps.length}</span></span><button type="button" onClick={onClose}><T text="Close" /><span aria-hidden="true"> ×</span></button></div>
        <div className="ordering-tour-content">
            <div className="ordering-tour-heading"><div className="ordering-tour-symbol"><CustomerIcon kind={current.icon} /></div>
            <h2 ref={title} tabIndex={-1} id="ordering-tour-title"><T text={current.title} /></h2></div>
            <p><T text={current.description} /></p><div className="ordering-tour-tip"><T text={current.hint} /></div>
        </div>
        <div className="ordering-tour-progress" aria-label={translate("Tour progress")}>
            {steps.map((item, index) => <span key={item.title} data-current={index === step} />)}
        </div>
        <div className="ordering-tour-actions">
            <button type="button" disabled={step === 0} onClick={() => setStep(value => value - 1)}><T text="Previous" /></button>
            <button type="button" className="ordering-tour-primary" onClick={() => last ? onClose() : setStep(value => value + 1)}><T text={last ? "Got it" : "Next"} /></button>
        </div>
    </dialog>;
}
