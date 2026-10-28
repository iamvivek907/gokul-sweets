"use client";
import {useMenuExperience,SaveDish,DishReviewAction} from "./MenuExperience";
import {dishPhotoPosition} from "@/types/menuDetails";
import {requestMenuPickup} from "@/lib/menuPickupRecovery";
import {T,useTranslation} from "@/lib/language";
import ProductDetailsSheet from "./ProductDetailsSheet";
import FoodPlaceholder from "./FoodPlaceholder";
import {menuMoney,menuDescription,comparisonPrice} from "@/lib/menuPresentationDetails";

import Image from "next/image";
import DietaryLabel from "./DietaryLabel";
import {useState,type ReactNode} from "react";

import type { MenuProduct } from "@/types/menu";
import type { ProductRatingSummary } from "@/types/review";


interface ProductCardProps {
 groupProductIds?:number[];
    dietaryLabel?: ReactNode;
    portionOptions?: ReactNode;
    purchaseControl?: ReactNode;
    refined?: boolean;
    premium?: boolean;
    priceFrom?: boolean;
    unavailableForPickup?: boolean;
    pickupMessage?: string;
    pickupChecking?: boolean;
    product: MenuProduct;
    ratingSummary: Pick<ProductRatingSummary, "averageRating" | "ratingCount"> | null;
    ratingLoading: boolean;
    quantity: number;
    weightGrams: number | null;
    onIncrease: (productId: number) => void;
    onDecrease: (productId: number) => void;
    onAdd: (product: MenuProduct) => void;
}


function formatCurrency(amount: number, exact = false, compact = false): string {

    if(exact || compact)return menuMoney(amount);
    return new Intl.NumberFormat(
        "en-IN",
        {
            style: "currency",
            currency: "INR",
            minimumFractionDigits: compact ? 0 : exact ? 2 : 0,
            maximumFractionDigits: exact ? 2 : 0
        }
    ).format(amount);
}


function formatWeight(
    weightGrams: number
): string {

    if (
        weightGrams >= 1000
    ) {

        return `${new Intl.NumberFormat(
            "en-IN",
            {
                maximumFractionDigits: 3
            }
        ).format(
            weightGrams / 1000
        )} kg`;
    }


    return `${weightGrams} g`;
}


export default function ProductCard({
    dietaryLabel,
    portionOptions,
    purchaseControl,
    refined = false,
    premium = false,
    priceFrom = false,
    unavailableForPickup = false,
    pickupMessage,
    pickupChecking = false,
    product,
    ratingSummary,
    ratingLoading,
    quantity,
    weightGrams,
    onIncrease,
    onDecrease,
    onAdd,
    groupProductIds,
}: ProductCardProps) {
    const t=useTranslation();
    const {details}=useMenuExperience();
    const memberIds=groupProductIds??[product.id];
    const [showDetails,setShowDetails]=useState(false);
    const previous=comparisonPrice(product);
    const description=refined?menuDescription(product):product.description;
    const [failedImageUrl, setFailedImageUrl] = useState<string | null>(null);

    const isAvailable =
        product.available !== false;

    const isWeighted =
        product.saleMode === "WEIGHT";

    const isInCart =
        isWeighted
            ? weightGrams !== null
            : quantity > 0;

    const selectionLabel =
        isWeighted
            ? formatWeight(
                weightGrams
                ?? product.minimumWeightGrams
                ?? 250
            )
            : String(quantity);


    return (
        <>
        <article
            onPointerDownCapture={event=>{if(unavailableForPickup&&!pickupChecking&&pickupMessage!=="Checking pickup"&&product.serviceAvailability?.code!=="CHECKING"&&event.target instanceof Element&&event.target.closest("button:disabled"))window.dispatchEvent(new Event("gokul-pickup-attention"));}}
            className={`
                gokul-product-card group
                ${refined ? "gokul-menu-product-card" : ""}
                flex
                min-h-32
                overflow-hidden
                rounded-2xl
                border
                border-[#eadfd6]
                bg-white
                shadow-[0_4px_16px_rgba(60,30,20,0.07)]
                transition

                hover:-translate-y-0.5
                hover:shadow-[0_10px_26px_rgba(60,30,20,0.11)]

                sm:flex-col
                ${refined ? "sm:rounded-3xl sm:shadow-[0_10px_30px_rgba(60,30,20,0.08)]" : ""}
            `}
        >

            
            {/* ============================================================ */}
            {/* Product Image                                                 */}
            
            {/* ============================================================ */}

            <div
                className="product-card-image
                    relative
                    h-32
                    w-28
                    shrink-0
                    overflow-hidden
                    bg-[#fff0dc]

                    sm:h-auto
                    sm:aspect-[4/3]
                    sm:w-full
                "
            >

                {
                    product.imageUrl && (!refined || failedImageUrl !== product.imageUrl)
                        ? (
                            <Image style={{objectPosition:dishPhotoPosition(details[product.id])}}
                                src={
                                    product.imageUrl
                                }
                                alt={
                                    product.name
                                }
                                fill
                                sizes={refined ? "(max-width: 640px) 136px, (max-width: 768px) 45vw, (max-width: 1024px) 30vw, 280px" : "(max-width: 639px) 112px, (max-width: 768px) 45vw, (max-width: 1024px) 30vw, 280px"}
                                loading="lazy"
                                quality={75}
                                className="
                                    object-cover
                                    transition
                                    duration-300
                                    group-hover:scale-105
                                "
                                draggable={false}
                                onError={refined ? () => setFailedImageUrl(product.imageUrl) : undefined}
                            />
                        )
                        : (
                            <div aria-hidden="true" className="product-image-fallback
                                flex
                                h-full
                                items-center
                                justify-center
                                text-3xl
                                sm:text-4xl
                            ">
                                {refined?<FoodPlaceholder/>:"🍬"}
                            </div>
                        )
                }


                {
                    (!refined && !isAvailable)
                    && (
                        <div className="
                            absolute
                            inset-0
                            z-10
                            flex
                            items-center
                            justify-center
                            bg-black/45
                            p-2
                        ">
                            <span className="
                                rounded-full
                                bg-white
                                px-2.5
                                py-1
                                text-center
                                text-[10px]
                                font-bold
                                text-[#5d0f1b]
                            ">
                                <T text={isAvailable ? pickupMessage ?? "Not at this time" : product.serviceAvailability?.code === "CHECKING" ? "Checking pickup" : product.serviceAvailability?.code === "SOLD_OUT" ? "Sold out" : product.serviceAvailability?.code === "OUTSIDE_SERVICE" ? "Available later" : "Unavailable"}/>
                            </span>
                        </div>
                    )
                }

            </div>


            
            {/* ============================================================ */}
            {/* Product Details                                               */}
            <SaveDish productIds={memberIds} name={product.name}/>
            {/* ============================================================ */}

            <div className="product-card-copy
                flex
                min-w-0
                flex-1
                flex-col
                p-3

                sm:p-3.5
            ">

                <div>{dietaryLabel ?? <DietaryLabel vegetarian={product.vegetarian}/>}</div>
                <p className="
                    truncate
                    text-[9px]
                    font-semibold
                    uppercase
                    tracking-[0.1em]
                    text-[#c88a20]
                ">
                    {
                        product.categoryName
                    }
                </p>


                <h3 className="
                    mt-1
                    line-clamp-2
                    text-[15px]
                    font-bold
                    leading-snug
                    text-[#241715]

                    sm:text-base
                ">
                    {
                        product.name
                    }
                </h3>


                {
                    description
                    && (!refined || description.trim().toLowerCase() !== product.name.trim().toLowerCase())
                    && (
                        <p className="product-card-description
                            mt-1
                            line-clamp-2
                            text-[11px]
                            leading-4
                            text-[#756763]

                            sm:text-xs
                            sm:leading-5
                        ">
                            {
                                description
                            }
                        </p>
                    )
                }


                {(refined ? (!purchaseControl && (!isAvailable || unavailableForPickup)) : (!isAvailable && !!product.serviceAvailability?.message)) && (
                    <p className="menu-availability-note my-1 text-xs leading-5 text-[#a94d39]" role="status"><span>
                        <T text={!isAvailable ? (refined && product.serviceAvailability?.code === "SOLD_OUT" ? "Sold out" : product.serviceAvailability?.message ?? "Unavailable") : pickupMessage ?? "Not at this time"}/>
                    </span></p>
                )}

                {refined&&unavailableForPickup&&product.available&&!purchaseControl&&!pickupChecking&&pickupMessage!=="Checking pickup"&&<button type="button" className="pickup-attention-action" onClick={()=>requestMenuPickup(product.id)}><T text="Choose another pickup"/></button>}
                {refined&&!priceFrom&&<button type="button" className="menu-details-trigger" aria-label={`${t("View details")} · ${product.name}`} onClick={()=>setShowDetails(true)}><T text="View details"/><span aria-hidden="true"> ↗</span></button>}
                {/* Rating */}

                {(!refined||ratingLoading||!!ratingSummary?.ratingCount)&&<div data-has-rating={!!ratingSummary?.ratingCount} className="product-card-rating mt-1.5 flex min-h-5 items-center">

                    {
                        ratingLoading
                            ? (
                                <div
                                    aria-label="Loading product rating"
                                    className="
                                        h-3
                                        w-24
                                        animate-pulse
                                        rounded-full
                                        bg-[#f1e4d8]
                                    "
                                />
                            )
                            : ratingSummary
                              &&
                              ratingSummary.ratingCount > 0
                                ? (
                                    <DishReviewAction productIds={memberIds} name={product.name} summaryLabel={`${ratingSummary.averageRating.toFixed(1)} ${t("out of 5 from")} ${ratingSummary.ratingCount} ${t("ratings")}`}><div
                                        aria-label={`${ratingSummary.averageRating.toFixed(1)} out of 5 from ${ratingSummary.ratingCount} ratings`}
                                        className="product-rating-summary
                                            flex
                                            items-center
                                            gap-1
                                        "
                                    >
                                        <span
                                            aria-hidden="true"
                                            className="
                                                text-sm
                                                leading-none
                                                text-[#d18b13]
                                            "
                                        >
                                            ★
                                        </span>

                                        <span className="
                                            text-[11px]
                                            font-bold
                                            text-[#241715]
                                        ">
                                            {
                                                ratingSummary.averageRating.toFixed(1)
                                            }
                                        </span>

                                        <span className="
                                            text-[10px]
                                            text-[#756763]
                                        ">
                                            ({
                                                ratingSummary.ratingCount
                                            })
                                        </span>
                                    </div></DishReviewAction>
                                )
                                : (
                                    <div className="
                                        flex
                                        items-center
                                        gap-1
                                        text-[10px]
                                        text-[#756763]
                                    ">
                                        <span
                                            aria-hidden="true"
                                            className="
                                                text-sm
                                                leading-none
                                                text-[#d8cbc3]
                                            "
                                        >
                                            ☆
                                        </span>

                                        <span>
                                            <span className={refined?"sr-only":undefined}><T text="No ratings yet" /></span></span>
                                    </div>
                                )
                    }

                </div>}


                {refined&&isWeighted&&isInCart&&<p className="mobile-selected-weight-price">{selectionLabel} · {formatCurrency(product.price*(weightGrams??0)/1000,true)}</p>}
                {portionOptions}
                {/* Price + Add */}

                <div className={`
                    product-card-purchase
                    mt-auto
                    flex
                    items-center
                    justify-between
                    gap-2
                    pt-2.5
                    ${refined ? "flex-wrap" : ""}
                `}>

                    <span data-copyable className="
                        text-sm
                        font-bold
                        text-[#241715]

                        sm:text-base
                    ">
                        {(priceFrom||premium&&isWeighted)&&<><T text="From"/> </>}
                        {(premium||refined)&&!priceFrom&&previous!==null&&<><span className="sr-only"><T text="Regular price"/> </span><s className="menu-comparison-price">{formatCurrency(premium&&isWeighted?previous*(product.minimumWeightGrams??250)/1000:previous,true,true)}</s><span className="sr-only"> <T text="Selling price"/> </span></>}
                        <span className={(premium||refined)&&!priceFrom&&previous!==null?"menu-sale-price":undefined}>{formatCurrency(premium&&isWeighted?product.price*(product.minimumWeightGrams??250)/1000:product.price,refined,premium)}</span>
                        {premium&&!isWeighted&&!priceFrom&&/sweet|mithai|मिठाई/i.test(product.categoryName)&&<span className="menu-unit-price-suffix"> <T text="/ piece"/></span>}

                        {
                            isWeighted && !premium
                            && (
                                <span className="
                                    ml-1
                                    text-[10px]
                                    font-semibold
                                    text-[#756763]

                                    sm:text-xs
                                ">
                                    <T text="/kg" /></span>
                            )
                        }
                    </span>

                    {refined && isWeighted && !premium && <span className="text-[10px] text-[#665550]" aria-label="Minimum weight">
                        <T text="From" />{" "}{formatCurrency(product.price * (product.minimumWeightGrams ?? 250) / 1000, true)} <T text="for" />{" "}{formatWeight(product.minimumWeightGrams ?? 250)}
                    </span>}


                    {premium&&!purchaseControl&&(isInCart||isWeighted)&&<small className="menu-product-caption">{isInCart?<>{isWeighted?selectionLabel:<>{quantity} <T text={quantity===1?"piece":"pieces"}/></>} · {formatCurrency(product.price*(isWeighted?(weightGrams??0)/1000:quantity),true,true)}</>:<T text="Choose weight"/>}</small>}

                    {purchaseControl ?? <div
                        className={`
                            product-card-controls
                            relative
                            h-10
                            shrink-0

                            ${
                                isWeighted
                                    ? "w-28 sm:w-32"
                                    : "w-24 sm:w-28"
                            }
                        `}
                        aria-live="polite"
                    >

                        {/* ADD */}

                        <button
                            data-ordering-target="add"
                            type="button"
                            disabled={!isAvailable || unavailableForPickup}
                            tabIndex={
                                isInCart
                                    ? -1
                                    : 0
                            }
                            aria-hidden={
                                isInCart
                            }
                            onClick={() =>
                                onAdd(
                                    product
                                )
                            }
                            aria-label={`Add ${product.name} to cart`}
                            className={`
                                absolute
                                inset-0
                                rounded-xl
                                border
                                border-[#7a1625]
                                bg-[#8f172b]
                                px-3
                                text-xs
                                font-bold
                                text-white
                                shadow-[0_4px_12px_rgba(122,22,37,0.2)]
                                transition-all
                                duration-200

                                hover:bg-[#6f1020]
                                active:scale-95

                                disabled:cursor-not-allowed
                                disabled:border-[#c9b9b4]
                                disabled:bg-[#f3eeeb]
                                disabled:text-[#9a8a85]

                                ${
                                    isInCart
                                        ? "pointer-events-none scale-90 opacity-0"
                                        : "scale-100 opacity-100"
                                }
                            `}
                        >
                            <T text="ADD" /></button>


                        {/* Quantity */}

                        <div
                            role="group"
                            aria-label={
                                isWeighted
                                    ? `${product.name} weight: ${selectionLabel}`
                                    : `${product.name} quantity: ${selectionLabel}`
                            }
                            aria-hidden={
                                !isInCart
                            }
                            className={`
                                absolute
                                inset-0
                                grid
                                grid-cols-[32px_minmax(0,1fr)_32px]
                                items-center
                                overflow-hidden
                                rounded-xl
                                bg-[#7a1625]
                                text-white
                                shadow-[0_4px_12px_rgba(122,22,37,0.22)]
                                transition-all
                                duration-200

                                ${
                                    isInCart
                                        ? "scale-100 opacity-100"
                                        : "pointer-events-none scale-90 opacity-0"
                                }
                            `}
                        >

                            <button
                                type="button"
                                tabIndex={
                                    isInCart
                                        ? 0
                                        : -1
                                }
                                onClick={() =>
                                    onDecrease(
                                        product.id
                                    )
                                }
                                aria-label={
                                    isWeighted
                                        ? `Decrease ${product.name} by ${product.weightStepGrams ?? 50} grams`
                                        : `Remove one ${product.name}`
                                }
                                className="
                                    h-10
                                    text-lg
                                    font-bold
                                    transition
                                    hover:bg-white/15
                                    active:scale-90
                                "
                            >
                                −
                            </button>


                            {
                                isWeighted
                                    ? (
                                        <button
                                            key={
                                                selectionLabel
                                            }
                                            type="button"
                                            disabled={!isAvailable || unavailableForPickup}
                                            tabIndex={
                                                isInCart
                                                    ? 0
                                                    : -1
                                            }
                                            onClick={() =>
                                                onAdd(
                                                    product
                                                )
                                            }
                                            aria-label={`Change ${product.name} quantity. Currently ${selectionLabel}`}
                                            className="
                                                flex
                                                h-10
                                                min-w-0
                                                flex-col
                                                items-center
                                                justify-center
                                                px-1
                                                text-center
                                                transition
                                                hover:bg-white/15
                                                active:scale-95
                                            "
                                        >
                                            <span className="
                                                max-w-full
                                                truncate
                                                text-[10px]
                                                font-bold
                                                leading-none
                                            ">
                                                {
                                                    selectionLabel
                                                }
                                            </span>

                                            <span className="
                                                mt-0.5
                                                text-[7px]
                                                font-bold
                                                uppercase
                                                tracking-[0.08em]
                                                text-white/80
                                            ">
                                                <T text="Change" /></span>
                                        </button>
                                    )
                                    : (
                                        <span
                                            key={
                                                selectionLabel
                                            }
                                            className="
                                                text-center
                                                text-xs
                                                font-bold
                                            "
                                        >
                                            {
                                                selectionLabel
                                            }
                                        </span>
                                    )
                            }


                            <button
                                type="button"
                                disabled={
                                    !isAvailable || unavailableForPickup
                                }
                                tabIndex={
                                    isInCart
                                        ? 0
                                        : -1
                                }
                                onClick={() =>
                                    onIncrease(
                                        product.id
                                    )
                                }
                                aria-label={
                                    isWeighted
                                        ? `Increase ${product.name} by ${product.weightStepGrams ?? 50} grams`
                                        : `Add one more ${product.name}`
                                }
                                className="
                                    h-10
                                    text-lg
                                    font-bold
                                    transition
                                    hover:bg-white/15
                                    active:scale-90
                                    disabled:cursor-not-allowed
                                    disabled:opacity-50
                                "
                            >
                                +
                            </button>

                        </div>

                    </div>}

                </div>

            </div>

        </article>
        {showDetails&&<ProductDetailsSheet key={product.id} product={product} onClose={()=>setShowDetails(false)}/>}
        </>
    );
}
