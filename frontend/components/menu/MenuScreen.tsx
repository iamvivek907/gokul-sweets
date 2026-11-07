"use client";
import {standaloneCartItems} from "@/lib/cartMeals";
import MealShortcut from "@/components/meal/MealShortcut";
import {useBranchMeals} from "@/hooks/useBranchMeals";
import MealCollection from "@/components/meal/MealCollection";
import {mealCanBeBuilt,mealProductsForPickup} from "@/lib/mealAvailability";
import BrandLoading from "@/components/common/BrandLoading";
import {datedMenuAvailable} from "@/lib/datedMenuAvailability";
import MenuToolbar,{measureMenuToolbar} from "./MenuToolbar";
import {reportLoadingStage} from "@/lib/customerLoading";
import dynamic from "next/dynamic";
import {getPickupSlotSnapshot} from "@/lib/checkoutStorage";
import {pickupIsFresh} from "@/lib/pickupFreshness";
import {menuFamily,retailCollections} from "@/lib/menuPresentation";
import RetailSweetRail from "./RetailSweetRail";
import MenuExperience from "./MenuExperience";
import SavedDishesHelp from "./SavedDishesHelp";
import {useMenuFavourites} from "@/hooks/useMenuFavourites";
import MenuCampaignBanners from "./MenuCampaignBanners";
import {useBranchMenuAppearance} from "@/hooks/useBranchMenuAppearance";
import MenuCategorySection from "./MenuCategorySection";
import MenuOffers from "./MenuOffers";
import {OPEN_ORDERING_TOUR} from "@/lib/orderingTour";
import FloatingMenuCategories from "./FloatingMenuCategories";
const MobileMenuPickup=dynamic(()=>import("./MobileMenuPickup"));
import MobileMenuSuggestions from "./MobileMenuSuggestions";
import {useMenuServiceRefresh} from "@/hooks/useMenuServiceRefresh";
import LinkFeedback from "@/components/common/LinkFeedback";

import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {usePhoneViewport} from "@/hooks/usePhoneViewport";
import {apiClient} from "@/services/apiClient";
import {matchesMobileFilters,mobileMenuRows,type PortionGroup} from "@/lib/mobileMenu";
import {progressiveBatches} from "@/lib/progressiveBatches";
import {readMenuBrowse,writeMenuBrowse,browserMenuStorage,type MenuBrowseState} from "@/lib/menuBrowseState";
import {matchesMenuSearch,menuSearchRank,normalizeMenuSearch} from "@/lib/menuSearch";
import MobileMenuFilters from "./MobileMenuFilters";
import {T,useTranslation} from "@/lib/language";
import {groupMenuProducts} from "@/lib/menuGroups";

import Link from "next/link";
import BranchMenuGallery from "@/components/menu/BranchMenuGallery";
import BranchMenuAppearance from "@/components/menu/BranchMenuAppearance";
import NewBranchItems from "@/components/menu/NewBranchItems";
import {useDateAvailability} from "@/components/menu/PickupContext";

import {
    type ReactNode,
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
    const translate = useTranslation();
    const mobileFeatures=useStorefrontFeatures();
    const renderStarted = useRef<number | null>(null);
    const lastCatalog=useRef<{branchId:number;categories:MenuCategory[]}|null>(null);
    const [refreshError,setRefreshError]=useState<string|null>(null);
    const [branchTab, setBranchTab] = useState<"menu" | "details">("menu");

    const {
        branch
    } =
        useSelectedBranch();


    const groupedMenu=mobileFeatures?.contextualStorefrontV2===true&&(mobileFeatures.futuristicStorefrontV2===true||mobileFeatures.checkoutExperienceV2===true);
    const appearance = useBranchMenuAppearance(branch?.id ?? null);
    const branchMeals=useBranchMeals(groupedMenu?branch?.id:undefined);
    const appearanceOrder = useMemo(()=>Object.fromEntries((appearance?.categories ?? []).map(c => [c.id, c.order])),[appearance]);

    const {
        items,
        meals,
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
        rawCategories,
        setCategories
    ] =
        useState<MenuCategory[]>(
            []
        );
    // Dated availability owns service-hour decisions. A closed current window must not
    // hide ready stock for a later pickup; the branch's manual catalog toggle still applies.
    const categories = useMemo(() => mobileFeatures?.smartAvailability
        ? rawCategories.map(category => ({...category, products: category.products.map(product => ({
            ...product, available: datedMenuAvailable(product)
        }))})) : rawCategories, [rawCategories, mobileFeatures?.smartAvailability]);
    useEffect(() => {
        if (!categories.length || renderStarted.current === null) return;
        const started = renderStarted.current;
        renderStarted.current = null;
        let paint = 0;
        const frame = requestAnimationFrame(() => {paint = requestAnimationFrame(() => reportLoadingStage("MENU_RENDER", started));});
        return () => {cancelAnimationFrame(frame); cancelAnimationFrame(paint);};
    }, [categories]);
    useMenuServiceRefresh(branch?.id,rawCategories,setCategories);


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
                            controller.signal,
                            catalog => {
                                if (controller.signal.aborted) return;
                                renderStarted.current = performance.now();
                                lastCatalog.current={branchId:selectedBranch.id,categories:catalog};
                                setCategories(catalog);
                                setLoadedBranchId(selectedBranch.id);
                                setLoading(false);
                            }
                        );


                    if (
                        controller.signal.aborted
                    ) {

                        return;
                    }


                    renderStarted.current = performance.now();
                    lastCatalog.current={branchId:selectedBranch.id,categories:result};setRefreshError(null);
                    setCategories(
                        result
                    );

                    // Filters belong to the browsing session, not to a catalog refresh.

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


                    void progressiveBatches(ratingBatches,batch=>getProductRatingSummaries(batch,AbortSignal.any([controller.signal,AbortSignal.timeout(8000)])),(summaries,batch)=>{
                        setRatingState(previous=>{
                            const next={...(previous?.branchId===selectedBranch.id?previous.summaries:{})};
                            // A successful batch is authoritative even when products have no published reviews.
                            for(const productId of batch)delete next[productId];
                            for(const summary of summaries)next[summary.productId]=summary;
                            return {branchId:selectedBranch.id,summaries:next};
                        });
                    },controller.signal).then(()=>{
                        if(!controller.signal.aborted)setRatingState(previous=>previous?.branchId===selectedBranch.id?previous:{branchId:selectedBranch.id,summaries:{}});
                    });

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


                    if(lastCatalog.current?.branchId===selectedBranch.id&&lastCatalog.current.categories.length){
                        setCategories(lastCatalog.current.categories);setError(null);setRefreshError("The menu could not refresh. Your cart is saved. Prices and availability will be checked before ordering.");setLoadedBranchId(selectedBranch.id);setLoading(false);return;
                    }
                    setCategories([]);
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


                return standaloneCartItems({items,meals}).reduce<
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
                items, meals
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


                return standaloneCartItems({items,meals}).reduce<
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
                items, meals
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
    const phoneMenu=phone===true&&groupedMenu;
    const [campaignPosition,setCampaignPosition]=useState<{key:string;index:number}|null>(null);
    const campaignKey=JSON.stringify([branch?.id,appearance?.banners,categories.map(category=>category.id)]);
    const campaignControls={position:campaignPosition?.key===campaignKey?campaignPosition.index:0,onPositionChange:(index:number)=>setCampaignPosition(previous=>previous?.key===campaignKey&&previous.index===index?previous:{key:campaignKey,index})};
    const favourites=useMenuFavourites(branch?.id);
    const [savedOnly,setSavedOnly]=useState(false);
    function changeSavedFilter(value:boolean){setSavedOnly(value);const url=new URL(location.href);if(value){url.searchParams.set("saved","1");url.searchParams.delete("category");}else url.searchParams.delete("saved");if(url.href!==location.href)history.replaceState(history.state,"",url);if(value){setSearch("");setSelectedCategoryId(null);setMobileCategories([]);setBrowseCategory(null);setMaximumPrice(null);setPortionsOnly(false);}}

    const [browseCategory,setBrowseCategory]=useState<{branchId:number;id:number}|null|undefined>(undefined);
    const initialCategoryId=browseCategory===undefined?(effectiveCategoryId??undefined):undefined;
    const activeBrowseId=phoneMenu?(browseCategory?.branchId===branch?.id?browseCategory?.id:initialCategoryId):undefined;
    const activeBrowse=categories.find(c=>c.id===activeBrowseId);
    const retailBrowse=!!activeBrowse&&/snack|dairy|drink|beverage|biscuit|namkeen/i.test(activeBrowse.name);
    const [mobileCategories,setMobileCategories]=useState<number[]|null>(null);
    const [maximumPrice,setMaximumPrice]=useState<number|null>(null);
    const [portionsOnly,setPortionsOnly]=useState(false);
    const [restoreScroll,setRestoreScroll]=useState<{branchId:number;y:number}|null>(null);
    const [restoredBrowse,setRestoredBrowse]=useState<number|null>(null);
    // Keep a search entered before saved browsing state finishes restoring.
    const searchEditedBranch=useRef<number|null>(null);
    const savedBrowse=useRef<{branchId:number;value:MenuBrowseState}|null>(null);
    useEffect(()=>{
        if(!branch||loadedBranchId!==branch.id||restoredBrowse===branch.id)return;
        const saved=readMenuBrowse(browserMenuStorage(),branch.id);
        const parameters=new URLSearchParams(location.search),wantsSaved=parameters.get("saved")==="1";
        const explicit=wantsSaved?"saved":parameters.get("category");
        queueMicrotask(()=>setSavedOnly(wantsSaved));
        const destination=wantsSaved?null:explicit?Number(explicit):saved?.categoryId??null;
        const ids=new Set(categories.map(category=>category.id));
        // Restore UI preferences only. Cart, stock and pickup remain independently authoritative.
        if(searchEditedBranch.current!==branch.id)setSearch(explicit?"":saved?.search??"");
        searchEditedBranch.current=null;
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setSelectedCategoryId(destination&&ids.has(destination)?destination:null);
        setMobileCategories(explicit?(destination&&ids.has(destination)?[destination]:[]):saved?.categories?.filter(id=>ids.has(id))??null);
        setMaximumPrice(explicit?null:saved?.maximum??null);setPortionsOnly(!explicit&&!!saved?.portions);
        setBrowseCategory(!explicit&&saved?(saved.browseId&&ids.has(saved.browseId)?{branchId:branch.id,id:saved.browseId}:null):undefined);
        setRestoredBrowse(branch.id);
        setRestoreScroll({branchId:branch.id,y:!explicit&&saved?saved.scrollY:0});
    },[branch,loadedBranchId,categories,restoredBrowse]);
    useEffect(()=>{
        if(!restoreScroll||restoreScroll.branchId!==loadedBranchId)return;
        let second=0,cancelled=false;const cancel=()=>{cancelled=true;};
        const events=["pointerdown","keydown","wheel","touchstart"];
        events.forEach(event=>window.addEventListener(event,cancel,{capture:true,passive:true}));
        const first=requestAnimationFrame(()=>{second=requestAnimationFrame(()=>{if(!cancelled)window.scrollTo({top:restoreScroll.y,behavior:"instant"});});});
        return()=>{cancelAnimationFrame(first);cancelAnimationFrame(second);events.forEach(event=>window.removeEventListener(event,cancel,true));};
    },[restoreScroll,loadedBranchId]);
    useEffect(()=>{
        if(branch&&restoredBrowse===branch.id)savedBrowse.current={branchId:branch.id,value:{savedAt:Date.now(),search,categoryId:selectedCategoryId,categories:mobileCategories,maximum:maximumPrice,portions:portionsOnly,browseId:browseCategory?.branchId===branch.id?browseCategory.id:null,scrollY:window.scrollY}};
    },[branch,restoredBrowse,search,selectedCategoryId,mobileCategories,maximumPrice,portionsOnly,browseCategory]);
    useEffect(()=>{
        if(!branch||restoredBrowse!==branch.id)return;
        const id=branch.id;let y=readMenuBrowse(browserMenuStorage(),id)?.scrollY??window.scrollY;
        const save=()=>{const current=savedBrowse.current;if(current?.branchId===id)writeMenuBrowse(browserMenuStorage(),id,{...current.value,savedAt:Date.now(),scrollY:y});};
        const scroll=()=>{y=window.scrollY;};
        window.addEventListener("scroll",scroll,{passive:true});window.addEventListener("pagehide",save);
        return()=>{save();window.removeEventListener("scroll",scroll);window.removeEventListener("pagehide",save);};
    },[branch,restoredBrowse]);
    const [portionSnapshot,setPortionSnapshot]=useState<{branchId:number;groups:PortionGroup[]}|null>(null);
    const portionGroups=useMemo(()=>portionSnapshot?.branchId===branch?.id?portionSnapshot?.groups??[]:[],[portionSnapshot,branch?.id]);
    useEffect(()=>{if(!groupedMenu||!branch)return;const controller=new AbortController();void apiClient<{groups:PortionGroup[]}>(`/api/menu/portion-groups?branchId=${branch.id}`,{signal:controller.signal}).then(value=>{if(!controller.signal.aborted)setPortionSnapshot({branchId:branch.id,groups:value.groups??[]});}).catch(()=>{});return()=>controller.abort();},[groupedMenu,branch]);
    const pickupCheck = useDateAvailability(allProducts);
    const pickupChecking = !!pickupCheck.features?.smartAvailability && (pickupCheck.pickupRequired || pickupCheck.findingSoonest || !pickupCheck.items || !!pickupCheck.error || !!pickupCheck.automaticError);
    const pickupStatusMessage = pickupCheck.error || pickupCheck.automaticError ? "Pickup check failed. Please retry."
        : pickupCheck.findingSoonest ? "Finding soonest pickup…"
        : pickupCheck.pickupRequired ? "Choose pickup time to add" : "Checking pickup";
    const pickupAllows = (id:number) => !pickupCheck.features?.smartAvailability || !!pickupCheck.intent.selection && pickupCheck.intent.pickupRaw === getPickupSlotSnapshot() && pickupIsFresh(pickupCheck.intent.selection,new Date()) && !pickupCheck.findingSoonest && !pickupChecking && pickupCheck.items?.find(item=>item.productId===id)?.available === true;
    const [availableOnly,setAvailableOnly]=useState(false);
    const filterByPickup = availableOnly && pickupCheck.features?.smartAvailability === true;
    // The menu may keep showing an earlier preview while cart quantities refresh.
    // Filter only from the current response; per-item results already account for
    // the selected slot without blocking every dish when a cart item has an issue.
    const pickupFilterReady = !!pickupCheck.intent.selection && !!pickupCheck.data && !pickupChecking
        && pickupCheck.intent.pickupRaw === getPickupSlotSnapshot()
        && pickupIsFresh(pickupCheck.intent.selection,new Date());
    const availablePickupIds = useMemo(() => filterByPickup
        ? new Set(pickupFilterReady ? pickupCheck.items?.filter(item=>item.available).map(item=>item.productId) : [])
        : null, [filterByPickup,pickupFilterReady,pickupCheck.items]);
    const visibleMeals = useMemo(() => {
        if (!filterByPickup) return branchMeals;
        if (!pickupFilterReady) return [];
        return branchMeals.filter(meal => mealCanBeBuilt(meal,
            mealProductsForPickup(meal,allProducts,1,pickupCheck.items,false,true)));
    }, [filterByPickup,pickupFilterReady,branchMeals,allProducts,pickupCheck.items]);
    const pickupFilter = pickupCheck.features?.smartAvailability
        ? {checked:availableOnly,onChange:setAvailableOnly} : undefined;
    const catalogForResults = useMemo(() => availablePickupIds
        ? allProducts.filter(product=>product.available && availablePickupIds.has(product.id))
        : allProducts, [allProducts,availablePickupIds]);
    const filteredProducts =
        useMemo(
            () => {

                const query =
                    search
                        .trim()
                        .toLowerCase();


                return catalogForResults.filter(
                    product => {

                        const categoryMatch = phoneMenu&&mobileCategories!==null ? !mobileCategories.length||mobileCategories.includes(product.categoryId) :
                            effectiveCategoryId
                            === null
                            ||
                            product.categoryId
                            === effectiveCategoryId;


                        const searchMatch =
                            matchesMenuSearch(product,query);


                        return (
                            categoryMatch
                            && (!groupedMenu||!savedOnly||favourites.ids.includes(product.id))
                            && (!phoneMenu||!!query||!activeBrowse||(menuFamily(product.categoryName)===menuFamily(activeBrowse.name)))
                            &&
                            searchMatch
                            && (!phoneMenu||matchesMobileFilters(product,null,maximumPrice,portionsOnly,portionGroups))
                        );
                    }
                );
            },
            [
                catalogForResults,
                effectiveCategoryId,
                search,phoneMenu,mobileCategories,maximumPrice,portionsOnly,portionGroups,activeBrowse,groupedMenu,savedOnly,favourites.ids
            ]
        );


    const menuOrderableIds = useMemo(() => pickupCheck.features?.smartAvailability
        ? pickupChecking ? null : new Set(pickupCheck.items?.filter(item=>item.available).map(item=>item.productId))
        : new Set(allProducts.filter(product=>product.available).map(product=>product.id)),
    [pickupCheck.features?.smartAvailability,pickupCheck.items,pickupChecking,allProducts]);
    const menuSections=useMemo(()=>{
        const sections=phoneMenu&&retailBrowse?retailCollections(categories,filteredProducts):groupMenuProducts(categories,filteredProducts);
        if(!groupedMenu)return sections.sort((a,b)=>(appearanceOrder[a.id]??10000)-(appearanceOrder[b.id]??10000));
        const query=normalizeMenuSearch(search);
        const searchRanks=new Map(filteredProducts.map(product=>[product.id,menuSearchRank(product.name,query)]));
        const relevance=(product:MenuProduct)=>searchRanks.get(product.id)??0;
        const orderRank=(product:MenuProduct)=>menuOrderableIds&&(!product.available||!menuOrderableIds.has(product.id))?1:0;
        const ranked=sections.map(section=>{
            section.products.sort((a,b)=>relevance(a)-relevance(b)||orderRank(a)-orderRank(b));
            return {section,match:relevance(section.products[0]),blocked:!section.products.some(product=>orderRank(product)===0)};
        });
        // A precise search stays above a looser match, even when it needs another pickup.
        return ranked.sort((a,b)=>a.match-b.match||Number(a.blocked)-Number(b.blocked)
            || (appearanceOrder[a.section.id]??10000)-(appearanceOrder[b.section.id]??10000)).map(value=>value.section);
    },[phoneMenu,retailBrowse,categories,filteredProducts,groupedMenu,appearanceOrder,search,menuOrderableIds]);

    useEffect(()=>{if(!branch||!new URLSearchParams(window.location.search).has("mealEdit"))return;queueMicrotask(()=>{setSearch("");setSelectedCategoryId(null);setMobileCategories([]);setMaximumPrice(null);setPortionsOnly(false);setBrowseCategory(null);setSavedOnly(false);const url=new URL(location.href);url.searchParams.delete("saved");url.searchParams.delete("category");history.replaceState(history.state,"",url);});},[branch]);

    const [browseScroll,setBrowseScroll]=useState<{branchId:number;id:number;revision:number}|null>(null);
    const completedBrowseScroll=useRef<object|null>(null);
    function browseMenu(id:number|null){
        changeSavedFilter(false);
        setBrowseCategory(id===null||!branch?null:{branchId:branch.id,id});
        setMobileCategories([]);setMaximumPrice(null);setPortionsOnly(false);setSearch("");
        setBrowseScroll(previous=>id===null||!branch?null:{branchId:branch.id,id,revision:(previous?.revision??0)+1});
    }
    function jumpMenuCategory(id:number|null){
        changeSavedFilter(false);
        setBrowseCategory(null);setSelectedCategoryId(id);setMobileCategories(id===null?[]:[id]);
        setMaximumPrice(null);setPortionsOnly(false);setSearch("");
        if(branch)setBrowseScroll(previous=>({branchId:branch.id,id:id??0,revision:(previous?.revision??0)+1}));
    }
    // Scroll only after React has committed the cleared search and destination collection.
    useEffect(()=>{
        if(!browseScroll||completedBrowseScroll.current===browseScroll||browseScroll.branchId!==branch?.id)return;
        const frame=requestAnimationFrame(()=>{
            const section=document.getElementById(browseScroll.id===0?"gokul-menu-items":`menu-category-${browseScroll.id}`);
            if(!section)return;
            completedBrowseScroll.current=browseScroll;
            const disclosure=section.querySelector("details");if(disclosure)disclosure.open=true;
            const toolbar=document.querySelector<HTMLElement>(".mobile-menu-sticky-tools");
            if(phoneMenu&&toolbar)measureMenuToolbar(toolbar);
            section.scrollIntoView({block:"start",behavior:matchMedia("(prefers-reduced-motion: reduce)").matches?"instant":"smooth"});
        });
        return()=>cancelAnimationFrame(frame);
    },[phoneMenu,browseScroll,branch?.id,filteredProducts]);

    const [searchDestination,setSearchDestination]=useState<{branchId:number;productId:number}|null>(null);
    function goToSearchDish(product:MenuProduct){
        if(!branch)return;
        clearFilters();
        searchEditedBranch.current=branch.id;
        setSearch(product.name);
        setSearchDestination({branchId:branch.id,productId:product.id});
    }
    useEffect(()=>{
        if(!searchDestination||searchDestination.branchId!==branch?.id)return;
        let timer:ReturnType<typeof setTimeout>|undefined;
        let target:HTMLElement|null=null;
        const frame=requestAnimationFrame(()=>{
            const anchor=document.getElementById(`gokul-product-${searchDestination.productId}`);
            target=anchor?.closest<HTMLElement>("[data-menu-product-card]")??null;
            if(!target)return;
            const category=target.closest(".menu-category-disclosure");
            if(category instanceof HTMLDetailsElement)category.open=true;
            target.setAttribute("data-search-arrival","");
            target.focus({preventScroll:true});
            target.scrollIntoView({block:"center",behavior:"instant"});
            timer=setTimeout(()=>target?.removeAttribute("data-search-arrival"),1700);
        });
        return()=>{cancelAnimationFrame(frame);clearTimeout(timer);target?.removeAttribute("data-search-arrival");};
    },[searchDestination,branch?.id]);

    const hasActiveFilters =
        filterByPickup ||
        (groupedMenu&&savedOnly) ||
        search.trim().length > 0
        ||
        (phoneMenu ? (!!(mobileCategories??(effectiveCategoryId===null?[]:[effectiveCategoryId])).length || maximumPrice!==null || portionsOnly) : effectiveCategoryId !== null);

    const guardedIncrease = (id:number) => {if(pickupAllows(id))increaseQuantity(id);};
    const pickupGrid = (products:MenuProduct[],afterLeadingRows?:ReactNode) => <ProductGrid afterLeadingRows={afterLeadingRows} pairingSeed={pairingSeed} pairing={retailBrowse?null:pairing}
        portionGroups={groupedMenu?portionGroups:undefined} catalogProducts={catalogForResults} compactGroups={phoneMenu}
        refined={pickupCheck.features?.contextualStorefrontV2 === true} pickupItems={pickupCheck.items}
        pickupChecking={pickupChecking} pickupStatusMessage={pickupStatusMessage} dateAware={!!pickupCheck.features?.smartAvailability} products={products}
        ratingSummaries={ratingSummaries} ratingsLoading={ratingsLoading} quantities={productQuantities} weights={productWeights}
        onIncrease={guardedIncrease} onDecrease={decreaseQuantity} onAdd={handleAddToCart}/>;
    const [lastAdded,setLastAdded]=useState<number|null>(null);
    const pairingSeed=lastAdded??items.at(-1)?.product.id;
    const basketPickup = useMemo(()=>{
        if(!pickupCheck.features?.smartAvailability||!pickupCheck.intent.selection)return "unknown";
        const decisions=new Map(pickupCheck.items?.map(item=>[item.productId,item.available]));
        if(pickupCheck.selectionUnavailable||items.some(item=>decisions.get(item.product.id)===false))return "blocked";
        return pickupCheck.data&&!pickupChecking&&items.every(item=>decisions.get(item.product.id)===true)?"ready":"unknown";
    },[pickupCheck.features?.smartAvailability,pickupCheck.intent.selection,pickupCheck.items,pickupCheck.selectionUnavailable,pickupCheck.data,pickupChecking,items]);
    const pairing=phoneMenu&&mobileFeatures?.pickupAddOns&&branch&&items.some(i=>i.product.id===pairingSeed)&&!filterByPickup&&filteredProducts.some(p=>p.id===pairingSeed)?<div className="mobile-menu-pairing-slot"><MobileMenuSuggestions seedProduct={allProducts.find(p=>p.id===pairingSeed)} pairings={appearance?.pairings} onPairingAdded={()=>setLastAdded(pairingSeed??null)} branchId={branch.id} products={allProducts} groups={portionGroups} pickupItems={pickupCheck.items} basketPickup={basketPickup}/></div>:null;

    const weightPickup = useRef("");
    function handleAddToCart(
        product: MenuProduct
    ) {

        if (!product.available || !pickupAllows(product.id)) return;
        if (product.saleMode === "WEIGHT") {
            weightPickup.current=getPickupSlotSnapshot();
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

        if (weightGrams && pickupCheck.features?.smartAvailability && weightPickup.current!==getPickupSlotSnapshot()) {setCartNotice("Pickup changed. Review the displayed time before adding.");return;}
        const liveProduct=categories.flatMap(category=>category.products).find(item=>item.id===product.id);
        if(!liveProduct?.available || !pickupAllows(product.id)){setWeightProduct(null);setCartNotice(liveProduct?.serviceAvailability?.message??"This item is currently unavailable.");return;}
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

        if(!categories.length)setLoading(true);

        setError(
            null
        );

        setReloadKey(
            current =>
                current + 1
        );
    }


    function clearFilters() {
        setAvailableOnly(false);
        changeSavedFilter(false);
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


    if (isLoading) return <section className="menu-loading-shell" aria-label={translate("Loading menu")}><h1><T text="Your branch menu"/></h1><BrandLoading compact className="customer-menu-loading" label="Loading menu" detail="Loading dishes. Your cart and pickup are saved."/><ProductSkeletonGrid/></section>;

    return (
        <MenuExperience branchId={branch?.id} products={allProducts} enabled={groupedMenu}>
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

                {!pickupCheck.features?.contextualStorefrontV2&&<header
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

                </header>}


                {refreshError&&<aside className="menu-refresh-notice" role="status"><T text={refreshError}/><button type="button" onClick={retryMenu}><T text="Try again"/></button></aside>}
                {groupedMenu&&!!appearance?.banners.length&&<div className="menu-leading-campaign"><MenuCampaignBanners {...campaignControls} banners={appearance.banners} categories={categories} onSelect={id => {jumpMenuCategory(id);setBrowseCategory({branchId:branch.id,id});}} compact={phoneMenu}/></div>}
                <MenuToolbar enabled={phoneMenu}>
                {!mobileFeatures?.smartAvailability&&(mobileFeatures?.futuristicStorefrontV2||mobileFeatures?.checkoutExperienceV2)&&<button type="button" className="menu-pickup-help ordering-tour-replay" aria-label={translate("How to order")} onClick={()=>window.dispatchEvent(new Event(OPEN_ORDERING_TOUR))}><span aria-hidden="true">?</span></button>}
                {mobileFeatures?.smartAvailability&&<MobileMenuPickup showHelp={!!(mobileFeatures?.futuristicStorefrontV2||mobileFeatures?.checkoutExperienceV2)} compactHeader={phoneMenu} key={branch.id} branchId={branch.id} products={allProducts} today={pickupCheck.today} days={mobileFeatures.futureOrderingDays??30} selection={pickupCheck.intent.selection} date={pickupCheck.intent.date} expired={pickupCheck.intent.expired} selectionUnavailable={pickupCheck.selectionUnavailable} noPickupMessage={pickupCheck.noPickupMessage} availabilityError={pickupCheck.automaticError??pickupCheck.error} onRetry={pickupCheck.retry} automatic={pickupCheck.intent.automatic} findingSoonest={pickupCheck.findingSoonest} onChoosingChange={pickupCheck.setChoosing}/>}

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

                    <MenuSearch key={branch.id} enhanced={groupedMenu} products={allProducts} branchId={branch.id} topOnTap={phoneMenu}
                        onSelectProduct={groupedMenu?goToSearchDish:undefined}
                        premium={groupedMenu}
                        refined={pickupCheck.features?.contextualStorefrontV2 === true}
                        value={
                            search
                        }
                        onChange={value => {
                            if(restoredBrowse!==branch.id)searchEditedBranch.current=branch.id;
                            setSearch(value);
                        }}
                    />


                    {groupedMenu&&!phoneMenu&&<button type="button" className="menu-saved-filter" aria-pressed={savedOnly} onClick={()=>changeSavedFilter(!savedOnly)}><T text="Saved"/></button>}
                    {groupedMenu&&savedOnly&&<SavedDishesHelp/>}
                    {((!isLoading && !error && categories.length > 0)
                        || (phoneMenu && mobileFeatures?.pickupAddOns))
                        && (
                            <div
                                className={phoneMenu ? "mobile-menu-category-entry" : "mt-4"}
                            >

                                {!phoneMenu&&pickupFilter&&<label className="flex min-h-11 items-center gap-2"><input type="checkbox" checked={availableOnly} onChange={event=>setAvailableOnly(event.target.checked)}/><T text="Available at selected pickup time"/></label>}
                                {phoneMenu?<MobileMenuFilters pickupFilter={pickupFilter} mealAction={visibleMeals.length?<MealShortcut onClick={()=>{if(!filterByPickup)clearFilters();requestAnimationFrame(()=>{const target=document.getElementById("menu-meals");const disclosure=target?.closest("details");if(disclosure)disclosure.open=true;target?.scrollIntoView({block:"start",behavior:matchMedia("(prefers-reduced-motion: reduce)").matches?"instant":"smooth"});target?.focus({preventScroll:true});});}}/>:undefined} allSelected={!savedOnly} endAction={<button className="menu-saved-filter" type="button" aria-pressed={savedOnly} onClick={()=>changeSavedFilter(!savedOnly)}><T text="Saved"/></button>} activeId={activeBrowseId} onBrowse={browseMenu} offerAction={mobileFeatures?.pickupAddOns?<MenuOffers key={branch.id} branchId={branch.id}/>:undefined} categories={categories} selected={mobileCategories??(effectiveCategoryId===null?[]:[effectiveCategoryId])} onCategories={ids=>{setBrowseCategory(null);setBrowseScroll(null);setMobileCategories(ids);}} maximum={maximumPrice} onMaximum={setMaximumPrice} portions={portionsOnly} onPortions={setPortionsOnly} onResetSearch={()=>{if(search)setSearch("");}}/>:<CategoryTabs
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
                </MenuToolbar>
                {!phoneMenu&&groupedMenu&&appearance&&<BranchMenuAppearance config={{...appearance,banners:[]}} categories={categories} onSelect={id=>jumpMenuCategory(id)}/>}
                {!phoneMenu && !groupedMenu && <BranchMenuAppearance {...campaignControls} config={appearance} categories={categories} onSelect={id=>jumpMenuCategory(id)}/>}



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


                    {!isLoading&&!error&&groupedMenu&&visibleMeals.length>0&&filteredProducts.length===0&&<MealCollection key={branch.id} branchId={branch.id} products={allProducts} meals={visibleMeals} smartAvailability={mobileFeatures?.smartAvailability===true} pickupItems={pickupCheck.items} pickupChecking={pickupChecking} pickupSelected={!!pickupCheck.intent.selection} pickupFailed={!!(pickupCheck.error||pickupCheck.automaticError)} onRetryPickup={pickupCheck.retry}/>}

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
                                    data-filtered={hasActiveFilters} className="menu-result-heading
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



                                {phoneMenu&&retailBrowse&&<h3 className="menu-retail-collection-title"><T text="Everyday favourites"/></h3>}
                                {pickupCheck.features?.contextualStorefrontV2 ? <div>
                                    {menuSections.map((group,index)=><MenuCategorySection key={phoneMenu?`${group.id}:${activeBrowse?.id??"all"}:${retailBrowse?"retail":"menu"}`:group.id} id={group.id} name={group.name} displayName={phoneMenu&&activeBrowse&&menuFamily(activeBrowse.name)==="Sweets"&&group.id===activeBrowse.id?"Sweets you’ll love":undefined} count={phoneMenu?mobileMenuRows(group.products,portionGroups).length:group.products.length} description={group.description} collapsible={phoneMenu}>{pickupGrid(group.products,groupedMenu&&index===0&&visibleMeals.length>0?<MealCollection key={branch.id} branchId={branch.id} products={allProducts} meals={visibleMeals} smartAvailability={mobileFeatures?.smartAvailability===true} pickupItems={pickupCheck.items} pickupChecking={pickupChecking} pickupSelected={!!pickupCheck.intent.selection} pickupFailed={!!(pickupCheck.error||pickupCheck.automaticError)} onRetryPickup={pickupCheck.retry}/>:undefined)}</MenuCategorySection>)}
                                </div> : (
                                <ProductGrid
                                    pairingSeed={pairingSeed}
                                    pairing={retailBrowse?null:pairing}
                                    portionGroups={groupedMenu?portionGroups:undefined}
                                    catalogProducts={allProducts} compactGroups={phoneMenu}
                                    refined={pickupCheck.features?.contextualStorefrontV2 === true}
                                    pickupItems={pickupCheck.items}
                                    pickupChecking={pickupChecking} pickupStatusMessage={pickupStatusMessage}
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
                                        guardedIncrease
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
                        && (!filterByPickup || visibleMeals.length === 0)
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
                                    <T text={filterByPickup&&!pickupFilterReady?"Pickup availability is not ready":"No matching items"} /></h2>


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
                                    <T text={filterByPickup ? (pickupFilterReady ? "No items match at this pickup time. Try another time or clear your filters." : "Choose a pickup time or retry the availability check.") : "Try another search or category to see more of the menu."} /></p>


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

                {pickupCheck.features?.contextualStorefrontV2 && !filterByPickup && !isLoading && !error &&
                    <NewBranchItems eligibleIds={pickupCheck.features?.smartAvailability?allProducts.filter(p=>pickupAllows(p.id)).map(p=>p.id):undefined} branch={branch} products={allProducts} portionGroups={groupedMenu?portionGroups:undefined} onSelect={product => {
                        changeSavedFilter(false);setSearch("");setBrowseCategory(null);
                        setSelectedCategoryId(product.categoryId);
                        if(phoneMenu){setMobileCategories([product.categoryId]);setMaximumPrice(null);setPortionsOnly(false);}
                        const group=portionGroups.find(g=>g.choices.some(c=>c.productId===product.id));
                        const target=group?.choices.find(c=>allProducts.some(p=>p.id===c.productId))?.productId??product.id;
                        requestAnimationFrame(() =>
                            document.getElementById(`gokul-product-${target}`)?.scrollIntoView({behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth", block: "center"}));
                    }} />}

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


            {(mobileFeatures?.futuristicStorefrontV2===true||mobileFeatures?.checkoutExperienceV2===true)&&mobileFeatures?.contextualStorefrontV2===true&&!error&&categories.length>0&&<FloatingMenuCategories key={branch.id} categories={categories} selectedId={phoneMenu?(mobileCategories?.length===1?mobileCategories[0]:undefined):(effectiveCategoryId??undefined)} hasCart={itemCount>0} onSelect={jumpMenuCategory}/>}
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
        </MenuExperience>
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
