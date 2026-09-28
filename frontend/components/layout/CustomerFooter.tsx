"use client";

import {useEffect, useState} from "react";
import Link from "next/link";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {getBranch} from "@/services/branchApi";

export default function CustomerFooter() {
    const {branch} = useSelectedBranch();
    const [licence, setLicence] = useState<{branchId: number; number: string | null} | null>(null);

    useEffect(() => {
        if (!branch) return;
        const controller = new AbortController();
        void getBranch(branch.id, controller.signal)
            .then(current => {
                if (!controller.signal.aborted) setLicence({branchId: branch.id, number: current.fssaiLicenceNumber});
            })
            .catch(() => {
                if (!controller.signal.aborted) setLicence(null);
            });
        return () => controller.abort();
    }, [branch?.id]);

    const fssaiLicenceNumber = branch && licence?.branchId === branch.id ? licence.number : null;
    return <footer aria-label="Customer footer" className="customer-site-footer border-t border-[#eadfd6] bg-white px-4 pt-6 pb-[calc(90px+env(safe-area-inset-bottom))]">
        <div className="gokul-footer-grid">
            <div><h2>Gokul Sweets</h2><p>Freshly made for the moments that matter. Order online, then collect from your chosen branch.</p></div>
            <div><h3>EXPLORE</h3><Link href="/menu">Menu</Link><Link href="/">Branches</Link><Link href="/about">Our story</Link></div>
            <div><h3>YOUR ORDER</h3><Link href="/orders">Orders</Link><Link href="/profile">Profile</Link><span>Pickup only</span></div>
            <div><h3>GOOD TO KNOW</h3><span>Choose a branch to see its live menu.</span><span>Pickup times and the full price are confirmed before payment.</span></div>
        </div>
        <div className="gokul-footer-bottom"><span>© Gokul Sweets</span>{fssaiLicenceNumber && <span>FSSAI licence no. {fssaiLicenceNumber} · {branch?.name}</span>}<span>Made with care, ready for pickup.</span></div>
    </footer>;
}
