"use client";
import LinkFeedback from "@/components/common/LinkFeedback";

import {T,translate,useLanguage} from "@/lib/language";

import type {ReactNode} from "react";
import Link from "next/link";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import BranchSelector from "@/components/branch/BranchSelector";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import "./checkout-experience.css";

type Stage = "pickup" | "details" | "review" | "offers" | "payment";
const stages: {key: Stage; label: string}[] = [
    {key: "pickup", label: "Pickup"},
    {key: "details", label: "Details"},
    {key: "review", label: "Review"},
    {key: "offers", label: "Offers"},
    {key: "payment", label: "Payment"}
];

export default function CheckoutExperienceFrame({
    enabled, stage, children, allowBranchChange = false
}: {
    enabled: boolean;
    stage: Stage;
    children: ReactNode;
    allowBranchChange?: boolean;
}) {
    useLanguage();
    const {branch} = useSelectedBranch();
    const simple=useStorefrontFeatures()?.simplifiedCheckout===true;
    const visibleStages: {key: Stage;label:string}[]=simple?[{key:"pickup",label:"Pickup"},{key:"review",label:"Review & offers"},{key:"payment",label:"Payment"}]:stages;
    const activeStage=simple&&["details","offers"].includes(stage)?"review":stage;
    const currentIndex = visibleStages.findIndex(item => item.key === activeStage);
    if (!enabled) return <>{children}</>;
    const previousRoutes: Partial<Record<Stage, string>> = {
        pickup: "/checkout/pickup",
        details: "/checkout/customer"
    };

    return <div className="checkout-experience-v2">
        <header className="checkout-experience-head">
            <Link href="/" className="checkout-experience-brand" aria-label="Gokul Sweets · Home">
                <span className="checkout-experience-mark" aria-hidden="true">G</span>
                <span><T text="Gokul Sweets" /><small><T text="FRESH FOR YOUR MOMENTS" /></small></span>
            <LinkFeedback /></Link>
            <div className="checkout-experience-branch">
                <span className="checkout-experience-location" aria-hidden="true">⌖</span>
                <span className="checkout-experience-branch-copy">
                    <small><T text="PICKUP FROM" /></small>
                    <strong>{branch?.name ?? translate("Choose a shop")}</strong>
                </span>
                {allowBranchChange && <BranchSelector compact />}
            </div>
        </header>
        <nav className="checkout-experience-progress" aria-label="Checkout progress">
            {visibleStages.map(({key, label}, index) => {
                const href = currentIndex <= 2 && index < currentIndex ? previousRoutes[key] : undefined;
                return href
                    ? <Link key={key} href={href} className="completed">{index + 1} {translate(label)}<LinkFeedback /></Link>
                    : <span key={key} aria-current={activeStage === key ? "step" : undefined}
                        className={activeStage === key ? "current" : ""}>{index + 1} {translate(label)}</span>;
            })}
        </nav>
        <div className="checkout-experience-content">{children}</div>
    </div>;
}
