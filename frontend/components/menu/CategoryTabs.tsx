"use client";

import type {
    MenuCategory
} from "@/types/menu";


interface CategoryTabsProps {

    categories:
        MenuCategory[];

    selectedCategoryId:
        number | null;

    onSelect:
        (categoryId: number | null) => void;
}


export default function CategoryTabs({
    categories,
    selectedCategoryId,
    onSelect
}: CategoryTabsProps) {

    return (
        <div
            className="
                no-scrollbar
                flex
                gap-2
                overflow-x-auto
                pb-1
            "
        >

            <button
                type="button"
                onClick={() =>
                    onSelect(null)
                }
                className={`
                    shrink-0
                    rounded-full
                    px-4
                    py-2
                    text-sm
                    font-semibold
                    transition
                    active:scale-95

                    ${
                        selectedCategoryId === null
                            ? "bg-[#7a1625] text-white"
                            : "border border-[#eadfd6] bg-white text-[#756763]"
                    }
                `}
            >
                All
            </button>


            {categories.map(
                category => {

                    const active =
                        selectedCategoryId
                        === category.id;

                    return (
                        <button
                            key={
                                category.id
                            }
                            type="button"
                            onClick={() =>
                                onSelect(
                                    category.id
                                )
                            }
                            className={`
                                shrink-0
                                rounded-full
                                px-4
                                py-2
                                text-sm
                                font-semibold
                                transition
                                active:scale-95

                                ${
                                    active
                                        ? "bg-[#7a1625] text-white"
                                        : "border border-[#eadfd6] bg-white text-[#756763]"
                                }
                            `}
                        >
                            {category.name}
                        </button>
                    );
                }
            )}

        </div>
    );
}