"use client";

import MobileMenu
    from "@/components/layout/MobileMenu";


export default function Header() {

    return (
        <header
            className="
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

                <div
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

                </div>


                <MobileMenu />

            </div>

        </header>
    );
}
