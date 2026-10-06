"use client";
import dynamic from "next/dynamic";
import {menuFamily,retailCollections} from "@/lib/menuPresentation";
import RetailSweetRail from "./RetailSweetRail";
import MobileMenuHighlights from "./MobileMenuHighlights";
import MenuCategorySection from "./MenuCategorySection";
import MenuOffers from "./MenuOffers";
const MobileMenuPickup=dynamic(()=>import("./MobileMenuPickup"));
import MobileMenuSuggestions from "./MobileMenuSuggestions";
import {useMenuServiceRefresh} from "@/hooks/useMenuServiceRefresh";
import LinkFeedback from "@/components/common/LinkFeedback";

import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {usePhoneViewport} from "@/hooks/usePhoneViewport";
import {apiClient} from "@/services/apiClient";
import {matchesMobileFilters,mobileMenuRows,type PortionGroup} from "@/lib/mobileMenu";
import MobileMenuFilters from "./MobileMenuFilters";
import {T,useTranslation} from "@/lib/language";
import {groupMenuProducts} from "@/lib/menuGroups";

import Link from "next/link";
import BranchMenuGallery from "@/components/menu/BranchMenuGallery";
import NewBranchItems from "@/components/menu/NewBranchItems";
import PickupContext, {useDateAvailability} from "@/components/menu/PickupContext";

import {
    useCallback,
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

import type {AvailableRebateResponse} from "@/types/rebate";
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
    const translate = useTranslation();
    const [branchTab, setBranchTab] = useState<"menu" | "details">("menu");

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
    useMenuServiceRefresh(branch?.id,categories,setCategories);


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


    const phone=usePhoneViewport();
    const mobileFeatures=useStorefrontFeatures();
    const phoneMenu=phone===true&&(mobileFeatures?.futuristicStorefrontV2===true||mobileFeatures?.checkoutExperienceV2===true)&&mobileFeatures?.contextualStorefrontV2===true;
    const [browseCategory,setBrowseCategory]=useState<{branchId:number;id:number}|null>(null);
    const activeBrowseId=phoneMenu&&browseCategory?.branchId===branch?.id?browseCategory?.id:undefined;
    const activeBrowse=categories.find(c=>c.id===activeBrowseId);
    const retailBrowse=!!activeBrowse&&/snack|dairy|drink|beverage|biscuit|namkeen/i.test(activeBrowse.name);
    const [mobileCategories,setMobileCategories]=useState<number[]|null>(null);
    const [maximumPrice,setMaximumPrice]=useState<number|null>(null);
    const [portionsOnly,setPortionsOnly]=useState(false);
    const [portionSnapshot,setPortionSnapshot]=useState<{branchId:number;groups:PortionGroup[]}|null>(null);
    const portionGroups=useMemo(()=>portionSnapshot?.branchId===branch?.id?portionSnapshot?.groups??[]:[],[portionSnapshot,branch?.id]);
    useEffect(()=>{if(!phoneMenu||!branch)return;const controller=new AbortController();void apiClient<{groups:PortionGroup[]}>(`/api/menu/portion-groups?branchId=${branch.id}`,{signal:controller.signal}).then(value=>{if(!controller.signal.aborted)setPortionSnapshot({branchId:branch.id,groups:value.groups??[]});}).catch(()=>{});return()=>controller.abort();},[phoneMenu,branch]);
    const filteredProducts =
        useMemo(
            () => {

                const query =
                    search
                        .trim()
                        .toLowerCase();


                return allProducts.filter(
                    product => {

                        const categoryMatch = phoneMenu&&mobileCategories!==null ? !mobileCategories.length||mobileCategories.includes(product.categoryId) :
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
                            && (!phoneMenu||!activeBrowse||(menuFamily(product.categoryName)===menuFamily(activeBrowse.name)))
                            &&
                            searchMatch
                            && (!phoneMenu||matchesMobileFilters(product,null,maximumPrice,portionsOnly,portionGroups))
                        );
                    }
                );
            },
            [
                allProducts,
                effectiveCategoryId,
                search,phoneMenu,mobileCategories,maximumPrice,portionsOnly,portionGroups,activeBrowse
            ]
        );


    const [browseScroll,setBrowseScroll]=useState<{branchId:number;id:number;revision:number}|null>(null);
    const completedBrowseScroll=useRef<object|null>(null);
    function browseMenu(id:number|null){
        setBrowseCategory(id===null||!branch?null:{branchId:branch.id,id});
        setMobileCategories([]);setMaximumPrice(null);setPortionsOnly(false);setSearch("");
        setBrowseScroll(previous=>id===null||!branch?null:{branchId:branch.id,id,revision:(previous?.revision??0)+1});
    }
    // Scroll only after React has committed the cleared search and destination collection.
    useEffect(()=>{
        if(!phoneMenu||!browseScroll||completedBrowseScroll.current===browseScroll||browseScroll.branchId!==branch?.id)return;
        const frame=requestAnimationFrame(()=>{
            const section=document.getElementById(`menu-category-${browseScroll.id}`);
            if(!section)return;
            completedBrowseScroll.current=browseScroll;
            const disclosure=section.querySelector("details");if(disclosure)disclosure.open=true;
            section.scrollIntoView({block:"start",behavior:matchMedia("(prefers-reduced-motion: reduce)").matches?"instant":"smooth"});
        });
        return()=>cancelAnimationFrame(frame);
    },[phoneMenu,browseScroll,branch?.id,filteredProducts]);

    const hasActiveFilters =
        search.trim().length > 0
        ||
        (phoneMenu ? (!!(mobileCategories??(effectiveCategoryId===null?[]:[effectiveCategoryId])).length || maximumPrice!==null || portionsOnly) : effectiveCategoryId !== null);

    const pickupCheck = useDateAvailability(allProducts);
    const [menuOffer,setMenuOffer]=useState<{key:string;target:AvailableRebateResponse|null}|null>(null);
    const offerContext=JSON.stringify([branch?.id,items,pickupCheck.intent.selection]);
    const onMenuTarget=useCallback((target:AvailableRebateResponse|null)=>setMenuOffer({key:offerContext,target}),[offerContext]);
    const [lastAdded,setLastAdded]=useState<number|null>(null);
    const pairingSeed=lastAdded??items.at(-1)?.product.id;
    const pairing=phoneMenu&&mobileFeatures?.pickupAddOns&&branch&&items.some(i=>i.product.id===pairingSeed)&&filteredProducts.some(p=>p.id===pairingSeed)?<div className="mobile-menu-pairing-slot"><MobileMenuSuggestions seedName={allProducts.find(p=>p.id===pairingSeed)?.name} branchId={branch.id} products={allProducts} groups={portionGroups} pickupItems={pickupCheck.items}/></div>:null;

    function handleAddToCart(
        product: MenuProduct
    ) {

        if (!product.available) return;
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


        const anchor=document.getElementById(`gokul-product-${product.id}`);
        const anchorTop=anchor?.getBoundingClientRect().top;
        // Adding/moving the inline rail must not dislodge the control the customer tapped.
        requestAnimationFrame(()=>{
            if(anchor?.isConnected&&anchorTop!==undefined){
                const delta=anchor.getBoundingClientRect().top-anchorTop;
                if(Math.abs(delta)>1)window.scrollBy({top:delta,behavior:"instant"});
            }
        });

        const liveProduct=categories.flatMap(category=>category.products).find(item=>item.id===product.id);
        if(!liveProduct?.available){setWeightProduct(null);setCartNotice(liveProduct?.serviceAvailability?.message??"This item is currently unavailable.");return;}
        const result =
            addItem(
                product,
                branch.id,
                weightGrams
            );


        if (
            result === "added"
        ) {

            setLastAdded(product.id);
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


            setLastAdded(product.id);
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
        setBrowseCategory(null);setBrowseScroll(null);setMobileCategories([]); setMaximumPrice(null); setPortionsOnly(false);

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
                        <T text="Pickup location required" /></p>


                    <h1
                        className="
                            mt-2
                            text-2xl
                            font-bold
                            tracking-tight
                            text-[#241715]
                        "
                    >
                        <T text="Select your pickup branch" /></h1>


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
                        <T text="Your branch determines which products, prices and pickup options are available." /></p>


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
                        <T text="Choose Branch" /><LinkFeedback /></Link>

                </div>

            </section>
        );
    }


    if (isLoading) return <section className="customer-page-state customer-menu-loading" aria-busy="true"><div role="status"><span className="customer-page-state-brand" aria-hidden="true">G</span><span className="customer-page-state-spinner" aria-hidden="true"/><p><T text="Loading your page…"/></p></div></section>;

    return (
        <>

            <section
                className={`
                    ${phoneMenu?"menu-products-first":""}
                    ${pickupCheck.features?.contextualStorefrontV2 ? "gokul-editorial-menu" : ""}
                    ${branchTab === "details" ? "branch-details-active" : ""}
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

                {pickupCheck.features?.contextualStorefrontV2 && <BranchMenuGallery branch={branch} products={allProducts} activeTab={branchTab} onTabChange={setBranchTab} />}

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

                    {!pickupCheck.features?.contextualStorefrontV2 && <div
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
                                <T text="Freshly prepared" /></p>


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
                                {pickupCheck.features?.contextualStorefrontV2 ? "Find something worth sharing" : "Explore our menu"}
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


                        {!pickupCheck.features?.contextualStorefrontV2 && <div
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
                                <T text="Pickup branch" /></p>


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
                                <T text="Change branch" /><LinkFeedback /></Link>

                        </div>}

                    </div>}

                {!phoneMenu && pickupCheck.features?.contextualStorefrontV2 && !isLoading && !error &&
                    <NewBranchItems branch={branch} products={allProducts} portionGroups={phoneMenu?portionGroups:undefined} onSelect={product => {
                        setSearch("");
                        setSelectedCategoryId(product.categoryId);
                        if(phoneMenu){setMobileCategories([product.categoryId]);setMaximumPrice(null);setPortionsOnly(false);}
                        const group=phoneMenu?portionGroups.find(g=>g.choices.some(c=>c.productId===product.id)):null;
                        const target=group?.choices.find(c=>allProducts.some(p=>p.id===c.productId))?.productId??product.id;
                        requestAnimationFrame(() =>
                            document.getElementById(`gokul-product-${target}`)?.scrollIntoView({behavior: "smooth", block: "center"}));
                    }} />}

                    {!pickupCheck.features?.contextualStorefrontV2 && !isLoading
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
                                                ? translate("item")
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

                {!phoneMenu&&<PickupContext check={pickupCheck} />}

                {phoneMenu&&mobileFeatures?.smartAvailability&&<MobileMenuPickup key={branch.id} branchId={branch.id} products={allProducts} today={pickupCheck.today} days={mobileFeatures.futureOrderingDays??30} selection={pickupCheck.intent.selection} date={pickupCheck.intent.date} expired={pickupCheck.intent.expired} selectionUnavailable={pickupCheck.selectionUnavailable}/>}

                <div
                    className="gokul-menu-tools
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
                        premium={phoneMenu}
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
                                className={phoneMenu ? "mobile-menu-category-entry" : "mt-4"}
                            >

                                {phoneMenu?<MobileMenuFilters showChips={!activeBrowse||!(/sweet|mithai|snack|dairy|drink|beverage|biscuit|namkeen/i.test(activeBrowse.name))} activeId={activeBrowseId} onBrowse={browseMenu} offersAvailable={!!mobileFeatures?.pickupAddOns} categories={categories} selected={mobileCategories??(effectiveCategoryId===null?[]:[effectiveCategoryId])} onCategories={ids=>{setBrowseCategory(null);setBrowseScroll(null);setMobileCategories(ids);}} maximum={maximumPrice} onMaximum={setMaximumPrice} portions={portionsOnly} onPortions={setPortionsOnly} onResetSearch={()=>{if(search)setSearch("");}}/>:<CategoryTabs
                                    categories={
                                        categories
                                    }
                                    selectedCategoryId={
                                        effectiveCategoryId
                                    }
                                    onSelect={
                                        setSelectedCategoryId
                                    }
                                />}

                            </div>
                        )}

                </div>

                {phoneMenu&&<div className={mobileFeatures?.smartAvailability?"mobile-menu-legacy-pickup":""}><PickupContext check={pickupCheck} /></div>}

                {phoneMenu&&pickupCheck.error&&<aside className="menu-date-error" role="alert"><p>{pickupCheck.error}</p><button type="button" onClick={pickupCheck.retry}><T text="Retry availability"/></button></aside>}
                {phoneMenu&&mobileFeatures?.pickupAddOns&&<MenuOffers branchId={branch.id} onTarget={onMenuTarget}/>}

                <div
                    id="gokul-menu-items"
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
                                actionLabel={translate("Change branch")}
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
                                    className="menu-result-heading
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
                                                phoneMenu&&mobileCategories!==null ? mobileCategories.map(id=>categories.find(c=>c.id===id)?.name).filter(Boolean).join(" + ")||translate("All items") : selectedCategory
                                                    ? selectedCategory.name
                                                    : translate("All items")
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
                                                            ? translate("item")
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
                                                <T text="Clear filters" /></button>
                                        )}

                                </div>


                                {phoneMenu&&!hasActiveFilters&&!(/sweet|mithai/i.test(activeBrowse?.name??""))&&<MobileMenuHighlights products={allProducts} retail={retailBrowse} onBrowse={browseMenu}/>}
                                {phoneMenu&&retailBrowse&&<h3 className="menu-retail-collection-title"><T text="Everyday favourites"/></h3>}
                                {pickupCheck.features?.contextualStorefrontV2 ? (phoneMenu&&retailBrowse?retailCollections(categories,filteredProducts):groupMenuProducts(categories, filteredProducts)).map(group => <MenuCategorySection key={phoneMenu?`${group.id}:${activeBrowse?.id??"all"}:${retailBrowse?"retail":"menu"}`:group.id} id={group.id} name={group.name} count={phoneMenu?mobileMenuRows(group.products,portionGroups).length:group.products.length} description={group.description} collapsible={phoneMenu}>
                                <ProductGrid
                                    pairingSeed={pairingSeed}
                                    pairing={retailBrowse?null:pairing}
                                    portionGroups={phoneMenu?portionGroups:undefined}
                                    catalogProducts={phoneMenu?allProducts:undefined}
                                    refined={pickupCheck.features?.contextualStorefrontV2 === true}
                                    pickupItems={pickupCheck.items}
                                    pickupChecking={!!pickupCheck.features?.smartAvailability && !!pickupCheck.intent.date && !pickupCheck.data}
                                    dateAware={!!pickupCheck.features?.smartAvailability}
                                    products={
                                        group.products
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
                                </MenuCategorySection>) : (
                                <ProductGrid
                                    pairingSeed={pairingSeed}
                                    pairing={retailBrowse?null:pairing}
                                    portionGroups={phoneMenu?portionGroups:undefined}
                                    catalogProducts={phoneMenu?allProducts:undefined}
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
                                )}

                                {phoneMenu&&retailBrowse&&!search.trim()&&<RetailSweetRail products={allProducts} onBrowse={browseMenu}/>}
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
                                    <T text="No matching items" /></h2>


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
                                    <T text="Try another search or category to see more of the menu." /></p>


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
                                    <T text="Show all items" /></button>

                            </div>
                        )}

                </div>

            </section>


            {cartNotice
                && (
                    <div
                        role="status"
                        aria-live="polite"
                        className={`${phoneMenu ? "menu-cart-announcement" : ""}
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
                        `}
                    >
                        ✓ {cartNotice}
                    </div>
                )}


            <FloatingCartButton
                offerEnabled={phoneMenu&&!!mobileFeatures?.pickupAddOns} offerTarget={phoneMenu&&menuOffer?.key===offerContext?menuOffer.target:null}
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
    useTranslation();

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
    useTranslation();

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
                <T text="Unable to load menu" /></h2>


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
                <T text="Try again" /></button>

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
    useTranslation();

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
            <LinkFeedback /></Link>

        </div>
    );
}
