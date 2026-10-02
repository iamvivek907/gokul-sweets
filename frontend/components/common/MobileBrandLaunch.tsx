"use client";
import {useEffect, useRef} from "react";
import {usePathname, useRouter} from "next/navigation";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {warmMenu} from "@/services/menuApi";
import {T} from "@/lib/language";
import "./MobileBrandLaunch.css";

/** One short entrance per document/app resume; route changes never restart it. */
export default function MobileBrandLaunch() {
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
        router.prefetch("/menu");
        router.prefetch(`/branches/${branch.id}`);
        void warmMenu(branch.id);
        const cover = branch.mobileCoverImageUrl || branch.coverImageUrl;
        if (cover) {const image = new Image(); image.src = cover;}
    }, [branch, staff, paymentReturn, router]);
    return <dialog ref={dialog} className="gokul-mobile-launch" aria-labelledby="gokul-launch-title">
        <div className="gokul-mobile-launch-copy"><span aria-hidden="true">G</span>
            <h1 id="gokul-launch-title"><T text="Gokul Sweets" /></h1>
            <p><T text="Fresh for your moments" /></p>
        </div>
    </dialog>;
}
