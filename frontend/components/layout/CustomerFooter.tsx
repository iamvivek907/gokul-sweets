export default function CustomerFooter() {
    return (
        <footer
            aria-label="Customer footer"
            className="customer-site-footer
                border-t
                border-[#eadfd6]
                bg-white
                px-4
                pb-[calc(90px+env(safe-area-inset-bottom))]
                pt-6
            "
        >
            <div
                className="
                    mx-auto
                    flex
                    w-full
                    max-w-[1180px]
                    flex-col
                    gap-2
                    text-center
                "
            >
                <p className="text-sm font-semibold text-[#241715]">
                    Gokul Sweets
                </p>
                <p className="text-xs leading-5 text-[#756763]">
                    Fresh sweets for every celebration. We’ll confirm your pickup time and payment
                    before preparing your order.
                </p>
            </div>
        </footer>
    );
}
