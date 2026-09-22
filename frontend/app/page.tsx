import Link from "next/link";

import AppShell from "@/components/layout/AppShell";
import BranchSelector from "@/components/branch/BranchSelector";
import InstallAppBanner from "@/components/pwa/InstallAppBanner";
import CustomerHomeExperience from "@/components/menu/CustomerHomeExperience";


const MENU_HIGHLIGHTS = [
    {
        title:
            "Traditional Sweets",
        description:
            "Fresh favourites for celebrations, gifting and everyday cravings.",
        eyebrow:
            "Made fresh"
    },
    {
        title:
            "Snacks & Fast Food",
        description:
            "Quick bites and savoury favourites prepared for convenient pickup.",
        eyebrow:
            "Quick bites"
    },
    {
        title:
            "Meals",
        description:
            "Browse lunch and dinner options available at your selected branch.",
        eyebrow:
            "For your meal"
    },
    {
        title:
            "Drinks & Packaged Items",
        description:
            "Cold drinks, biscuits, namkeen and other convenient add-ons.",
        eyebrow:
            "Complete your order"
    }
];


const PICKUP_STEPS = [
    {
        number:
            "01",
        title:
            "Choose your branch",
        description:
            "Select where you want to collect your order."
    },
    {
        number:
            "02",
        title:
            "Pick your favourites",
        description:
            "Browse the live menu, prices and availability."
    },
    {
        number:
            "03",
        title:
            "Select a pickup time",
        description:
            "Choose a convenient available slot and pay online."
    }
];


export default function Home() {
    return <CustomerHomeExperience fallback={<LegacyHome />} />;
}

function LegacyHome() {

    return (
        <AppShell>

            <section>

                <BranchSelector />

                <InstallAppBanner />

            </section>


            <section
                className="
                    mt-6
                "
            >

                <div
                    className="
                        relative
                        overflow-hidden
                        rounded-2rem
                        bg-[#5d0f1b]
                        px-6
                        py-8
                        text-white
                        shadow-[0_24px_70px_rgba(93,15,27,0.18)]
                        sm:px-8
                        sm:py-10
                        lg:px-12
                        lg:py-14
                    "
                >

                    <div
                        aria-hidden="true"
                        className="
                            absolute
                            -right-20
                            -top-24
                            h-64
                            w-64
                            rounded-full
                            bg-[#c88a20]/20
                            blur-3xl
                        "
                    />


                    <div
                        aria-hidden="true"
                        className="
                            absolute
                            -bottom-28
                            -left-16
                            h-64
                            w-64
                            rounded-full
                            bg-white/10
                            blur-3xl
                        "
                    />


                    <div
                        className="
                            relative
                            max-w-3xl
                        "
                    >

                        <div
                            className="
                                inline-flex
                                items-center
                                rounded-full
                                border
                                border-[#f6dfad]/30
                                bg-white/10
                                px-3
                                py-1.5
                                text-xs
                                font-semibold
                                uppercase
                                tracking-[0.16em]
                                text-[#f6dfad]
                                backdrop-blur
                            "
                        >
                            Freshly prepared · Pickup only
                        </div>


                        <h1
                            className="
                                mt-5
                                max-w-2xl
                                text-4xl
                                font-bold
                                leading-[1.06]
                                tracking-tight
                                sm:text-5xl
                                lg:text-6xl
                            "
                        >
                            Your favourites,
                            ready when you are.
                        </h1>


                        <p
                            className="
                                mt-5
                                max-w-2xl
                                text-sm
                                leading-7
                                text-white/80
                                sm:text-base
                            "
                        >
                            Order sweets, snacks and meals from your selected
                            branch, choose a convenient pickup slot and collect
                            without waiting.
                        </p>


                        <div
                            className="
                                mt-7
                                flex
                                flex-col
                                gap-3
                                sm:flex-row
                            "
                        >

                            <Link
                                href="/menu"
                                className="
                                    inline-flex
                                    min-h-12
                                    items-center
                                    justify-center
                                    rounded-xl
                                    bg-[#f6dfad]
                                    px-6
                                    text-sm
                                    font-bold
                                    text-[#5d0f1b]!
                                    shadow-sm
                                    transition

                                    hover:bg-[#f2d28d]
                                    active:scale-[0.98]
                                "
                            >
                                Explore Menu
                            </Link>


                            <a
                                href="#how-pickup-works"
                                className="
                                    inline-flex
                                    min-h-12
                                    items-center
                                    justify-center
                                    rounded-xl
                                    border
                                    border-white/20
                                    bg-white/10
                                    px-6
                                    text-sm
                                    font-semibold
                                    text-white!
                                    backdrop-blur
                                    transition

                                    hover:bg-white/15
                                    active:scale-[0.98]
                                "
                            >
                                How pickup works
                            </a>

                        </div>


                        <div
                            className="
                                mt-8
                                grid
                                gap-3
                                border-t
                                border-white/10
                                pt-6
                                text-sm
                                sm:grid-cols-3
                            "
                        >

                            <HeroPoint
                                title="Branch-specific menu"
                                description="Live prices and availability"
                            />

                            <HeroPoint
                                title="Convenient pickup"
                                description="Choose an available slot"
                            />

                            <HeroPoint
                                title="Simple checkout"
                                description="Pay online and collect"
                            />

                        </div>

                    </div>

                </div>

            </section>


            <section
                className="
                    mt-10
                "
            >

                <div
                    className="
                        flex
                        items-end
                        justify-between
                        gap-4
                    "
                >

                    <div>

                        <p
                            className="
                                text-xs
                                font-semibold
                                uppercase
                                tracking-[0.16em]
                                text-[#c88a20]
                            "
                        >
                            Explore Gokul Sweets
                        </p>


                        <h2
                            className="
                                mt-2
                                text-2xl
                                font-bold
                                tracking-tight
                                text-[#241715]
                                sm:text-3xl
                            "
                        >
                            Something for every craving
                        </h2>

                    </div>


                    <Link
                        href="/menu"
                        className="
                            hidden
                            text-sm
                            font-semibold
                            text-[#7a1625]
                            transition
                            hover:underline
                            sm:inline
                        "
                    >
                        View full menu
                    </Link>

                </div>


                <div
                    className="
                        mt-5
                        grid
                        gap-4
                        sm:grid-cols-2
                    "
                >

                    {
                        MENU_HIGHLIGHTS.map(
                            item => (

                                <Link
                                    key={
                                        item.title
                                    }
                                    href="/menu"
                                    className="
                                        group
                                        rounded-2xl
                                        border
                                        border-[#eadfd6]
                                        bg-white
                                        p-5
                                        shadow-sm
                                        transition

                                        hover:-translate-y-0.5
                                        hover:border-[#dfc7b8]
                                        hover:shadow-md
                                    "
                                >

                                    <p
                                        className="
                                            text-xs
                                            font-semibold
                                            uppercase
                                            tracking-[0.12em]
                                            text-[#c88a20]
                                        "
                                    >
                                        {item.eyebrow}
                                    </p>


                                    <div
                                        className="
                                            mt-3
                                            flex
                                            items-start
                                            justify-between
                                            gap-4
                                        "
                                    >

                                        <div>

                                            <h3
                                                className="
                                                    text-lg
                                                    font-bold
                                                    text-[#241715]
                                                "
                                            >
                                                {item.title}
                                            </h3>


                                            <p
                                                className="
                                                    mt-2
                                                    max-w-md
                                                    text-sm
                                                    leading-6
                                                    text-[#756763]
                                                "
                                            >
                                                {item.description}
                                            </p>

                                        </div>


                                        <span
                                            aria-hidden="true"
                                            className="
                                                flex
                                                h-10
                                                w-10
                                                shrink-0
                                                items-center
                                                justify-center
                                                rounded-full
                                                bg-[#fff0dc]
                                                text-lg
                                                font-semibold
                                                text-[#7a1625]
                                                transition

                                                group-hover:bg-[#f6dfad]
                                            "
                                        >
                                            →
                                        </span>

                                    </div>

                                </Link>

                            )
                        )
                    }

                </div>


                <Link
                    href="/menu"
                    className="
                        mt-4
                        inline-flex
                        min-h-11
                        w-full
                        items-center
                        justify-center
                        rounded-xl
                        border
                        border-[#eadfd6]
                        bg-white
                        px-4
                        text-sm
                        font-semibold
                        text-[#7a1625]
                        sm:hidden
                    "
                >
                    View full menu
                </Link>

            </section>


            <section
                id="how-pickup-works"
                className="
                    mt-12
                    scroll-mt-24
                    rounded-3xl
                    border
                    border-[#eadfd6]
                    bg-[#fffaf3]
                    p-5
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
                    Easy pickup
                </p>


                <h2
                    className="
                        mt-2
                        text-2xl
                        font-bold
                        tracking-tight
                        text-[#241715]
                        sm:text-3xl
                    "
                >
                    From menu to pickup in three simple steps
                </h2>


                <div
                    className="
                        mt-6
                        grid
                        gap-4
                        md:grid-cols-3
                    "
                >

                    {
                        PICKUP_STEPS.map(
                            step => (

                                <article
                                    key={
                                        step.number
                                    }
                                    className="
                                        rounded-2xl
                                        border
                                        border-[#eadfd6]
                                        bg-white
                                        p-5
                                    "
                                >

                                    <span
                                        className="
                                            inline-flex
                                            h-9
                                            min-w-9
                                            items-center
                                            justify-center
                                            rounded-lg
                                            bg-[#7a1625]
                                            px-2
                                            text-xs
                                            font-bold
                                            text-white
                                        "
                                    >
                                        {step.number}
                                    </span>


                                    <h3
                                        className="
                                            mt-4
                                            font-bold
                                            text-[#241715]
                                        "
                                    >
                                        {step.title}
                                    </h3>


                                    <p
                                        className="
                                            mt-2
                                            text-sm
                                            leading-6
                                            text-[#756763]
                                        "
                                    >
                                        {step.description}
                                    </p>

                                </article>

                            )
                        )
                    }

                </div>

            </section>


            <section
                className="
                    mt-10
                    rounded-3xl
                    bg-white
                    p-6
                    text-center
                    shadow-sm
                    ring-1
                    ring-[#eadfd6]
                    sm:p-8
                "
            >

                <p
                    className="
                        text-sm
                        font-semibold
                        text-[#c88a20]
                    "
                >
                    Ready to order?
                </p>


                <h2
                    className="
                        mt-2
                        text-2xl
                        font-bold
                        text-[#241715]
                    "
                >
                    See what&apos;s available at your branch
                </h2>


                <p
                    className="
                        mx-auto
                        mt-3
                        max-w-xl
                        text-sm
                        leading-6
                        text-[#756763]
                    "
                >
                    The menu uses your selected branch to show the relevant
                    products, prices and availability.
                </p>


                <Link
                    href="/menu"
                    className="
                        mt-6
                        inline-flex
                        min-h-12
                        items-center
                        justify-center
                        rounded-xl
                        bg-[#7a1625]
                        px-6
                        text-sm
                        font-bold
                        text-white!
                        transition

                        hover:bg-[#5d0f1b]
                        active:scale-[0.98]
                    "
                >
                    Browse Menu
                </Link>

            </section>

        </AppShell>
    );
}


function HeroPoint({
    title,
    description
}: {
    title: string;
    description: string;
}) {

    return (
        <div>

            <p
                className="
                    font-semibold
                    text-white
                "
            >
                {title}
            </p>


            <p
                className="
                    mt-1
                    text-xs
                    leading-5
                    text-white/60
                "
            >
                {description}
            </p>

        </div>
    );
}
