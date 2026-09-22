"use client";

import Link
    from "next/link";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";


export default function AdminMenuPage() {

    const {
        profile
    } =
        useAdminAuth();


    return (
        <div
            className="
                px-4
                py-6

                sm:px-6
                sm:py-8

                lg:px-8
            "
        >

            <div
                className="
                    mx-auto
                    max-w-7xl
                "
            >

                <section
                    className="
                        rounded-3xl
                        border
                        border-[#eadfd6]
                        bg-white
                        p-5
                        shadow-sm

                        sm:p-7
                    "
                >

                    <p
                        className="
                            text-xs
                            font-semibold
                            uppercase
                            tracking-[0.16em]
                            text-[#c88a20]
                        "
                    >
                        Menu Management
                    </p>


                    <h1
                        className="
                            mt-2
                            text-3xl
                            font-bold
                            tracking-tight
                            text-[#241715]

                            sm:text-4xl
                        "
                    >
                        Manage Menu
                    </h1>


                    <p
                        className="
                            mt-3
                            max-w-3xl
                            text-sm
                            leading-6
                            text-[#756763]
                        "
                    >
                        Manage daily menu availability, branch pricing,
                        product images and bulk menu updates from one place.
                    </p>


                    <div
                        className="
                            mt-5
                            inline-flex
                            rounded-xl
                            border
                            border-[#eadfd6]
                            bg-[#fffaf3]
                            px-4
                            py-3
                        "
                    >

                        <div>

                            <p
                                className="
                                    text-xs
                                    font-semibold
                                    uppercase
                                    tracking-wide
                                    text-[#756763]
                                "
                            >
                                Logged in as
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-sm
                                    font-bold
                                    text-[#7a1625]
                                "
                            >
                                {
                                    profile?.roleName
                                }
                            </p>

                        </div>

                    </div>

                </section>


                <section
                    className="
                        mt-6
                    "
                >

                    <div>

                        <h2
                            className="
                                text-xl
                                font-bold
                                text-[#241715]
                            "
                        >
                            Menu tools
                        </h2>


                        <p
                            className="
                                mt-1
                                text-sm
                                text-[#756763]
                            "
                        >
                            Choose how you want to manage the menu.
                        </p>

                    </div>


                    <div
                        className="
                            mt-4
                            grid
                            gap-4

                            md:grid-cols-2
                        "
                    >

                        {/* Live Menu */}

                        <Link
                            href="/admin/menu/live"
                            className="
                                group
                                flex
                                min-h-56
                                flex-col
                                justify-between
                                rounded-2xl
                                border
                                border-[#eadfd6]
                                bg-white
                                p-6
                                transition

                                hover:-translate-y-0.5
                                hover:border-[#d9c5b8]
                                hover:shadow-md

                                focus-visible:outline-none
                                focus-visible:ring-4
                                focus-visible:ring-[#c88a20]/20
                            "
                        >

                            <div>

                                <div
                                    className="
                                        inline-flex
                                        rounded-full
                                        bg-[#fff1e9]
                                        px-3
                                        py-1
                                        text-xs
                                        font-bold
                                        uppercase
                                        tracking-wide
                                        text-[#7a1625]
                                    "
                                >
                                    Daily operations
                                </div>


                                <h3
                                    className="
                                        mt-4
                                        text-2xl
                                        font-bold
                                        text-[#241715]
                                    "
                                >
                                    Live Menu
                                </h3>


                                <p
                                    className="
                                        mt-3
                                        text-sm
                                        leading-6
                                        text-[#756763]
                                    "
                                >
                                    Quickly update item availability,
                                    branch pricing and display order
                                    without uploading Excel.
                                </p>

                            </div>


                            <div
                                className="
                                    mt-6
                                    flex
                                    items-center
                                    justify-between
                                "
                            >

                                <span
                                    className="
                                        text-sm
                                        font-semibold
                                        text-[#7a1625]
                                    "
                                >
                                    Manage live menu
                                </span>


                                <span
                                    aria-hidden="true"
                                    className="
                                        text-xl
                                        text-[#c88a20]
                                        transition

                                        group-hover:translate-x-1
                                    "
                                >
                                    →
                                </span>

                            </div>

                        </Link>


                        {/* Excel Import */}

                        <Link
                            href="/admin/menu/import"
                            className="
                                group
                                flex
                                min-h-56
                                flex-col
                                justify-between
                                rounded-2xl
                                border
                                border-[#eadfd6]
                                bg-white
                                p-6
                                transition

                                hover:-translate-y-0.5
                                hover:border-[#d9c5b8]
                                hover:shadow-md

                                focus-visible:outline-none
                                focus-visible:ring-4
                                focus-visible:ring-[#c88a20]/20
                            "
                        >

                            <div>

                                <div
                                    className="
                                        inline-flex
                                        rounded-full
                                        bg-[#fff8e7]
                                        px-3
                                        py-1
                                        text-xs
                                        font-bold
                                        uppercase
                                        tracking-wide
                                        text-[#c88a20]
                                    "
                                >
                                    Bulk update
                                </div>


                                <h3
                                    className="
                                        mt-4
                                        text-2xl
                                        font-bold
                                        text-[#241715]
                                    "
                                >
                                    Excel Import / Update
                                </h3>


                                <p
                                    className="
                                        mt-3
                                        text-sm
                                        leading-6
                                        text-[#756763]
                                    "
                                >
                                    Download the menu template,
                                    validate your spreadsheet and
                                    import menu changes safely.
                                </p>

                            </div>


                            <div
                                className="
                                    mt-6
                                    flex
                                    items-center
                                    justify-between
                                "
                            >

                                <span
                                    className="
                                        text-sm
                                        font-semibold
                                        text-[#7a1625]
                                    "
                                >
                                    Open import tools
                                </span>


                                <span
                                    aria-hidden="true"
                                    className="
                                        text-xl
                                        text-[#c88a20]
                                        transition

                                        group-hover:translate-x-1
                                    "
                                >
                                    →
                                </span>

                            </div>

                        </Link>


                        {/* Image Management */}

                        <Link
                            href="/admin/menu/images"
                            className="
                                group
                                flex
                                min-h-56
                                flex-col
                                justify-between
                                rounded-2xl
                                border
                                border-[#eadfd6]
                                bg-white
                                p-6
                                transition

                                hover:-translate-y-0.5
                                hover:border-[#d9c5b8]
                                hover:shadow-md

                                focus-visible:outline-none
                                focus-visible:ring-4
                                focus-visible:ring-[#c88a20]/20
                            "
                        >

                            <div>

                                <div
                                    className="
                                        inline-flex
                                        rounded-full
                                        bg-[#fff4e6]
                                        px-3
                                        py-1
                                        text-xs
                                        font-bold
                                        uppercase
                                        tracking-wide
                                        text-[#b87900]
                                    "
                                >
                                    Product images
                                </div>


                                <h3
                                    className="
                                        mt-4
                                        text-2xl
                                        font-bold
                                        text-[#241715]
                                    "
                                >
                                    Image Management
                                </h3>


                                <p
                                    className="
                                        mt-3
                                        text-sm
                                        leading-6
                                        text-[#756763]
                                    "
                                >
                                    Upload, replace or remove product
                                    images for your menu items.
                                </p>

                            </div>


                            <div
                                className="
                                    mt-6
                                    flex
                                    items-center
                                    justify-between
                                "
                            >

                                <span
                                    className="
                                        text-sm
                                        font-semibold
                                        text-[#7a1625]
                                    "
                                >
                                    Manage product images
                                </span>


                                <span
                                    aria-hidden="true"
                                    className="
                                        text-xl
                                        text-[#c88a20]
                                        transition

                                        group-hover:translate-x-1
                                    "
                                >
                                    →
                                </span>

                            </div>

                        </Link>

                    </div>

                </section>


                <section
                    className="
                        mt-6
                        rounded-2xl
                        border
                        border-[#eadfd6]
                        bg-[#fffaf3]
                        p-5
                    "
                >

                    <h2
                        className="
                            text-base
                            font-bold
                            text-[#241715]
                        "
                    >
                        How to use this section
                    </h2>


                    <p
                        className="
                            mt-2
                            max-w-4xl
                            text-sm
                            leading-6
                            text-[#756763]
                        "
                    >
                        Use Live Menu for normal day-to-day changes such
                        as marking an item unavailable. Use Image Management
                        to upload or replace product photos. Use Excel Import
                        when you need to add many products, categories or
                        pricing changes together.
                    </p>

                </section>

            </div>

        </div>
    );
}