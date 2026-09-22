import type {
    ReactNode
} from "react";


interface PolicySectionProps {
    id: string;
    icon: ReactNode;
    title: string;
    description: string;
    children: ReactNode;
}


const POLICY_LINKS = [
    {
        id: "pickup-policy",
        label: "Pickup Policy"
    },
    {
        id: "cancellation-policy",
        label: "Cancellation Policy"
    },
    {
        id: "refund-policy",
        label: "Refund Policy"
    },
    {
        id: "privacy-policy",
        label: "Privacy Policy"
    },
    {
        id: "terms-and-conditions",
        label: "Terms and Conditions"
    }
];


function PickupIcon() {

    return (
        <svg
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
            className="h-7 w-7"
        >
            <path
                d="M4 10h16v10H4V10Z"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinejoin="round"
            />

            <path
                d="M3 10 5.5 4h13L21 10"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinejoin="round"
            />

            <path
                d="M9 20v-6h6v6"
                stroke="currentColor"
                strokeWidth="1.8"
            />
        </svg>
    );
}


function CalendarIcon() {

    return (
        <svg
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
            className="h-7 w-7"
        >
            <rect
                x="3"
                y="5"
                width="18"
                height="16"
                rx="2"
                stroke="currentColor"
                strokeWidth="1.8"
            />

            <path
                d="M7 3v4M17 3v4M3 10h18"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
            />
        </svg>
    );
}


function ShieldIcon() {

    return (
        <svg
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
            className="h-7 w-7"
        >
            <path
                d="M12 3 20 6v5c0 5.2-3.4 8.6-8 10-4.6-1.4-8-4.8-8-10V6l8-3Z"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinejoin="round"
            />

            <path
                d="m9 12 2 2 4-5"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
        </svg>
    );
}


function DocumentIcon() {

    return (
        <svg
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
            className="h-7 w-7"
        >
            <path
                d="M6 3h8l4 4v14H6V3Z"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinejoin="round"
            />

            <path
                d="M14 3v5h5M9 13h6M9 17h6"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
            />
        </svg>
    );
}


function PolicySection({
    id,
    icon,
    title,
    description,
    children
}: PolicySectionProps) {

    return (
        <section
            id={id}
            className="
                scroll-mt-28
                rounded-3xl
                border
                border-[#eadfd6]
                bg-white
                p-5
                shadow-[0_4px_18px_rgba(60,30,20,0.05)]
                sm:p-7
            "
        >
            <div
                className="
                    flex
                    items-start
                    gap-4
                "
            >
                <div
                    className="
                        flex
                        h-12
                        w-12
                        shrink-0
                        items-center
                        justify-center
                        rounded-2xl
                        bg-[#7a1625]/8
                        text-[#7a1625]
                    "
                >
                    {icon}
                </div>

                <div className="min-w-0">

                    <h3
                        className="
                            text-xl
                            font-extrabold
                            tracking-tight
                            text-[#241715]
                        "
                    >
                        {title}
                    </h3>

                    <p
                        className="
                            mt-1
                            text-sm
                            leading-6
                            text-[#756763]
                        "
                    >
                        {description}
                    </p>

                </div>
            </div>

            <div
                className="
                    mt-5
                    space-y-3
                    text-sm
                    leading-7
                    text-[#5f514d]
                    sm:pl-16
                "
            >
                {children}
            </div>
        </section>
    );
}


function PolicyPoint({
    title,
    children,
    important = false
}: {
    title: string;
    children: ReactNode;
    important?: boolean;
}) {

    return (
        <div
            className={
                important
                    ? `
                        rounded-2xl
                        border
                        border-[#e8c67a]
                        bg-[#fff8e8]
                        px-4
                        py-3
                    `
                    : `
                        flex
                        items-start
                        gap-3
                    `
            }
        >
            {
                !important
                && (
                    <span
                        aria-hidden="true"
                        className="
                            mt-[11px]
                            h-1.5
                            w-1.5
                            shrink-0
                            rounded-full
                            bg-[#7a1625]
                        "
                    />
                )
            }

            <p>
                <strong className="text-[#241715]">
                    {title}:
                </strong>{" "}

                {children}
            </p>
        </div>
    );
}


export default function PolicyCenter() {

    return (
        <section
            id="policies"
            className="
                mt-8
                scroll-mt-24
            "
        >
            <div
                className="
                    rounded-3xl
                    border
                    border-[#eadfd6]
                    bg-white
                    px-5
                    py-7
                    sm:px-8
                "
            >
                <p
                    className="
                        text-xs
                        font-bold
                        uppercase
                        tracking-[0.16em]
                        text-[#c88a20]
                    "
                >
                    Clear and transparent
                </p>

                <h2
                    className="
                        mt-2
                        text-3xl
                        font-extrabold
                        tracking-tight
                        text-[#7a1625]
                    "
                >
                    Policies &amp; Customer Care
                </h2>

                <p
                    className="
                        mt-3
                        max-w-2xl
                        text-sm
                        leading-7
                        text-[#756763]
                        sm:text-base
                    "
                >
                    Everything you need to know about
                    ordering, collecting, cancelling and
                    requesting help with a Gokul Sweets
                    order.
                </p>
            </div>

            <div
                className="
                    mt-5
                    grid
                    gap-5
                    lg:grid-cols-[240px_minmax(0,1fr)]
                "
            >
                <aside className="min-w-0">

                    <nav
                        aria-label="Policy navigation"
                        className="
                            no-scrollbar
                            flex
                            gap-2
                            overflow-x-auto
                            rounded-2xl
                            border
                            border-[#eadfd6]
                            bg-white
                            p-3
                            lg:sticky
                            lg:top-24
                            lg:block
                            lg:space-y-1
                            lg:overflow-visible
                            lg:p-4
                        "
                    >
                        <p
                            className="
                                hidden
                                px-3
                                pb-3
                                text-sm
                                font-extrabold
                                text-[#241715]
                                lg:block
                            "
                        >
                            On this page
                        </p>

                        {POLICY_LINKS.map(
                            item => (
                                <a
                                    key={item.id}
                                    href={`#${item.id}`}
                                    className="
                                        flex
                                        min-h-10
                                        shrink-0
                                        items-center
                                        rounded-xl
                                        px-3
                                        text-sm
                                        font-semibold
                                        text-[#756763]
                                        transition

                                        hover:bg-[#fff4e5]
                                        hover:text-[#7a1625]

                                        lg:w-full
                                    "
                                >
                                    {item.label}
                                </a>
                            )
                        )}
                    </nav>

                </aside>

                <div className="min-w-0">

                    <div
                        className="
                            rounded-3xl
                            border
                            border-[#ead19a]
                            bg-linear-to-br
                            from-[#fff8e8]
                            to-[#fffdf7]
                            p-5
                            sm:p-7
                        "
                    >
                        <p
                            className="
                                text-xs
                                font-extrabold
                                uppercase
                                tracking-[0.16em]
                                text-[#c88a20]
                            "
                        >
                            At a glance
                        </p>

                        <h3
                            className="
                                mt-1
                                text-xl
                                font-extrabold
                                text-[#7a1625]
                            "
                        >
                            Quick things to know
                        </h3>

                        <div
                            className="
                                mt-5
                                grid
                                gap-3
                                sm:grid-cols-3
                            "
                        >
                            <QuickFact
                                title="Pickup only"
                                message="Website orders must be collected from the selected branch."
                            />

                            <QuickFact
                                title="Online payment"
                                message="All website orders are paid securely during checkout."
                            />

                            <QuickFact
                                title="Report within 2 hours"
                                message="Tell us promptly about quality, damage or incorrect items."
                            />
                        </div>
                    </div>

                    <div
                        className="
                            mt-5
                            space-y-4
                        "
                    >
                        <PolicySection
                            id="pickup-policy"
                            icon={<PickupIcon />}
                            title="Pickup Policy"
                            description="Website orders are prepared for collection from your selected branch."
                        >
                            <PolicyPoint title="Pickup location">
                                Collect from the branch selected
                                while placing the order.
                            </PolicyPoint>

                            <PolicyPoint title="Pickup window">
                                Please arrive during the reserved
                                date and time shown in your order.
                            </PolicyPoint>

                            <PolicyPoint title="Order identification">
                                Provide the order number and
                                registered mobile number during
                                collection.
                            </PolicyPoint>

                            <PolicyPoint title="Order inspection">
                                Check your order at pickup and
                                immediately report missing,
                                incorrect or visibly damaged items.
                            </PolicyPoint>
                        </PolicySection>

                        <PolicySection
                            id="cancellation-policy"
                            icon={<CalendarIcon />}
                            title="Cancellation Policy"
                            description="Cancellation availability depends on the products in the order."
                        >
                            <PolicyPoint
                                title="Weight or mixed orders"
                                important
                            >
                                Cancel at least{" "}
                                <strong className="text-[#7a1625]">
                                    24 hours before
                                </strong>{" "}
                                the scheduled pickup.
                            </PolicyPoint>

                            <PolicyPoint
                                title="Unit-only orders"
                                important
                            >
                                Cancel at least{" "}
                                <strong className="text-[#7a1625]">
                                    2 hours before
                                </strong>{" "}
                                the scheduled pickup.
                            </PolicyPoint>

                            <PolicyPoint title="Preparation started">
                                Cancellation may be unavailable once
                                preparation has begun or the relevant
                                cutoff has passed.
                            </PolicyPoint>
                        </PolicySection>

                        <PolicySection
                            id="refund-policy"
                            icon={
                                <span
                                    aria-hidden="true"
                                    className="
                                        text-2xl
                                        font-extrabold
                                    "
                                >
                                    ₹
                                </span>
                            }
                            title="Refund Policy"
                            description="Eligible refunds are processed securely to the original payment method."
                        >
                            <PolicyPoint title="No-show orders">
                                Refunds are not available when an
                                order is not collected and is marked
                                as a no-show.
                            </PolicyPoint>

                            <PolicyPoint title="Quality concerns">
                                Wrong, damaged or quality-related
                                concerns must be reported at pickup
                                or within 2 hours of collection.
                            </PolicyPoint>

                            <PolicyPoint title="Approved refunds">
                                The amount is returned to the original
                                payment method. Your bank or payment
                                provider may require additional
                                processing time.
                            </PolicyPoint>
                        </PolicySection>

                        <PolicySection
                            id="privacy-policy"
                            icon={<ShieldIcon />}
                            title="Privacy Policy"
                            description="We use customer information only where it is needed to operate and support orders."
                        >
                            <PolicyPoint title="Information collected">
                                We collect your name, mobile number,
                                selected branch, order details and
                                payment references.
                            </PolicyPoint>

                            <PolicyPoint title="Payment security">
                                Card, banking and UPI credentials
                                are handled by the payment provider
                                and are not stored by Gokul Sweets.
                            </PolicyPoint>

                            <PolicyPoint title="How information is used">
                                Information may be used for order
                                fulfilment, pickup verification,
                                customer support, refunds, fraud
                                prevention and required business
                                records.
                            </PolicyPoint>
                        </PolicySection>

                        <PolicySection
                            id="terms-and-conditions"
                            icon={<DocumentIcon />}
                            title="Terms and Conditions"
                            description="Please review these terms before completing your order."
                        >
                            <PolicyPoint title="Prices and availability">
                                Product availability, price and final
                                payable amount are confirmed by the
                                server when the order is created.
                            </PolicyPoint>

                            <PolicyPoint title="Product appearance">
                                Product images are illustrative.
                                Handmade products can have natural
                                differences in colour, shape and
                                presentation.
                            </PolicyPoint>

                            <PolicyPoint title="Order fulfilment">
                                Gokul Sweets may reject or refund an
                                order when stock, payment, food
                                safety or operational constraints
                                prevent fulfilment.
                            </PolicyPoint>
                        </PolicySection>
                    </div>
                </div>
            </div>
        </section>
    );
}


function QuickFact({
    title,
    message
}: {
    title: string;
    message: string;
}) {

    return (
        <div
            className="
                rounded-2xl
                border
                border-[#eadfd6]
                bg-white/80
                p-4
            "
        >
            <p
                className="
                    text-sm
                    font-extrabold
                    text-[#241715]
                "
            >
                {title}
            </p>

            <p
                className="
                    mt-1
                    text-xs
                    leading-5
                    text-[#756763]
                "
            >
                {message}
            </p>
        </div>
    );
}