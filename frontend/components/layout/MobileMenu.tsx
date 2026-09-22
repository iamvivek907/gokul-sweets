"use client";

import Link from "next/link";

import {
    useEffect,
    useState
} from "react";

import {
    usePathname
} from "next/navigation";

import {
    useCart
} from "@/hooks/useCart";


/*
 * Replace these two URLs with your real social-media pages.
 */
const INSTAGRAM_URL =
    "https://www.instagram.com/_gokulsweets";

const FACEBOOK_URL =
    "https://www.facebook.com/visitgokulsweets";


interface MenuItem {

    label: string;

    href: string;

    description: string;

    disabled?: boolean;
}


const MENU_ITEMS: MenuItem[] = [
    {
        label: "Home",
        href: "/",
        description: "Go to home"
    },
    {
        label: "Menu",
        href: "/menu",
        description: "Browse sweets, snacks and food"
    },
    {
        label: "Cart",
        href: "/cart",
        description: "View your cart"
    },
    {
        label: "My Orders",
        href: "/orders",
        description: "Track active and past orders"
    },
    {
        label: "Offers & Rebates",
        href: "/offers",
        description: "View available offers",
        disabled: true
    },
    {
        label: "Profile",
        href: "/profile",
        description: "Manage your profile",
        disabled: true
    },
    {
        label: "About Gokul Sweets",
        href: "/about",
        description: "Know our story and business"
    }
];


export default function MobileMenu() {

    const pathname =
        usePathname();


    const {
        itemCount
    } =
        useCart();


    const [
        open,
        setOpen
    ] =
        useState(false);


    /*
     * =========================================================
     * LOCK BACKGROUND SCROLL
     * =========================================================
     */
    useEffect(
        () => {

            if (!open) {

                return;
            }


            const previousOverflow =
                document.body.style.overflow;


            document.body.style.overflow =
                "hidden";


            return () => {

                document.body.style.overflow =
                    previousOverflow;
            };

        },
        [
            open
        ]
    );


    /*
     * =========================================================
     * ESCAPE KEY
     * =========================================================
     */
    useEffect(
        () => {

            if (!open) {

                return;
            }


            function handleKeyDown(
                event: KeyboardEvent
            ) {

                if (
                    event.key ===
                    "Escape"
                ) {

                    setOpen(
                        false
                    );
                }
            }


            window.addEventListener(
                "keydown",
                handleKeyDown
            );


            return () => {

                window.removeEventListener(
                    "keydown",
                    handleKeyDown
                );
            };

        },
        [
            open
        ]
    );


    function closeMenu() {

        setOpen(
            false
        );
    }


    function toggleMenu() {

        setOpen(
            current =>
                !current
        );
    }


    return (

        <>
            <button
                type="button"
                aria-label={
                    open
                        ? "Close menu"
                        : "Open menu"
                }
                aria-expanded={
                    open
                }
                aria-controls="gokul-mobile-menu"
                onClick={
                    toggleMenu
                }
                className="
                    relative
                    flex
                    h-11
                    w-11
                    shrink-0
                    items-center
                    justify-center
                    rounded-full
                    border
                    border-[#eadfd6]
                    bg-white
                    shadow-sm
                    transition
                    hover:bg-[#fffaf3]
                    active:scale-95
                    focus-visible:outline-none
                    focus-visible:ring-2
                    focus-visible:ring-[#c88a20]
                    focus-visible:ring-offset-2
                "
            >

                <span
                    className="sr-only"
                >
                    Menu
                </span>


                <span
                    className="
                        flex
                        w-5
                        flex-col
                        gap-1.5
                    "
                    aria-hidden="true"
                >

                    <span
                        className={`
                            block
                            h-0.5
                            w-full
                            rounded-full
                            bg-[#241715]
                            transition
                            duration-200

                            ${
                                open
                                    ? "translate-y-2 rotate-45"
                                    : ""
                            }
                        `}
                    />


                    <span
                        className={`
                            block
                            h-0.5
                            w-full
                            rounded-full
                            bg-[#241715]
                            transition
                            duration-200

                            ${
                                open
                                    ? "opacity-0"
                                    : ""
                            }
                        `}
                    />


                    <span
                        className={`
                            block
                            h-0.5
                            w-full
                            rounded-full
                            bg-[#241715]
                            transition
                            duration-200

                            ${
                                open
                                    ? "-translate-y-2 -rotate-45"
                                    : ""
                            }
                        `}
                    />

                </span>


                {
                    itemCount > 0
                    && (

                        <span
                            className="
                                absolute
                                -right-1
                                -top-1
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
                                text-white
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

            </button>


            <div
                aria-hidden="true"
                onClick={
                    closeMenu
                }
                className={`
                    fixed
                    inset-0
                    z-40
                    bg-black/35
                    backdrop-blur-[1px]
                    transition-opacity
                    duration-200

                    ${
                        open
                            ? "pointer-events-auto opacity-100"
                            : "pointer-events-none opacity-0"
                    }
                `}
            />


            <aside
                id="gokul-mobile-menu"
                aria-hidden={
                    !open
                }
                className={`
                    fixed
                    right-0
                    top-0
                    z-50
                    flex
                    h-dvh
                    w-[min(88vw,380px)]
                    flex-col
                    border-l
                    border-[#eadfd6]
                    bg-[#fffaf3]
                    shadow-2xl
                    transition-transform
                    duration-300
                    ease-out

                    ${
                        open
                            ? "visible pointer-events-auto translate-x-0"
                            : "invisible pointer-events-none translate-x-full"
                    }
                `}
            >

                <div
                    className="
                        flex
                        items-center
                        justify-between
                        gap-4
                        border-b
                        border-[#eadfd6]
                        bg-white
                        px-5
                        py-4
                    "
                >

                    <div>

                        <p
                            className="
                                text-lg
                                font-extrabold
                                tracking-tight
                                text-[#7a1625]
                            "
                        >
                            Gokul Sweets
                        </p>


                        <p
                            className="
                                mt-0.5
                                text-xs
                                font-medium
                                uppercase
                                tracking-[0.16em]
                                text-[#c88a20]
                            "
                        >
                            Tradition tastes better
                        </p>

                    </div>


                    <button
                        type="button"
                        aria-label="Close menu"
                        onClick={
                            closeMenu
                        }
                        className="
                            flex
                            h-10
                            w-10
                            items-center
                            justify-center
                            rounded-xl
                            border
                            border-[#eadfd6]
                            bg-[#fffaf3]
                            text-xl
                            font-semibold
                            text-[#241715]
                            active:scale-95
                        "
                    >
                        ×
                    </button>

                </div>


                <nav
                    className="
                        flex-1
                        overflow-y-auto
                        px-4
                        py-5
                    "
                >

                    <div
                        className="space-y-2"
                    >

                        {
                            MENU_ITEMS.map(
                                item => {

                                    const active =
                                        item.href === "/"
                                            ? pathname === "/"
                                            : pathname.startsWith(
                                                item.href
                                            );


                                    if (
                                        item.disabled
                                    ) {

                                        return (

                                            <div
                                                key={
                                                    item.href
                                                }
                                                className="
                                                    flex
                                                    items-center
                                                    justify-between
                                                    gap-4
                                                    rounded-2xl
                                                    border
                                                    border-[#eadfd6]
                                                    bg-white/70
                                                    px-4
                                                    py-3.5
                                                    opacity-65
                                                "
                                            >

                                                <div
                                                    className="min-w-0"
                                                >

                                                    <p
                                                        className="
                                                            font-bold
                                                            text-[#241715]
                                                        "
                                                    >
                                                        {
                                                            item.label
                                                        }
                                                    </p>


                                                    <p
                                                        className="
                                                            mt-1
                                                            text-xs
                                                            leading-5
                                                            text-[#756763]
                                                        "
                                                    >
                                                        {
                                                            item.description
                                                        }
                                                    </p>

                                                </div>


                                                <span
                                                    className="
                                                        shrink-0
                                                        rounded-full
                                                        bg-[#f6dfad]
                                                        px-2.5
                                                        py-1
                                                        text-[10px]
                                                        font-bold
                                                        uppercase
                                                        tracking-wide
                                                        text-[#7a1625]
                                                    "
                                                >
                                                    Soon
                                                </span>

                                            </div>
                                        );
                                    }


                                    return (

                                        <Link
                                            key={
                                                item.href
                                            }
                                            href={
                                                item.href
                                            }
                                            onClick={
                                                closeMenu
                                            }
                                            className={`
                                                flex
                                                items-center
                                                justify-between
                                                gap-4
                                                rounded-2xl
                                                border
                                                px-4
                                                py-3.5
                                                transition

                                                ${
                                                    active
                                                        ? "border-[#d9a64d] bg-[#fff2d2]"
                                                        : "border-[#eadfd6] bg-white hover:border-[#d8c7bb]"
                                                }
                                            `}
                                        >

                                            <div
                                                className="min-w-0"
                                            >

                                                <p
                                                    className={`
                                                        font-bold

                                                        ${
                                                            active
                                                                ? "text-[#7a1625]"
                                                                : "text-[#241715]"
                                                        }
                                                    `}
                                                >
                                                    {
                                                        item.label
                                                    }
                                                </p>


                                                <p
                                                    className="
                                                        mt-1
                                                        text-xs
                                                        leading-5
                                                        text-[#756763]
                                                    "
                                                >
                                                    {
                                                        item.description
                                                    }
                                                </p>

                                            </div>


                                            <div
                                                className="
                                                    flex
                                                    shrink-0
                                                    items-center
                                                    gap-2
                                                "
                                            >

                                                {
                                                    item.href ===
                                                    "/cart"
                                                    &&
                                                    itemCount > 0
                                                    && (

                                                        <span
                                                            className="
                                                                flex
                                                                h-6
                                                                min-w-6
                                                                items-center
                                                                justify-center
                                                                rounded-full
                                                                bg-[#7a1625]
                                                                px-1.5
                                                                text-xs
                                                                font-bold
                                                                text-white
                                                            "
                                                        >
                                                            {
                                                                itemCount
                                                            }
                                                        </span>

                                                    )
                                                }


                                                <span
                                                    aria-hidden="true"
                                                    className="
                                                        text-lg
                                                        text-[#c88a20]
                                                    "
                                                >
                                                    ›
                                                </span>

                                            </div>

                                        </Link>
                                    );
                                }
                            )
                        }

                    </div>


                    <div
                        className="
                            mt-6
                            rounded-2xl
                            border
                            border-[#eadfd6]
                            bg-white
                            p-4
                        "
                    >

                        <p
                            className="
                                text-xs
                                font-bold
                                uppercase
                                tracking-wide
                                text-[#c88a20]
                            "
                        >
                            Follow us
                        </p>


                        <p
                            className="
                                mt-1
                                text-sm
                                leading-5
                                text-[#756763]
                            "
                        >
                            New sweets, festive specials and
                            shop updates.
                        </p>


                        <div
                            className="
                                mt-4
                                grid
                                grid-cols-2
                                gap-2
                            "
                        >

                            <a
                                href={
                                    INSTAGRAM_URL
                                }
                                target="_blank"
                                rel="noreferrer"
                                className="
                                    flex
                                    min-h-11
                                    items-center
                                    justify-center
                                    rounded-xl
                                    border
                                    border-[#eadfd6]
                                    bg-[#fffaf3]
                                    text-sm
                                    font-bold
                                    text-[#7a1625]
                                    transition
                                    hover:bg-[#fff2d2]
                                "
                            >
                                Instagram
                            </a>


                            <a
                                href={
                                    FACEBOOK_URL
                                }
                                target="_blank"
                                rel="noreferrer"
                                className="
                                    flex
                                    min-h-11
                                    items-center
                                    justify-center
                                    rounded-xl
                                    border
                                    border-[#eadfd6]
                                    bg-[#fffaf3]
                                    text-sm
                                    font-bold
                                    text-[#7a1625]
                                    transition
                                    hover:bg-[#fff2d2]
                                "
                            >
                                Facebook
                            </a>

                        </div>

                    </div>

                </nav>


                <div
                    className="
                        border-t
                        border-[#eadfd6]
                        bg-white
                        px-5
                        py-4
                    "
                >

                    <p
                        className="
                            text-xs
                            leading-5
                            text-[#756763]
                        "
                    >
                        Online orders are prepared for pickup
                        from your selected branch.
                    </p>

                </div>

            </aside>

        </>
    );
}
