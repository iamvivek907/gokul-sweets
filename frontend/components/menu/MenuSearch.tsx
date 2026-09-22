"use client";


interface MenuSearchProps {

    value: string;

    onChange:
        (value: string) => void;
}


export default function MenuSearch({
    value,
    onChange
}: MenuSearchProps) {

    return (
        <div
            className="
                relative
                w-full
            "
        >

            <div
                className="
                    pointer-events-none
                    absolute
                    left-4
                    top-1/2
                    -translate-y-1/2
                    text-lg
                    text-[#756763]
                "
            >
                ⌕
            </div>


            <input
                type="search"
                value={value}
                onChange={
                    event =>
                        onChange(
                            event.target.value
                        )
                }
                placeholder="Search sweets, snacks & meals"
                className="
                    min-h-13
                    w-full
                    rounded-2xl
                    border
                    border-[#eadfd6]
                    bg-white
                    py-3
                    pl-12
                    pr-4
                    text-sm
                    outline-none
                    shadow-sm
                    transition

                    focus:border-[#c88a20]
                    focus:ring-2
                    focus:ring-[#f6dfad]
                "
            />

        </div>
    );
}