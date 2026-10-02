"use client";
import {T,useTranslation} from "@/lib/language";
import LinkFeedback from "@/components/common/LinkFeedback";


import Image from "next/image";
import Link from "next/link";
import {useEffect, useState} from "react";
import type {Branch} from "@/types/branch";
import type {MenuProduct} from "@/types/menu";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import BranchDetails from "@/components/branch/BranchDetails";
import {getBranch} from "@/services/branchApi";

export default function BranchMenuGallery({branch, products, activeTab, onTabChange}: {branch: Branch; products: MenuProduct[]; activeTab: "menu" | "details"; onTabChange: (tab: "menu" | "details") => void}) {
    useTranslation();
    const features = useStorefrontFeatures();
    const branchExperience = features?.branchExperience === true;
    const [failed, setFailed] = useState<string[]>([]);
    const [currentBranch, setCurrentBranch] = useState<Branch | null>(null);
    useEffect(() => {
        const controller = new AbortController();
        getBranch(branch.id, controller.signal).then(result => {if (!controller.signal.aborted) setCurrentBranch(result);})
            .catch(() => { /* Keep the selected branch details when a refresh is unavailable. */ });
        return () => controller.abort();
    }, [branch.id]);
    const photos = products.filter(product => product.available && product.imageUrl && !failed.includes(product.imageUrl))
        .filter((product, index, list) => list.findIndex(candidate => candidate.imageUrl === product.imageUrl) === index).slice(0, 3);

    return <div className="gokul-branch-gallery">
        <div className="gokul-branch-intro">
            <p className="gokul-overline">Gokul Sweets &amp; Restaurants / Pickup menu</p>
            <h1>{branch.name}</h1>
            <p><T text="Fresh sweets, snacks and meals from your chosen branch. Browse the live menu and choose a pickup time at checkout." /></p>
            <div className="gokul-branch-meta"><span>{[branch.address, branch.city].filter(Boolean).join(", ") || "Your selected pickup branch"}</span>
                <a href="#gokul-menu-items" className="gokul-branch-menu-cta"><T text="Browse menu" /></a></div>
        </div>
        {activeTab === "menu" && (photos.length ? <div className="gokul-gallery-photos" aria-label="Food from this branch's menu">
            {photos.map((product, index) => <div key={product.id} className={`gokul-gallery-tile gokul-gallery-tile-${index}`}>
                <Image src={product.imageUrl!} alt={product.name} fill sizes={index === 0 ? "(max-width: 700px) 100vw, 65vw" : "(max-width: 700px) 50vw, 25vw"}
                    className="object-cover" onError={() => setFailed(current => [...current, product.imageUrl!])} />
                <span>{product.name}</span>
            </div>)}
        </div> : <div className="gokul-gallery-fallback" role="img" aria-label="Gokul Sweets brand banner"><strong><T text="Freshly made." /><br /><T text="Ready for you." /></strong></div>)}
        <nav className="gokul-branch-tabs" aria-label="Branch pages">
            {branchExperience && <Link href={`/branches/${branch.id}`}><T text="Home" /><LinkFeedback /></Link>}
            <button type="button" aria-current={activeTab === "menu" ? "page" : undefined} onClick={() => onTabChange("menu")}><T text="Menu" /></button>
            {features?.occasionEnquiries && <Link href="/occasions"><span className="desktop-celebration-label"><T text="Occasions & gifting" /></span><span className="mobile-celebration-label"><T text="Bulk order" /></span><LinkFeedback /></Link>}
            {branchExperience ? <button type="button" aria-current={activeTab === "details" ? "page" : undefined} onClick={() => onTabChange("details")}><T text="Branch details" /></button>
                : <Link href="/about#our-branches"><T text="Branch details" /><LinkFeedback /></Link>}
        </nav>
        {activeTab === "details" && <BranchDetails branch={currentBranch?.id === branch.id ? currentBranch : branch} />}
    </div>;
}
