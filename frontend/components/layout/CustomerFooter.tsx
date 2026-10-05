"use client";
import {T} from "@/lib/language";
import LinkFeedback from "@/components/common/LinkFeedback";


import {useEffect, useState} from "react";
import Link from "next/link";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {getBranch} from "@/services/branchApi";

export default function CustomerFooter() {
    const {branch} = useSelectedBranch();
    const branchId = branch?.id;
    const [licence, setLicence] = useState<{branchId: number; number: string | null} | null>(null);

    useEffect(() => {
        if (!branchId) return;
        const controller = new AbortController();
        void getBranch(branchId, controller.signal)
            .then(current => {
                if (!controller.signal.aborted) setLicence({branchId, number: current.fssaiLicenceNumber});
            })
            .catch(() => {
                if (!controller.signal.aborted) setLicence(null);
            });
        return () => controller.abort();
    }, [branchId]);

    const fssaiLicenceNumber = branch && licence?.branchId === branch.id ? licence.number : null;
    return <footer aria-label="Customer footer" className="customer-site-footer border-t border-[#eadfd6] bg-white px-4 pt-6 pb-[calc(90px+env(safe-area-inset-bottom))]">
        <div className="gokul-footer-grid">
            <div><h2><span className="customer-footer-brand-mark" aria-hidden="true">G</span><T text="Gokul Sweets" /></h2><p><T text="Freshly made for the moments that matter. Order online, then collect from your chosen branch." /></p></div>
            <div><h3><T text="EXPLORE" /></h3><Link href="/menu"><T text="Menu" /><LinkFeedback /></Link><Link href="/branches"><T text="Branches" /><LinkFeedback /></Link><Link href="/about"><T text="Our story" /><LinkFeedback /></Link></div>
            <div><h3><T text="YOUR ORDER" /></h3><Link href="/orders"><T text="Orders" /><LinkFeedback /></Link><Link href="/profile"><T text="Profile" /><LinkFeedback /></Link><span><T text="Pickup only" /></span></div>
            <div><h3><T text="GOOD TO KNOW" /></h3><span><T text="Choose a branch to see its live menu." /></span><span><T text="Pickup times and the full price are confirmed before payment." /></span></div>
        </div>
        {fssaiLicenceNumber && <div className="gokul-fssai" aria-label={`FSSAI licence number for ${branch?.name}: ${fssaiLicenceNumber}`}>
            <span className="gokul-fssai-business">Gokul Sweets · {branch?.name}</span>
            <span className="gokul-fssai-mark" aria-hidden="true">fssai</span>
            <span className="gokul-fssai-number">Lic. No. {fssaiLicenceNumber}</span>
        </div>}
        <div className="gokul-footer-bottom"><span>© Gokul Sweets</span><span><T text="Made with care, ready for pickup." /></span></div>
    </footer>;
}
