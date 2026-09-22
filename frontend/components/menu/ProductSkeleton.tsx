export default function ProductSkeleton() {
    return (
        <div
            className="
                flex
                min-h-36
                overflow-hidden
                rounded-3xl
                border
                border-[#eadfd6]
                bg-white

                sm:flex-col
            "
        >
            <div
                className="
                    min-h-36
                    w-28
                    shrink-0
                    animate-pulse
                    bg-[#f4e9df]

                    sm:aspect-[4/3]
                    sm:min-h-0
                    sm:w-full
                "
            />


            <div className="flex min-w-0 flex-1 flex-col p-3.5 sm:p-4">
                <div className="h-2.5 w-16 animate-pulse rounded bg-[#f4e9df]" />
                <div className="mt-2.5 h-5 w-3/4 animate-pulse rounded bg-[#f4e9df]" />
                <div className="mt-2 h-3 w-full animate-pulse rounded bg-[#f4e9df]" />
                <div className="mt-2 h-3 w-4/5 animate-pulse rounded bg-[#f4e9df]" />
                <div className="mt-2.5 h-3.5 w-28 animate-pulse rounded-full bg-[#f4e9df]" />


                <div className="mt-auto flex items-center justify-between gap-3 pt-3">
                    <div className="h-5 w-14 animate-pulse rounded bg-[#f4e9df]" />
                    <div className="h-11 w-32 animate-pulse rounded-xl bg-[#f4e9df]" />
                </div>
            </div>
        </div>
    );
}


export function ProductSkeletonGrid() {
    return (
        <div
            className="
                grid
                grid-cols-1
                gap-3

                sm:grid-cols-2
                sm:gap-4

                lg:grid-cols-3
            "
        >
            {Array.from({length: 6}).map((_, index) => (
                <ProductSkeleton key={index} />
            ))}
        </div>
    );
}
