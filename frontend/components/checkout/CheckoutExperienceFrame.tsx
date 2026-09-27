"use client";

import type {ReactNode} from "react";
import Link from "next/link";
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
    const {branch} = useSelectedBranch();
    if (!enabled) return <>{children}</>;
    const currentIndex = stages.findIndex(item => item.key === stage);
    const previousRoutes: Partial<Record<Stage, string>> = {
        pickup: "/checkout/pickup",
        details: "/checkout/customer"
    };

    return <div className="checkout-experience-v2">
        <header className="checkout-experience-head">
            <Link href="/" className="checkout-experience-brand" aria-label="Gokul Sweets · Home">
                <span className="checkout-experience-mark" aria-hidden="true">G</span>
                <span>Gokul Sweets<small>FRESH FOR YOUR MOMENTS</small></span>
            </Link>
            <div className="checkout-experience-branch">
                <span className="checkout-experience-location" aria-hidden="true">⌖</span>
                <span className="checkout-experience-branch-copy">
                    <small>PICKUP FROM</small>
                    <strong>{branch?.name ?? "Choose a shop"}</strong>
                </span>
                {allowBranchChange && <BranchSelector compact />}
            </div>
        </header>
        <nav className="checkout-experience-progress" aria-label="Checkout progress">
            {stages.map(({key, label}, index) => {
                const href = currentIndex <= 2 && index < currentIndex ? previousRoutes[key] : undefined;
                return href
                    ? <Link key={key} href={href} className="completed">{index + 1} {label}</Link>
                    : <span key={key} aria-current={stage === key ? "step" : undefined}
                        className={stage === key ? "current" : ""}>{index + 1} {label}</span>;
            })}
        </nav>
        <div className="checkout-experience-content">{children}</div>
    </div>;
}
