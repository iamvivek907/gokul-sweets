"use client";
import {useEffect, useId, useRef} from "react";
import {usePathname} from "next/navigation";
import {usePwaInstall} from "@/hooks/usePwaInstall";
import {T, useTranslation} from "@/lib/language";
import "./mobile-install.css";

export default function MobileInstallAppBanner() {
    const pathname = usePathname();
    const pwa = usePwaInstall();
    const {guideOpen, closeGuide, recordBannerView} = pwa;
    const translate = useTranslation();
    const dialog = useRef<HTMLDialogElement>(null);
    const titleId = useId();
    const allowedRoute = pathname === "/" || pathname === "/profile" || /^\/branches\/[0-9]+$/.test(pathname);
    const visible = allowedRoute && pwa.isMobile && !pwa.isInstalled && (pwa.canInstall || pwa.isPromptInProgress || !!pwa.error);
    useEffect(() => {if (visible) recordBannerView();}, [visible, recordBannerView]);
    useEffect(() => {
        const element = dialog.current;
        if (visible && guideOpen) element?.showModal();
        else {element?.close(); if (guideOpen) closeGuide();}
    }, [visible, guideOpen, closeGuide]);
    if (!visible) return null;
    function install() {
        // Never stack our guide/native prompt over verification, logout, or another modal.
        if (document.querySelector('dialog[open], [role="dialog"][aria-modal="true"]')) return;
        void pwa.promptInstall();
    }
    return <section className="pwa-install-card" aria-label={translate("Install Gokul Sweets")}>
        <div className="pwa-install-mark" aria-hidden="true">G</div>
        <div className="pwa-install-copy"><strong><T text="Gokul, one tap away" /></strong>
            <p><T text={pathname === "/profile" ? "Keep your orders and rewards close." : "Fresh favourites, right on your home screen."} /></p>
            {pwa.error && <p role="alert">{translate(pwa.error)}</p>}
            {pwa.canInstall && <button type="button" onClick={install}><T text="Install App" /></button>}
            {pwa.isPromptInProgress && <p role="status"><T text="Opening install options…" /></p>}
        </div>
        <button type="button" className="pwa-install-dismiss" onClick={pwa.dismiss} aria-label={translate("Close install banner")}><T text="Close" /></button>
        <dialog ref={dialog} aria-labelledby={titleId} className="pwa-install-guide"
            onCancel={event => {event.preventDefault(); pwa.closeGuide();}}
            onClick={event => {
                if (event.target !== event.currentTarget) return;
                const box = event.currentTarget.getBoundingClientRect();
                if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) pwa.closeGuide();
            }}>
            <header><span className="pwa-install-mark" aria-hidden="true">G</span>
                <button type="button" autoFocus onClick={pwa.closeGuide} aria-label={translate("Close install instructions")}><T text="Close" /></button></header>
            <h2 id={titleId}><T text="Add Gokul Sweets to your Home Screen" /></h2>
            <p><T text="A shortcut to the same Gokul you know. You can always keep using this website." /></p>
            <ol>
                <li><span aria-hidden="true">1</span><div><strong><T text="Tap Share" /></strong><p><T text="Open your browser’s Share menu." /></p></div></li>
                <li><span aria-hidden="true">2</span><div><strong><T text="Add to Home Screen" /></strong><p><T text="Find this option in the Share menu." /></p></div></li>
                <li><span aria-hidden="true">3</span><div><strong><T text="Tap Add" /></strong><p><T text="Gokul Sweets will appear on your home screen." /></p></div></li>
            </ol>
            <p className="pwa-install-browser-note"><T text="If this option is missing, open this page in Safari and use Share." /></p>
            <button type="button" className="pwa-install-done" onClick={pwa.closeGuide}><T text="Got it" /></button>
        </dialog>
    </section>;
}
