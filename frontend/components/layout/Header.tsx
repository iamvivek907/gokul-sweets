"use client";

import Link from "next/link";
import MobileMenu
    from "@/components/layout/MobileMenu";
import BranchSelector from "@/components/branch/BranchSelector";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useCart} from "@/hooks/useCart";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";


export default function Header() {
    const features = useStorefrontFeatures();
    const futuristic = features?.futuristicStorefrontV2 === true || features?.checkoutExperienceV2 === true;
    const {branch} = useSelectedBranch();
    const cart = useCart();

    return (
        <header
            className="customer-site-header

                sticky
                top-0
                z-40
                w-full
                max-w-full
                overflow-x-clip
                border-b
                border-[#eadfd6]
                bg-[#fffaf3]/95
                backdrop-blur-xl
            "
        >

            <div
                className="
                    mx-auto
                    flex
                    w-full
                    max-w-[1180px]
                    min-w-0
                    items-center
                    justify-between
                    gap-3
                    px-4
                    py-3
                "
            >

                {futuristic ? <div className="future-brand" aria-label="Gokul Sweets">
                    <span className="future-brand-mark" aria-hidden="true">G</span>
                    <span>Gokul Sweets<small>FRESH FOR YOUR MOMENTS</small></span>
                </div> : <div
                    className="
                        min-w-0
                    "
                >

                    <p
                        className="
                            truncate
                            text-[11px]
                            font-medium
                            text-[#756763]
                            sm:text-xs
                        "
                    >
                        Fresh sweets. Happier moments.
                    </p>


                    <h1
                        className="
                            truncate
                            text-lg
                            font-extrabold
                            tracking-tight
                            text-[#7a1625]
                            sm:text-xl
                        "
                    >
                        Gokul Sweets
                    </h1>

                </div>}


                {futuristic && <div className="future-branch-control">
                    <span aria-hidden="true" className="future-location-icon">⌖</span>
                    <span className="future-branch-name"><small>PICKUP FROM</small><strong>{branch?.name ?? "Choose a shop"}</strong></span>
                    {features?.cartSwitchPreview || cart.isEmpty
                        ? <BranchSelector compact />
                        : <Link href="/cart" className="future-branch-review">Review branch</Link>}
                </div>}

                <MobileMenu />

            </div>

        </header>
    );
}
