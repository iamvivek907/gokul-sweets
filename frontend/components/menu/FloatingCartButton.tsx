"use client";
import {T} from "@/lib/language";
import LinkFeedback from "@/components/common/LinkFeedback";


import Link from "next/link";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {usePickupIntent} from "@/hooks/usePickupIntent";
import type {AvailableRebateResponse} from "@/types/rebate";
import {useState, useEffect} from "react";
import {startOfferArrival} from "@/lib/offerArrival";


interface FloatingCartButtonProps {
    offerTarget?:AvailableRebateResponse|null;
    offerEnabled?:boolean;

    itemCount:
        number;

    total:
        number;
}


function formatCurrency(
    amount: number
): string {

    return new Intl.NumberFormat(
        "en-IN",
        {
            style:
                "currency",
            currency:
                "INR",
            maximumFractionDigits:
                0
        }
    ).format(
        amount
    );
}


export default function FloatingCartButton({
    itemCount,
    total,
    offerEnabled=false,
    offerTarget
}: FloatingCartButtonProps) {

    const features = useStorefrontFeatures();
    const {branch}=useSelectedBranch();
    const pickup=usePickupIntent(branch?.id);
    const [mobile, setMobile] = useState(false);
    useEffect(() => {
        const media = window.matchMedia("(max-width: 640px)");
        const update = () => setMobile(media.matches);
        update(); media.addEventListener("change", update);
        return () => media.removeEventListener("change", update);
    }, []);
    const threshold=offerTarget?.nextSlabMinimumOrderAmount??0;
    const progress=threshold>0?Math.min(1,Math.max(0,1-(offerTarget?.amountNeededForNextSlab??threshold)/threshold)):0;
    const quick = mobile && features?.simplifiedCheckout;
    const consolidated=quick&&features?.checkoutExperienceV2&&features.acceptedCheckoutQuote;
    if (
        itemCount <= 0
    ) {

        return null;
    }


    return (
        <div
            className="
                gokul-floating-cart fixed
                bottom-[calc(5.5rem+env(safe-area-inset-bottom))]
                left-1/2
                z-60
                w-[calc(100vw-2rem)]
                max-w-lg
                -translate-x-1/2
            "
        >

            {consolidated&&offerEnabled&&<div className="mobile-cart-offer-slot"><button type="button" className="mobile-cart-offer-target" onClick={()=>document.getElementById("menu-offers-open")?.click()}><span className="mobile-cart-offer-icon" aria-hidden="true"><svg viewBox="0 0 40 40"><circle cx="20" cy="20" r="17" fill="none" stroke="#d7e5fc" strokeWidth="3"/><circle cx="20" cy="20" r="17" fill="none" stroke="#2e68c7" strokeWidth="3" strokeLinecap="round" strokeDasharray="106.82" strokeDashoffset={106.82*(1-progress)} transform="rotate(-90 20 20)"/></svg><span>%</span></span><span>{offerTarget?<><strong><T text="Unlock"/> {formatCurrency(offerTarget.nextSlabRebateAmount!)} <T text="off"/></strong><small><T text="Add"/> {formatCurrency(offerTarget.amountNeededForNextSlab!)} <T text="in eligible items"/></small></>:<><strong><T text="Offers & savings"/></strong><small><T text={!pickup.selection&&features?.smartAvailability?"Choose pickup to check your savings":"View eligible offers and ways to save"}/></small></>}</span><span aria-hidden="true">⌃</span></button></div>}
            <Link
                onNavigate={()=>{if(consolidated)startOfferArrival();}}
                href={consolidated ? "/checkout/mobile" : quick ? "/checkout/pickup" : "/cart"}
                className="
                    flex
                    min-h-16
                    w-full
                    items-center
                    justify-between
                    gap-3
                    rounded-2xl
                    bg-[#7a1625]
                    px-4
                    py-3
                    text-white!
                    shadow-[0_10px_30px_rgba(70,15,30,0.30)]
                    transition

                    hover:bg-[#5d0f1b]
                    active:scale-[0.98]

                    sm:px-5
                "
            >

                <div
                    className="
                        flex
                        min-w-0
                        items-center
                        gap-3
                    "
                >

                    <div
                        className="
                            flex
                            h-10
                            w-10
                            shrink-0
                            items-center
                            justify-center
                            rounded-full
                            bg-white
                            text-sm
                            font-bold
                            text-[#7a1625]
                        "
                    >
                        <span key={itemCount} className="mobile-cart-count">{itemCount}</span>
                    </div>


                    <div
                        className="
                            min-w-0
                        "
                    >

                        <p
                            className="
                                text-xs
                                text-white/75!
                            "
                        >
                            {
                                itemCount === 1
                                    ? "1 item"
                                    : `${itemCount} items`
                            }
                        </p>


                        <p
                            className="
                                truncate
                                font-bold
                                text-white!
                            "
                        >
                            <T text={consolidated ? "Continue" : quick ? "Choose pickup" : "View Cart"} /></p>

                    </div>

                </div>


                <div
                    className="
                        shrink-0
                        text-right
                    "
                >

                    <p
                        className="
                            font-bold
                            text-white!
                        "
                    >
                        {
                            formatCurrency(
                                total
                            )
                        }
                    </p>


                    <p
                        className="
                            text-xs
                            text-white/75!
                        "
                    >
                        →
                    </p>

                </div>

            <LinkFeedback /></Link>

        </div>
    );
}
