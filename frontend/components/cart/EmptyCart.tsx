import Link
    from "next/link";


export default function EmptyCart() {

    return (
        <div
            className="
                w-full
                min-w-0
                rounded-3xl
                border
                border-[#eadfd6]
                bg-white
                px-6
                py-14
                text-center
                shadow-sm
                sm:py-16
            "
        >

            <div
                aria-hidden="true"
                className="
                    mx-auto
                    flex
                    h-20
                    w-20
                    items-center
                    justify-center
                    rounded-full
                    bg-[#fff4e5]
                    text-4xl
                "
            >
                🛍️
            </div>


            <p
                className="
                    mt-5
                    text-xs
                    font-semibold
                    uppercase
                    tracking-[0.14em]
                    text-[#c88a20]
                "
            >
                Nothing here yet
            </p>


            <h2
                className="
                    mt-2
                    text-2xl
                    font-bold
                    text-[#241715]
                "
            >
                Your cart is empty
            </h2>


            <p
                className="
                    mx-auto
                    mt-3
                    max-w-sm
                    text-sm
                    leading-6
                    text-[#756763]
                "
            >
                Add your favourite sweets, snacks and meals,
                then choose a convenient pickup time.
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
                Explore Menu
            </Link>

        </div>
    );
}
