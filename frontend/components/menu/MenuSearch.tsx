"use client";
import {T} from "@/lib/language";


interface MenuSearchProps {
    refined?: boolean;

    value: string;

    onChange:
        (value: string) => void;
}


export default function MenuSearch({
    refined = false,
    value,
    onChange
}: MenuSearchProps) {

    return (
        <div
            className="menu-search
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
                <span className="menu-search-legacy-icon" aria-hidden="true">⌕</span><svg className="menu-search-modern-icon" aria-hidden="true" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="10.5" cy="10.5" r="7.5"/><path d="m16 16 5 5"/></svg>
            </div>


            {refined && <label htmlFor="gokul-menu-search" className="mb-2 block text-sm font-semibold text-[#241715]"><T text="Find a favourite" /></label>}
            <input
                id={refined ? "gokul-menu-search" : undefined}
                aria-label={refined ? undefined : "Search the menu"}
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
