"use client";

import Link from "next/link";
import PickupContext, {useDateAvailability} from "@/components/menu/PickupContext";

import {
    useEffect,
    useMemo,
    useRef,
    useState
} from "react";

import MenuSearch
    from "@/components/menu/MenuSearch";

import CategoryTabs
    from "@/components/menu/CategoryTabs";

import ProductGrid
    from "@/components/menu/ProductGrid";

import WeightSelectorSheet
    from "@/components/menu/WeightSelectorSheet";

import {
    ProductSkeletonGrid
} from "@/components/menu/ProductSkeleton";

import FloatingCartButton
    from "@/components/menu/FloatingCartButton";

import {
    getMenu
} from "@/services/menuApi";

import {
    getProductRatingSummaries
} from "@/services/reviewApi";

import {
    useSelectedBranch
} from "@/hooks/useSelectedBranch";

import {
    useCart
} from "@/hooks/useCart";

import type {
    MenuCategory,
    MenuProduct
} from "@/types/menu";

import type {
    ProductRatingSummary
} from "@/types/review";


interface ProductRatingState {
    branchId: number;
    summaries: Record<number, ProductRatingSummary>;
}


export default function MenuScreen() {

    const {
        branch
    } =
        useSelectedBranch();


    const {
        items,
        branchId: cartBranchId,
        itemCount,
        subtotal,
        addItem,
        increaseQuantity,
        decreaseQuantity,
        replaceCartForBranch
    } =
        useCart();


    const [
        categories,
        setCategories
    ] =
        useState<MenuCategory[]>(
            []
        );


    const [
        ratingState,
        setRatingState
    ] =
        useState<ProductRatingState | null>(
            null
        );


    const [
        selectedCategoryId,
        setSelectedCategoryId
    ] =
        useState<number | null>(
            null
        );


    const [
        search,
        setSearch
    ] =
        useState("");


    const [
        loading,
        setLoading
    ] =
        useState(true);


    const [
        loadedBranchId,
        setLoadedBranchId
    ] =
        useState<number | null>(
            null
        );


    const [
        error,
        setError
    ] =
        useState<string | null>(
            null
        );


    const [
        reloadKey,
        setReloadKey
    ] =
        useState(0);


    const [
        cartNotice,
        setCartNotice
    ] =
        useState<string | null>(
            null
        );


    const [
        weightProduct,
        setWeightProduct
    ] =
        useState<MenuProduct | null>(
            null
        );


    const cartNoticeTimerRef =
        useRef<number | null>(
            null
        );


    useEffect(
        () => {

            return () => {

                if (
                    cartNoticeTimerRef.current
                    !== null
                ) {

                    window.clearTimeout(
                        cartNoticeTimerRef.current
                    );
                }
            };

        },
        []
    );


    function showCartNotice(
        productName: string
    ) {

        setCartNotice(
            `${productName} added to cart`
        );


        if (
            cartNoticeTimerRef.current
            !== null
        ) {

            window.clearTimeout(
                cartNoticeTimerRef.current
            );
        }


        cartNoticeTimerRef.current =
            window.setTimeout(
                () => {

                    setCartNotice(
                        null
                    );


                    cartNoticeTimerRef.current =
                        null;

                },
                1600
            );
    }


    useEffect(
        () => {

            if (
                !branch
            ) {

                return;
            }


            const selectedBranch =
                branch;


            const controller =
                new AbortController();


            async function loadMenu() {

                try {

                    const result =
                        await getMenu(
                            selectedBranch.id,
                            controller.signal
                        );


                    if (
                        controller.signal.aborted
                    ) {

                        return;
                    }


                    setCategories(
                        result
                    );

                    setSelectedCategoryId(Number(new URLSearchParams(window.location.search).get("category")) || null);

                    setSearch(
                        ""
                    );

                    setError(
                        null
                    );

                    setLoadedBranchId(
                        selectedBranch.id
                    );

                    setLoading(
                        false
                    );


                    const productIds =
                        Array.from(
                            new Set(
                                result.flatMap(
                                    category =>
                                        category.products.map(
                                            product =>
                                                product.id
                                        )
                                )
                            )
                        );


                    if (
                        productIds.length === 0
                    ) {

                        setRatingState(
                            {
                                branchId:
                                    selectedBranch.id,

                                summaries:
                                    {}
                            }
                        );

                        return;
                    }


                    const ratingBatches =
                        Array.from(
                            {
                                length:
                                    Math.ceil(
                                        productIds.length
                                        /
                                        100
                                    )
                            },
                            (_, index) =>
                                productIds.slice(
                                    index * 100,
                                    (index + 1) * 100
                                )
                        );


                    void Promise.all(
                        ratingBatches.map(
                            batch =>
                                getProductRatingSummaries(
                                    batch,
                                    controller.signal
                                )
                        )
                    )
                        .then(
                            batchResults => {

                                if (
                                    controller.signal.aborted
                                ) {
                                    return;
                                }


                                const summaries =
                                    batchResults.flat();


                                setRatingState(
                                    {
                                        branchId:
                                            selectedBranch.id,

                                        summaries:
                                            summaries.reduce<
                                                Record<
                                                    number,
                                                    ProductRatingSummary
                                                >
                                            >(
                                                (
                                                    indexed,
                                                    summary
                                                ) => {

                                                    indexed[
                                                        summary.productId
                                                    ] =
                                                        summary;

                                                    return indexed;
                                                },
                                                {}
                                            )
                                    }
                                );
                            }
                        )
                        .catch(
                            exception => {

                                if (
                                    controller.signal.aborted
                                ) {
                                    return;
                                }


                                console.error(
                                    "Unable to load product ratings:",
                                    exception
                                );


                                setRatingState(
                                    {
                                        branchId:
                                            selectedBranch.id,

                                        summaries:
                                            {}
                                    }
                                );
                            }
                        );

                } catch (exception) {

                    if (
                        controller.signal.aborted
                    ) {

                        return;
                    }


                    console.error(
                        "Unable to load menu:",
                        exception
                    );


                    setCategories(
                        []
                    );


                    setError(
                        exception instanceof Error
                            ? exception.message
                            : "Unable to load menu."
                    );


                    setLoadedBranchId(
                        selectedBranch.id
                    );

                    setLoading(
                        false
                    );
                }
            }


            void loadMenu();


            return () => {

                controller.abort();
            };

        },
        [
            branch,
            reloadKey
        ]
    );


    const isLoading =
        branch
            ? loading
              ||
              loadedBranchId !== branch.id
            : false;


    const ratingSummaries =
        branch
        &&
        ratingState?.branchId === branch.id
            ? ratingState.summaries
            : {};


    const ratingsLoading =
        Boolean(
            branch
        )
        &&
        ratingState?.branchId !== branch?.id;



    const allProducts =
        useMemo(
            () =>
                categories.flatMap(
                    category =>
                        category.products
                ),
            [
                categories
            ]
        );


    const productQuantities =
        useMemo(
            () => {

                if (
                    !branch
                    ||
                    cartBranchId !== branch.id
                ) {
                    return {};
                }


                return items.reduce<
                    Record<number, number>
                >(
                    (
                        quantities,
                        item
                    ) => {

                        quantities[
                            item.product.id
                        ] =
                            item.quantity;

                        return quantities;
                    },
                    {}
                );
            },
            [
                branch,
                cartBranchId,
                items
            ]
        );


    const productWeights =
        useMemo(
            () => {

                if (
                    !branch
                    || cartBranchId !== branch.id
                ) {
                    return {};
                }


                return items.reduce<
                    Record<number, number>
                >(
                    (
                        weights,
                        item
                    ) => {

                        if (
                            item.product.saleMode === "WEIGHT"
                            && item.weightGrams !== null
                        ) {
                            weights[item.product.id] = item.weightGrams;
                        }

                        return weights;
                    },
                    {}
                );
            },
            [
                branch,
                cartBranchId,
                items
            ]
        );


    const effectiveCategoryId =
        useMemo(
            () => {

                if (
                    selectedCategoryId
                    === null
                ) {

                    return null;
                }


                const exists =
                    categories.some(
                        category =>
                            category.id
                            === selectedCategoryId
                    );


                return exists
                    ? selectedCategoryId
                    : null;
            },
            [
                categories,
                selectedCategoryId
            ]
        );


    const selectedCategory =
        useMemo(
            () => {

                if (
                    effectiveCategoryId
                    === null
                ) {

                    return null;
                }


                return categories.find(
                    category =>
                        category.id
                        === effectiveCategoryId
                )
                ?? null;
            },
            [
                categories,
                effectiveCategoryId
            ]
        );


    const filteredProducts =
        useMemo(
            () => {

                const query =
                    search
                        .trim()
                        .toLowerCase();


                return allProducts.filter(
                    product => {

                        const categoryMatch =
                            effectiveCategoryId
                            === null
                            ||
                            product.categoryId
                            === effectiveCategoryId;


                        const searchMatch =
                            !query
                            ||
                            product.name
                                .toLowerCase()
                                .includes(
                                    query
                                )
                            ||
                            product.description
                                ?.toLowerCase()
                                .includes(
                                    query
                                );


                        return (
                            categoryMatch
                            &&
                            searchMatch
                        );
                    }
                );
            },
            [
                allProducts,
                effectiveCategoryId,
                search
            ]
        );


    const hasActiveFilters =
        search.trim().length > 0
        ||
        effectiveCategoryId !== null;

    const pickupCheck = useDateAvailability(filteredProducts);

    function handleAddToCart(
        product: MenuProduct
    ) {

        if (product.saleMode === "WEIGHT") {
            setWeightProduct(product);
            return;
        }

        commitAddToCart(product);
    }


    function commitAddToCart(
        product: MenuProduct,
        weightGrams?: number
    ) {

        if (
            !branch
        ) {

            return;
        }


        const result =
            addItem(
                product,
                branch.id,
                weightGrams
            );


        if (
            result === "added"
        ) {

            showCartNotice(
                product.name
            );

            return;
        }


        if (
            result === "branch-mismatch"
        ) {

            const confirmed =
                window.confirm(
                    "Your cart contains items from another branch. "
                    + "Clear the existing cart and start a new order from "
                    + branch.name
                    + "?"
                );


            if (
                !confirmed
            ) {

                return;
            }


            replaceCartForBranch(
                product,
                branch.id,
                weightGrams
            );


            showCartNotice(
                product.name
            );
        }
    }


    function handleWeightConfirm(
        product: MenuProduct,
        weightGrams: number
    ) {
        setWeightProduct(null);
        commitAddToCart(product, weightGrams);
    }


    function retryMenu() {

        setLoading(
            true
        );

        setLoadedBranchId(
            null
        );

        setError(
            null
        );

        setReloadKey(
            current =>
                current + 1
        );
    }


    function clearFilters() {

        setSearch(
            ""
        );

        setSelectedCategoryId(
            null
        );
    }


    if (
        !branch
    ) {

        return (
            <section
                className="
                    mx-auto
                    max-w-lg
                    py-6
                "
            >

                <div
                    className="
                        rounded-3xl
                        border
                        border-[#eadfd6]
                        bg-white
                        px-6
                        py-12
                        text-center
                        shadow-sm
                    "
                >

                    <div
                        aria-hidden="true"
                        className="
                            mx-auto
                            flex
                            h-16
                            w-16
                            items-center
                            justify-center
                            rounded-full
                            bg-[#fff4e5]
                            text-3xl
                        "
                    >
                        📍
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
                        Pickup location required
                    </p>


                    <h1
                        className="
                            mt-2
                            text-2xl
                            font-bold
                            tracking-tight
                            text-[#241715]
                        "
                    >
                        Select your pickup branch
                    </h1>


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
                        Your branch determines which products,
                        prices and pickup options are available.
                    </p>


                    <Link
                        href="/"
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
                        Choose Branch
                    </Link>

                </div>

            </section>
        );
    }


    return (
        <>

            <section
                className={`
                    mx-auto
                    max-w-295
                    future-menu-width

                    ${
                        itemCount > 0
                            ? "pb-48"
                            : "pb-8"
                    }
                `}
            >

                <header
                    className="
                        overflow-hidden
                        rounded-3xl
                        border
                        border-[#eadfd6]
                        bg-white
                        p-5
                        shadow-sm
                        sm:p-6
                    "
                >

                    <div
                        className="
                            flex
                            flex-col
                            gap-5
                            sm:flex-row
                            sm:items-start
                            sm:justify-between
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
                                Freshly prepared
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
                                Explore our menu
                            </h1>


                            <p
                                className="
                                    mt-3
                                    max-w-xl
                                    text-sm
                                    leading-6
                                    text-[#756763]
                                "
                            >
                                {pickupCheck.features?.contextualStorefrontV2
                                    ? `Browsing ${branch.name}. Prices are shown per piece or per kg. Choose a pickup date to preview availability; we confirm the final quote before payment.`
                                    : pickupCheck.features?.smartAvailability
                                    ? "Choose a pickup date to see what fits, or browse first and decide later."
                                    : "Choose your favourites now. You'll select a convenient pickup time during checkout."}
                            </p>

                        </div>


                        <div
                            className="
                                rounded-2xl
                                border
                                border-[#eadfd6]
                                bg-[#fffaf3]
                                p-3
                                sm:min-w-52
                            "
                        >

                            <p
                                className="
                                    text-[10px]
                                    font-semibold
                                    uppercase
                                    tracking-[0.12em]
                                    text-[#c88a20]
                                "
                            >
                                Pickup branch
                            </p>


                            <p
                                className="
                                    mt-1
                                    truncate
                                    text-sm
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                {branch.name}
                            </p>


                            <Link
                                href="/"
                                className="
                                    mt-2
                                    inline-flex
                                    text-xs
                                    font-semibold
                                    text-[#7a1625]
                                    hover:underline
                                "
                            >
                                Change branch
                            </Link>

                        </div>

                    </div>


                    {!isLoading
                        &&
                        !error
                        && (
                            <div
                                className="
                                    mt-5
                                    flex
                                    flex-wrap
                                    gap-2
                                    border-t
                                    border-[#eadfd6]
                                    pt-4
                                "
                            >

                                <InfoPill
                                    value={
                                        `${allProducts.length} ${
                                            allProducts.length === 1
                                                ? "item"
                                                : "items"
                                        }`
                                    }
                                />

                                <InfoPill
                                    value={
                                        `${categories.length} ${
                                            categories.length === 1
                                                ? "category"
                                                : "categories"
                                        }`
                                    }
                                />

                                <InfoPill
                                    value="Pickup only"
                                />

                            </div>
                        )}

                </header>

                <PickupContext check={pickupCheck} />

                <div
                    className="
                        mt-5
                        rounded-3xl
                        border
                        border-[#eadfd6]
                        bg-white
                        p-4
                        shadow-sm
                        sm:p-5
                    "
                >

                    <MenuSearch
                        refined={pickupCheck.features?.contextualStorefrontV2 === true}
                        value={
                            search
                        }
                        onChange={
                            setSearch
                        }
                    />


                    {!isLoading
                        &&
                        !error
                        &&
                        categories.length > 0
                        && (
                            <div
                                className="
                                    mt-4
                                "
                            >

                                <CategoryTabs
                                    categories={
                                        categories
                                    }
                                    selectedCategoryId={
                                        effectiveCategoryId
                                    }
                                    onSelect={
                                        setSelectedCategoryId
                                    }
                                />

                            </div>
                        )}

                </div>


                <div
                    className="
                        mt-6
                    "
                >

                    {isLoading
                        && (
                            <ProductSkeletonGrid />
                        )}


                    {!isLoading
                        &&
                        error
                        && (
                            <MenuError
                                message={
                                    error
                                }
                                onRetry={
                                    retryMenu
                                }
                            />
                        )}


                    {!isLoading
                        &&
                        !error
                        &&
                        categories.length === 0
                        && (
                            <MenuEmptyState
                                title="Menu is not available yet"
                                description={`There are currently no menu items available for ${branch.name}. Please check again shortly or choose another branch.`}
                                actionLabel="Change branch"
                                actionHref="/"
                            />
                        )}


                    {!isLoading
                        &&
                        !error
                        &&
                        categories.length > 0
                        &&
                        filteredProducts.length > 0
                        && (
                            <>

                                <div
                                    className="
                                        mb-4
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
                                                tracking-[0.12em]
                                                text-[#c88a20]
                                            "
                                        >
                                            {
                                                selectedCategory
                                                    ? selectedCategory.name
                                                    : "All items"
                                            }
                                        </p>


                                        <h2
                                            className="
                                                mt-1
                                                text-xl
                                                font-bold
                                                text-[#241715]
                                            "
                                        >
                                            {
                                                search.trim()
                                                    ? `${filteredProducts.length} ${
                                                        filteredProducts.length === 1
                                                            ? "result"
                                                            : "results"
                                                    }`
                                                    : `${filteredProducts.length} ${
                                                        filteredProducts.length === 1
                                                            ? "item"
                                                            : "items"
                                                    } on the menu`
                                            }
                                        </h2>

                                    </div>


                                    {hasActiveFilters
                                        && (
                                            <button
                                                type="button"
                                                onClick={
                                                    clearFilters
                                                }
                                                className="
                                                    shrink-0
                                                    text-sm
                                                    font-semibold
                                                    text-[#7a1625]
                                                    hover:underline
                                                "
                                            >
                                                Clear filters
                                            </button>
                                        )}

                                </div>


                                <ProductGrid
                                    refined={pickupCheck.features?.contextualStorefrontV2 === true}
                                    pickupItems={pickupCheck.items}
                                    pickupChecking={!!pickupCheck.features?.smartAvailability && !!pickupCheck.intent.date && !pickupCheck.data}
                                    dateAware={!!pickupCheck.features?.smartAvailability}
                                    products={
                                        filteredProducts
                                    }
                                    ratingSummaries={
                                        ratingSummaries
                                    }
                                    ratingsLoading={
                                        ratingsLoading
                                    }
                                    quantities={
                                        productQuantities
                                    }
                                    weights={
                                        productWeights
                                    }
                                    onIncrease={
                                        increaseQuantity
                                    }
                                    onDecrease={
                                        decreaseQuantity
                                    }
                                    onAdd={
                                        handleAddToCart
                                    }
                                />

                            </>
                        )}


                    {!isLoading
                        &&
                        !error
                        &&
                        categories.length > 0
                        &&
                        filteredProducts.length === 0
                        && (
                            <div
                                className="
                                    rounded-3xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    px-6
                                    py-12
                                    text-center
                                "
                            >

                                <div
                                    aria-hidden="true"
                                    className="
                                        mx-auto
                                        flex
                                        h-14
                                        w-14
                                        items-center
                                        justify-center
                                        rounded-full
                                        bg-[#fff4e5]
                                        text-2xl
                                    "
                                >
                                    🔎
                                </div>


                                <h2
                                    className="
                                        mt-4
                                        text-xl
                                        font-bold
                                        text-[#241715]
                                    "
                                >
                                    No matching items
                                </h2>


                                <p
                                    className="
                                        mx-auto
                                        mt-2
                                        max-w-md
                                        text-sm
                                        leading-6
                                        text-[#756763]
                                    "
                                >
                                    Try another search or category
                                    to see more of the menu.
                                </p>


                                <button
                                    type="button"
                                    onClick={
                                        clearFilters
                                    }
                                    className="
                                        mt-5
                                        min-h-11
                                        rounded-xl
                                        border
                                        border-[#eadfd6]
                                        bg-white
                                        px-5
                                        text-sm
                                        font-semibold
                                        text-[#7a1625]
                                        transition

                                        hover:bg-[#fffaf3]
                                        active:scale-[0.98]
                                    "
                                >
                                    Show all items
                                </button>

                            </div>
                        )}

                </div>

            </section>


            {cartNotice
                && (
                    <div
                        role="status"
                        aria-live="polite"
                        className="
                            fixed
                            bottom-40
                            left-1/2
                            z-70
                            max-w-[calc(100vw-2rem)]
                            -translate-x-1/2
                            rounded-full
                            bg-[#241715]
                            px-4
                            py-2.5
                            text-center
                            text-sm
                            font-semibold
                            text-white
                            shadow-lg
                        "
                    >
                        ✓ {cartNotice}
                    </div>
                )}


            <FloatingCartButton
                itemCount={
                    itemCount
                }
                total={
                    subtotal
                }
            />


            {weightProduct
                && (
                    <WeightSelectorSheet
                        key={weightProduct.id}
                        product={weightProduct}
                        currentWeightGrams={
                            productWeights[weightProduct.id]
                            ?? null
                        }
                        onClose={() => setWeightProduct(null)}
                        onConfirm={handleWeightConfirm}
                    />
                )}

        </>
    );
}


function InfoPill({
    value
}: {
    value: string;
}) {

    return (
        <span
            className="
                inline-flex
                items-center
                rounded-full
                bg-[#fff4e5]
                px-3
                py-1.5
                text-xs
                font-semibold
                text-[#7a1625]
            "
        >
            {value}
        </span>
    );
}


function MenuError({
    message,
    onRetry
}: {
    message: string;
    onRetry: () => void;
}) {

    return (
        <div
            className="
                rounded-3xl
                border
                border-[#eadfd6]
                bg-white
                px-6
                py-12
                text-center
            "
        >

            <div
                aria-hidden="true"
                className="
                    mx-auto
                    flex
                    h-16
                    w-16
                    items-center
                    justify-center
                    rounded-full
                    bg-[#fff4e5]
                    text-3xl
                "
            >
                ⚠
            </div>


            <h2
                className="
                    mt-4
                    text-xl
                    font-bold
                    text-[#241715]
                "
            >
                Unable to load menu
            </h2>


            <p
                className="
                    mx-auto
                    mt-2
                    max-w-md
                    text-sm
                    leading-6
                    text-[#756763]
                "
            >
                {message}
            </p>


            <button
                type="button"
                onClick={
                    onRetry
                }
                className="
                    mt-5
                    min-h-11
                    rounded-xl
                    bg-[#7a1625]
                    px-5
                    text-sm
                    font-bold
                    text-white!
                    transition

                    hover:bg-[#5d0f1b]
                    active:scale-[0.98]
                "
            >
                Try again
            </button>

        </div>
    );
}


function MenuEmptyState({
    title,
    description,
    actionLabel,
    actionHref
}: {
    title: string;
    description: string;
    actionLabel: string;
    actionHref: string;
}) {

    return (
        <div
            className="
                rounded-3xl
                border
                border-[#eadfd6]
                bg-white
                px-6
                py-12
                text-center
            "
        >

            <div
                aria-hidden="true"
                className="
                    mx-auto
                    flex
                    h-16
                    w-16
                    items-center
                    justify-center
                    rounded-full
                    bg-[#fff4e5]
                    text-3xl
                "
            >
                🍽️
            </div>


            <h2
                className="
                    mt-4
                    text-xl
                    font-bold
                    text-[#241715]
                "
            >
                {title}
            </h2>


            <p
                className="
                    mx-auto
                    mt-2
                    max-w-md
                    text-sm
                    leading-6
                    text-[#756763]
                "
            >
                {description}
            </p>


            <Link
                href={
                    actionHref
                }
                className="
                    mt-5
                    inline-flex
                    min-h-11
                    items-center
                    justify-center
                    rounded-xl
                    bg-[#7a1625]
                    px-5
                    text-sm
                    font-bold
                    text-white!
                    transition

                    hover:bg-[#5d0f1b]
                    active:scale-[0.98]
                "
            >
                {actionLabel}
            </Link>

        </div>
    );
}
