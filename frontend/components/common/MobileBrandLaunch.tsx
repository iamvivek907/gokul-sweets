"use client";
import {useEffect, useRef, useSyncExternalStore} from "react";
import {usePathname, useRouter} from "next/navigation";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {constrainedPhoneConnection} from "@/lib/mobileConnection";
import {warmMenu} from "@/services/menuApi";
import {T} from "@/lib/language";
import ReferenceWordmark from "@/components/layout/ReferenceWordmark";
import "./MobileBrandLaunch.css";

const subscribeHydration=()=>()=>{};
const hydratedSnapshot=()=>true;
const serverSnapshot=()=>false;

/** One short entrance per document/app resume; route changes never restart it. */
export default function MobileBrandLaunch() {
    const hydrated=useSyncExternalStore(subscribeHydration,hydratedSnapshot,serverSnapshot);
    const dialog = useRef<HTMLDialogElement>(null);
    const pathname = usePathname();
    const entryPath = useRef(pathname);
    const router = useRouter();
    const {branch} = useSelectedBranch();
    const paymentReturn = pathname.startsWith("/checkout/payment") || pathname.startsWith("/orders/");
    const staff = pathname.startsWith("/admin") || pathname.startsWith("/staff");
    useEffect(() => {
        if (staff) return;
        const surface = dialog.current;
        if (!surface) return;
        let timer: ReturnType<typeof setTimeout>;
        let hiddenAt = 0;
        const close = () => {clearTimeout(timer); surface.close();};
        const show = () => {
            if (!window.matchMedia("(max-width: 640px)").matches || document.visibilityState !== "visible") return;
            if (!surface.open) surface.showModal();
            clearTimeout(timer); timer = setTimeout(close, 2400);
        };
        const resume = () => {
            if (document.visibilityState === "hidden") {hiddenAt = Date.now(); close();}
            // A long app resume gets the welcome; returning from a payment app does not.
            else if (hiddenAt && Date.now() - hiddenAt > 60_000 && !/^\/(checkout|orders)(\/|$)/.test(location.pathname)) show();
        };
        if (!/^\/(checkout\/payment|orders\/)/.test(entryPath.current)) show();
        document.addEventListener("visibilitychange", resume);
        return () => {close(); document.removeEventListener("visibilitychange", resume);};
    }, [staff]);
    useEffect(() => {if (paymentReturn) dialog.current?.close();}, [paymentReturn]);
    useEffect(() => {
        if (staff || paymentReturn || !branch || !matchMedia("(max-width: 640px)").matches) return;
        if (constrainedPhoneConnection()) return;
        router.prefetch("/menu");
        router.prefetch(`/branches/${branch.id}`);
        void warmMenu(branch.id);
        const cover = branch.mobileCoverImageUrl || branch.coverImageUrl;
        if (cover) {const image = new Image(); image.src = cover;}
    }, [branch, staff, paymentReturn, router]);
    return <>{!hydrated&&!staff&&!paymentReturn&&<div className="gokul-mobile-boot" role="status" aria-label="Opening Gokul"><ReferenceWordmark className="brand-loading-wordmark"/><p>Fresh for your moments</p></div>}<dialog ref={dialog} className="gokul-mobile-launch" aria-labelledby="gokul-launch-title">
        <div className="gokul-mobile-launch-copy">
            <h1 id="gokul-launch-title"><span className="sr-only"><T text="Gokul Sweets" /></span><ReferenceWordmark className="brand-loading-wordmark" /></h1>
            <p><T text="Fresh for your moments" /></p>
            <div className="gokul-launch-food" aria-hidden="true">
                <svg className="launch-dish launch-sweet" viewBox="0 0 64 64" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M10 38h44c-2 11-11 17-22 17S12 49 10 38Z"/><path d="M7 38h50M19 32a7 7 0 1 1 14 0M31 32a7 7 0 1 1 14 0M23 19l2-4m11 4 2-4"/></svg>
                <svg className="launch-dish launch-meal" viewBox="0 0 64 64" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="32" cy="35" r="22"/><circle cx="32" cy="35" r="15"/><path d="M23 35c2-5 5-7 9-7s7 2 9 7M24 40h16M21 9v10m-4-10v7c0 3 2 5 4 5s4-2 4-5V9m-4 12v4M45 9v16"/></svg>
                <svg className="launch-dish launch-tea" viewBox="0 0 64 64" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M14 28h31v13c0 8-6 13-15 13s-16-5-16-13V28Zm31 4h5c8 0 8 12 0 12h-5M11 55h38M23 9c-5 6 5 8 0 14m12-14c-5 6 5 8 0 14"/></svg>
            </div>
        </div>
    </dialog></>;
}
