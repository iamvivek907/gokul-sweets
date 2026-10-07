"use client";
import {useEffect,useRef,useState} from "react";
import {useTranslation} from "@/lib/language";
import LinkFeedback from "@/components/common/LinkFeedback";


import Link
    from "next/link";

import {
    usePathname
} from "next/navigation";

import {
    useCart
} from "@/hooks/useCart";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {usePhoneViewport} from "@/hooks/usePhoneViewport";


interface NavigationItem {

    label: string;

    href: string;

    icon:
        "home"
        | "menu"
        | "cart"
        | "orders"
        | "profile";
}


const items: NavigationItem[] = [
    {
        label:
            "Home",
        href:
            "/",
        icon:
            "home"
    },
    {
        label:
            "Menu",
        href:
            "/menu",
        icon:
            "menu"
    },
    {
        label:
            "Cart",
        href:
            "/cart",
        icon:
            "cart"
    },
    {
        label:
            "Orders",
        href:
            "/orders",
        icon:
            "orders"
    },
    {
        label:
            "Profile",
        href:
            "/profile",
        icon:
            "profile"
    }
];


export default function BottomNavigation() {
    const translate = useTranslation();

    const pathname =
        usePathname();
    const features = useStorefrontFeatures();
    const checkoutExperienceV2 = features?.checkoutExperienceV2 === true;
    const navRef=useRef<HTMLElement>(null);
    const [scrollHidden,setScrollHidden]=useState(false);
    const modern=features?.futuristicStorefrontV2===true||checkoutExperienceV2;
    const phone = usePhoneViewport();
    // Use the same phone destinations and icons throughout the enabled customer app.
    const referenceMenu = modern && phone === true;
    const visibleItems = referenceMenu ? items.filter(item => item.icon !== "cart") : items;
    useEffect(()=>{
        if(!modern||checkoutExperienceV2&&pathname.startsWith("/checkout/"))return;
        let active=true,lastY=window.scrollY;
        let timer:ReturnType<typeof setTimeout>|undefined;
        const show=()=>{clearTimeout(timer);if(active)setScrollHidden(false);};
        queueMicrotask(show);
        const scroll=()=>{
            const next=window.scrollY,delta=Math.abs(next-lastY);lastY=next;
            if(!window.matchMedia("(max-width:640px)").matches||window.matchMedia("(prefers-reduced-motion:reduce)").matches||next<24||(navRef.current?.contains(document.activeElement)&&document.activeElement?.matches(":focus-visible"))||document.querySelector("dialog[open]")){show();return;}
            if(delta<2)return;
            setScrollHidden(true);clearTimeout(timer);timer=setTimeout(show,450);
        };
        const keyboard=(event:KeyboardEvent)=>{if(event.key==="Tab"||event.key==="Escape")show();};
        window.addEventListener("scroll",scroll,{passive:true});
        window.addEventListener("keydown",keyboard);
        navRef.current?.addEventListener("focusin",show);
        const nav=navRef.current;
        return()=>{active=false;clearTimeout(timer);window.removeEventListener("scroll",scroll);window.removeEventListener("keydown",keyboard);nav?.removeEventListener("focusin",show);};
    },[pathname,modern,checkoutExperienceV2]);

    const selectedBranch = useSelectedBranch().branch;
    const homeHref = features?.branchExperience === true
        ? selectedBranch ? `/branches/${selectedBranch.id}` : "/branches"
        : "/";


    const {
        itemCount
    } =
        useCart();


    function isActive(
        href: string
    ) {

        if (
            href === "/"
        ) {

            return pathname === homeHref;
        }


        return pathname.startsWith(
            href
        );
    }


    if (checkoutExperienceV2 && pathname.startsWith("/checkout/")) return null;

    return (
        <nav
            ref={navRef}
            data-scroll-hidden={scrollHidden}
            data-reference-menu={referenceMenu || undefined}
            aria-label="Primary navigation"
            className="customer-bottom-navigation
                fixed
                inset-x-0
                bottom-0
                z-50
                w-full
                max-w-full
                overflow-x-clip
                border-t
                border-[#eadfd6]
                bg-white/95
                shadow-[0_-8px_30px_rgba(60,30,20,0.06)]
                backdrop-blur-xl
            "
        >

            <div
                className="
                    mx-auto
                    grid
                    w-full
                    max-w-[600px]
                    px-1
                    pb-[env(safe-area-inset-bottom)]
                "
                style={{gridTemplateColumns: `repeat(${visibleItems.length}, minmax(0, 1fr))`}}
            >

                {
                    visibleItems.map(
                        item => {

                            const active =
                                isActive(
                                    item.href
                                );


                            return (
                                <Link
                                    key={
                                        item.icon === "home" ? homeHref : item.href
                                    }
                                    href={
                                        item.icon === "home" ? homeHref : item.href
                                    }
                                    data-nav-icon={item.icon}
                                    aria-current={
                                        active
                                            ? "page"
                                            : undefined
                                    }
                                    className={`
                                        relative
                                        flex
                                        min-w-0
                                        min-h-[64px]
                                        flex-col
                                        items-center
                                        justify-center
                                        gap-1
                                        rounded-xl
                                        px-1
                                        py-2
                                        text-[11px]
                                        font-medium
                                        transition

                                        active:scale-[0.96]

                                        ${
                                            active
                                                ? "text-[#7a1625]!"
                                                : "text-[#756763]!"
                                        }
                                    `}
                                >

                                    <span
                                        className={`
                                            relative
                                            flex
                                            h-9
                                            w-9
                                            shrink-0
                                            items-center
                                            justify-center
                                            rounded-xl
                                            transition-colors

                                            ${
                                                active
                                                    ? "bg-[#fff0dc]"
                                                    : "bg-transparent"
                                            }
                                        `}
                                    >

                                        <NavigationIcon
                                            icon={
                                                item.icon
                                            }
                                            active={
                                                active
                                            }
                                            referenceMenu={referenceMenu}
                                        />


                                        {
                                            item.icon === "cart"
                                            &&
                                            itemCount > 0
                                            && (
                                                <span
                                                    className="
                                                        absolute
                                                        -right-1.5
                                                        -top-1.5
                                                        flex
                                                        h-5
                                                        min-w-5
                                                        items-center
                                                        justify-center
                                                        rounded-full
                                                        bg-[#7a1625]
                                                        px-1
                                                        text-[10px]
                                                        font-bold
                                                        leading-none
                                                        text-white!
                                                        ring-2
                                                        ring-white
                                                    "
                                                >
                                                    {
                                                        itemCount > 99
                                                            ? "99+"
                                                            : itemCount
                                                    }
                                                </span>
                                            )
                                        }

                                    </span>


                                    <span
                                        className="
                                            max-w-full
                                            whitespace-normal
                                            text-center
                                            leading-tight
                                        "
                                    >
                                        {translate(item.label)}
                                    </span>

                                <LinkFeedback href={item.icon === "home" ? homeHref : item.href} /></Link>
                            );
                        }
                    )
                }

            </div>

        </nav>
    );
}


function NavigationIcon({
    icon,
    active,
    referenceMenu = false
}: {
    icon: NavigationItem["icon"];
    active: boolean;
    referenceMenu?: boolean;
}) {

    const className =
        `
            h-5
            w-5
            ${active ? "stroke-[#7a1625]" : "stroke-[#756763]"}
        `;


    if (
        icon === "home"
    ) {

        return (
            <svg
                viewBox="0 0 24 24"
                fill="none"
                aria-hidden="true"
                className={className}
            >
                <path
                    d="M3.5 10.7 12 3.8l8.5 6.9v8.1a1.7 1.7 0 0 1-1.7 1.7H5.2a1.7 1.7 0 0 1-1.7-1.7v-8.1Z"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                />
                <path
                    d="M9.2 20.5v-6h5.6v6"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                />
            </svg>
        );
    }


    if (icon === "menu") {
        if (referenceMenu) return <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" className={className} strokeWidth="1.7" strokeLinejoin="round">
            <rect x="3" y="3" width="7" height="7" rx="1.6" />
            <rect x="14" y="3" width="7" height="7" rx="1.6" />
            <rect x="3" y="14" width="7" height="7" rx="1.6" />
            <rect x="14" y="14" width="7" height="7" rx="1.6" />
        </svg>;
        return <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" className={className} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="5.5" />
            <circle cx="12" cy="12" r="3" />
            <path d="M2 4v5h3V4M3.5 4v16M21 4v16M21 4c-3 3-3 7 0 7" />
        </svg>;
    }

    if (icon === "cart") {
        return <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" className={className} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <path d="M5 7h14l1 12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2L5 7ZM9 7V5a3 3 0 0 1 6 0v2" />
            <path d="M8.5 14a3.5 3.5 0 0 1 7 0h-7ZM8.5 17h7" />
        </svg>;
    }


    if (
        icon === "orders"
    ) {

        if (referenceMenu) return <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" className={className} strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
            <path d="M9 4H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2h-3" />
            <rect x="9" y="2" width="6" height="4" rx="1.4" />
            <path d="M8 10h8M8 14h8M8 18h5" />
        </svg>;

        return (
            <svg
                viewBox="0 0 24 24"
                fill="none"
                aria-hidden="true"
                className={className}
            >
                <path
                    d="M6 3.5h12v17l-2-1.2-2 1.2-2-1.2-2 1.2-2-1.2-2 1.2v-17Z"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                />
                <path
                    d="M9 8h6M9 12h6M9 16h4"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                />
            </svg>
        );
    }


    return (
        <svg
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
            className={className}
        >
            <circle
                cx="12"
                cy="8"
                r="3.2"
                strokeWidth="1.8"
            />
            <path
                d="M5.5 20c.7-4 3-6 6.5-6s5.8 2 6.5 6"
                strokeWidth="1.8"
                strokeLinecap="round"
            />
        </svg>
    );
}
