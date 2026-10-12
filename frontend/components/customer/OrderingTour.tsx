"use client";

import {useEffect, useMemo, useRef, useState, useSyncExternalStore} from "react";
import {useRouter} from "next/navigation";
import CustomerIcon from "./CustomerIcon";
import {useCart} from "@/hooks/useCart";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {T, useTranslation} from "@/lib/language";
import {dismissOrderingTour, OPEN_ORDERING_TOUR, orderingTourSeen, orderingTourServerSnapshot, subscribeOrderingTour,
    getWalkthroughSnapshot, getWalkthroughServerSnapshot, parseWalkthrough, subscribeWalkthrough, startWalkthrough, stopWalkthrough, followWalkthrough, updateWalkthrough, type WalkthroughStep} from "@/lib/orderingTour";
import {getPendingOrderSnapshot, getServerPendingOrderSnapshot, parsePendingOrder, subscribeToPendingOrder} from "@/lib/pendingOrderStorage";
import {getPendingPaymentSnapshot, getServerPendingPaymentSnapshot, parsePendingPayment, subscribeToPendingPayment} from "@/lib/pendingPaymentStorage";

const hints = {
    branch: {title: "Choose your branch", text: "Tap the highlighted branch control and choose where you’ll collect your order.", action: "Choose branch", icon: "pin" as const},
    menu: {title: "Explore the menu", text: "Your branch is selected. Open Browse menu or tap Menu below.", action: "Open menu", icon: "menu" as const},
    pickup: {title: "Choose your pickup", text: "Open the time picker and confirm a date and time. Available items will match your pickup.", action: "Choose time", icon: "receipt" as const},
    add: {title: "Add something you love", text: "Tap an available Add button. Choose a size or weight if asked. The guide continues once it is in your cart.", action: "Show available item", icon: "menu" as const},
    cart: {title: "Review your cart", text: "Your item is added. Open View cart or Continue to review pickup, quantities and the total before payment.", action: "View cart", icon: "receipt" as const}
};
const stages: WalkthroughStep[] = ["branch", "menu", "pickup", "add", "cart"];
function findTarget(step: WalkthroughStep): HTMLElement | null {
    const selector = step === "menu" ? '[data-ordering-target="menu"], a[href="/menu"]' : `[data-ordering-target="${step}"]`;
    const elements = [...document.querySelectorAll<HTMLElement>(selector)].filter(node => {
        const bounds = node.getBoundingClientRect();
        return bounds.width > 0 && bounds.height > 0 && !node.matches(':disabled, [aria-hidden="true"]') && getComputedStyle(node).visibility !== "hidden";
    });
    return elements.find(node => {const rect = node.getBoundingClientRect(); return rect.top >= 0 && rect.bottom <= innerHeight;}) ?? elements[0] ?? null;
}

export default function OrderingTour({pathname, onActivityChange}: {pathname: string; onActivityChange: (active: boolean) => void}) {
    const router = useRouter(), translate = useTranslation();
    const seen = useSyncExternalStore(subscribeOrderingTour, orderingTourSeen, orderingTourServerSnapshot);
    const raw = useSyncExternalStore(subscribeWalkthrough, getWalkthroughSnapshot, getWalkthroughServerSnapshot);
    const saved = useMemo(() => parseWalkthrough(raw), [raw]);
    const order = parsePendingOrder(useSyncExternalStore(subscribeToPendingOrder, getPendingOrderSnapshot, getServerPendingOrderSnapshot));
    const payment = parsePendingPayment(useSyncExternalStore(subscribeToPendingPayment, getPendingPaymentSnapshot, getServerPendingPaymentSnapshot));
    const {isEmpty} = useCart();
    const {branch} = useSelectedBranch();
    const features = useStorefrontFeatures();
    const smart = features?.smartAvailability === true;
    const branchId = branch?.id ?? null;
    const [offeredHere, setOfferedHere] = useState(false);
    const [placement, setPlacement] = useState<{left: number; top: number; hidden: boolean; found: boolean}>({left: 12, top: 12, hidden: true, found: false});
    const coach = useRef<HTMLElement>(null), target = useRef<HTMLElement | null>(null);
    const browsing = ["/", "/home", "/menu", "/branches"].includes(pathname) || /^\/branches\/\d+$/.test(pathname);
    const eligible = isEmpty && !order && payment?.paymentStatus !== "PENDING" && browsing;
    const step = saved ? followWalkthrough(saved, branchId, pathname === "/menu", !isEmpty, smart) : null;
    const visible = !!saved && browsing && !order && payment?.paymentStatus !== "PENDING";
    const begin = () => {setOfferedHere(false); startWalkthrough(branchId, pathname === "/menu", !isEmpty, smart);};
    const close = () => {const control = target.current ?? document.querySelector<HTMLElement>(".ordering-tour-replay"); setOfferedHere(false); stopWalkthrough(); control?.focus({preventScroll: true});};
    useEffect(() => {
        if (seen || !eligible) return;
        // Preserve this first invitation locally after recording its exposure.
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setOfferedHere(true);
        dismissOrderingTour();
    }, [seen, eligible]);
    useEffect(() => {
        const replay = () => {
            if (document.querySelector('dialog[open], [aria-modal="true"], :popover-open')) return;
            setOfferedHere(false); startWalkthrough(branchId, pathname === "/menu", !isEmpty, smart);
            if (!browsing) router.push(branchId ? `/branches/${branchId}` : "/branches");
        };
        window.addEventListener(OPEN_ORDERING_TOUR, replay);
        return () => window.removeEventListener(OPEN_ORDERING_TOUR, replay);
    }, [branchId, pathname, isEmpty, smart, browsing, router]);
    useEffect(() => {
        if (saved && (order || payment?.paymentStatus === "PENDING")) stopWalkthrough();
        else if (saved && step) updateWalkthrough(step, branchId);
    }, [saved, branchId, step, order, payment?.paymentStatus]); // Saved JSON changes only on an actual step change.
    useEffect(() => {
        if (!saved) return;
        const timer = window.setTimeout(stopWalkthrough, Math.max(0, saved.expiresAt - Date.now()));
        return () => clearTimeout(timer);
    }, [saved]);
    const invite = offeredHere && eligible;
    useEffect(() => {onActivityChange(invite || visible); return () => onActivityChange(false);}, [invite, visible, onActivityChange]);
    useEffect(() => {
        if (!visible || !step) return;
        let frame = 0, alive = true;
        const measure = () => {
            if (!alive) return;
            const blocked = !!document.querySelector('dialog[open], [aria-modal="true"], :popover-open');
            const next = blocked ? null : findTarget(step);
            if (target.current !== next) {target.current?.classList.remove("ordering-tour-target"); target.current = next; next?.classList.add("ordering-tour-target");}
            const width = Math.min(320, innerWidth - 24), height = coach.current?.offsetHeight || 190;
            const rect = next?.getBoundingClientRect();
            const left = Math.max(12, Math.min(rect?.left ?? innerWidth - width - 12, innerWidth - width - 12));
            let top = innerHeight - height - 88;
            if (rect && rect.top >= 0 && rect.bottom <= innerHeight) top = rect.bottom + height + 24 <= innerHeight ? rect.bottom + 12 : rect.top - height - 12;
            top = Math.max(12, Math.min(top, innerHeight - height - 12));
            setPlacement(previous => previous.left === left && previous.top === top && previous.hidden === blocked && previous.found === !!next ? previous : {left, top, hidden: blocked, found: !!next});
        };
        const schedule = () => {cancelAnimationFrame(frame); frame = requestAnimationFrame(measure);};
        const observer = new MutationObserver(schedule);
        observer.observe(document.body, {childList: true, subtree: true, attributes: true, attributeFilter: ["disabled", "aria-hidden", "open", "class"]});
        const size = new ResizeObserver(schedule); if (coach.current) size.observe(coach.current);
        const escape = (event: KeyboardEvent) => {if (event.key === "Escape" && !document.querySelector('dialog[open], [aria-modal="true"], :popover-open')) {const control = target.current; stopWalkthrough(); control?.focus({preventScroll: true});}};
        window.addEventListener("resize", schedule); window.addEventListener("scroll", schedule, true); document.addEventListener("toggle", schedule, true); window.addEventListener("keydown", escape);
        schedule();
        return () => {alive = false; cancelAnimationFrame(frame); observer.disconnect(); size.disconnect(); window.removeEventListener("resize", schedule); window.removeEventListener("scroll", schedule, true); document.removeEventListener("toggle", schedule, true); window.removeEventListener("keydown", escape); target.current?.classList.remove("ordering-tour-target"); target.current = null;};
    }, [visible, step]);
    const act = () => {
        if (!step) return;
        if (step === "menu") {router.push("/menu"); return;}
        const node = findTarget(step);
        if (node) {node.scrollIntoView({block: "center", behavior: "instant"}); node.focus({preventScroll: true}); if (step !== "add" && !(step === "branch" && !node.hasAttribute("popoverTarget"))) node.click();}
        else if (step === "branch") router.push("/branches");
    };
    return <>
        {invite && <aside className="ordering-tour-invite" aria-label={translate("Ordering guide")}>
            <div><CustomerIcon kind="receipt" /><strong><T text="First visit?" /></strong></div>
            <button type="button" onClick={begin}><T text="Show me how" /></button>
            <button type="button" className="ordering-tour-skip" aria-label={translate("Dismiss ordering guide")} onClick={close}><span aria-hidden="true">×</span></button>
        </aside>}
        {visible && step && <aside ref={coach} className="ordering-tour-coach" aria-label={translate("Ordering guide")} style={{left: placement.left, top: placement.top, visibility: placement.hidden ? "hidden" : "visible"}}>
            <div className="ordering-tour-top"><span><T text="How to order" /> <span className="ordering-tour-count">{stages.indexOf(step) + 1}/5</span></span><button type="button" onClick={close}><T text="Close" /><span aria-hidden="true"> ×</span></button></div>
            <div className="ordering-tour-heading"><div className="ordering-tour-symbol"><CustomerIcon kind={hints[step].icon} /></div><h2 aria-live="polite"><T text={hints[step].title} /></h2></div>
            <p><T text={hints[step].text} /></p>
            {!placement.found && pathname === "/menu" && step !== "menu" && <small role="status"><T text="Waiting for an available control. You can browse normally or close this guide." /></small>}
            <div className="ordering-tour-actions"><span><T text="Continue at your pace" /></span><button type="button" className="ordering-tour-primary" disabled={!placement.found && step !== "branch" && step !== "menu"} onClick={act}><T text={hints[step].action} /></button></div>
        </aside>}
    </>;
}
