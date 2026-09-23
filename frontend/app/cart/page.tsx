"use client";
import PickupContext, {useDateAvailability} from "@/components/menu/PickupContext";

import {
    useState
} from "react";

import {
    useRouter
} from "next/navigation";

import Link
    from "next/link";

import AppShell
    from "@/components/layout/AppShell";

import CartItem
    from "@/components/cart/CartItem";

import CartSummary
    from "@/components/cart/CartSummary";

import EmptyCart
    from "@/components/cart/EmptyCart";

import WeightSelectorSheet
    from "@/components/menu/WeightSelectorSheet";

import {
    useCart
} from "@/hooks/useCart";

import {
    useSelectedBranch
} from "@/hooks/useSelectedBranch";

import type {
    CartItem as CartItemType
} from "@/types/cart";


export default function CartPage() {
    const pickupCheck = useDateAvailability();

    const router =
        useRouter();


    const {
        branch
    } =
        useSelectedBranch();


    const {
        items,
        branchId,
        itemCount,
        subtotal,
        isEmpty,
        increaseQuantity,
        decreaseQuantity,
        setItemWeight,
        removeItem,
        clearCart
    } =
        useCart();


    const [
        weightItem,
        setWeightItem
    ] =
        useState<CartItemType | null>(
            null
        );


    const cartMatchesSelectedBranch =
        branch !== null
        &&
        branchId !== null
        &&
        branch.id === branchId;


    function handleClearCart() {

        const confirmed =
            window.confirm(
                "Remove all items from your cart?"
            );


        if (
            !confirmed
        ) {

            return;
        }


        setWeightItem(
            null
        );


        clearCart();
    }


    function handleRemoveItem(
        productId: number
    ): void {

        if (
            weightItem?.product.id === productId
        ) {

            setWeightItem(
                null
            );
        }


        removeItem(
            productId
        );
    }


    function handleContinue() {

        if (
            !cartMatchesSelectedBranch
        ) {

            window.alert(
                "Please select the pickup branch associated with this cart before continuing."
            );

            return;
        }


        router.push(
            "/checkout/pickup"
        );
    }


    return (
        <AppShell>
            <PickupContext check={pickupCheck} cart />

            <section
                className="
                    mx-auto
                    w-full
                    min-w-0
                    max-w-[1180px]
                    px-4
                    pb-28
                    pt-5
                    sm:px-6
                    sm:pt-7
                "
            >

                <div
                    className="
                        flex
                        items-end
                        justify-between
                        gap-4
                    "
                >

                    <div
                        className="
                            min-w-0
                        "
                    >

                        <p
                            className="
                                text-xs
                                font-semibold
                                uppercase
                                tracking-[0.16em]
                                text-[#c88a20]
                            "
                        >
                            Your pickup order
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
                            Cart
                        </h1>


                        {!isEmpty
                            && (
                                <p
                                    className="
                                        mt-2
                                        text-sm
                                        text-[#756763]
                                    "
                                >
                                    {itemCount} {itemCount === 1 ? "item" : "items"} ready for review
                                </p>
                            )}

                    </div>


                    {!isEmpty
                        && (
                            <button
                                type="button"
                                onClick={
                                    handleClearCart
                                }
                                className="
                                    shrink-0
                                    rounded-lg
                                    px-2
                                    py-2
                                    text-sm
                                    font-semibold
                                    text-[#7a1625]
                                    transition

                                    hover:bg-[#fff0dc]
                                    active:scale-[0.98]
                                "
                            >
                                Clear cart
                            </button>
                        )}

                </div>


                {isEmpty
                    ? (
                        <div
                            className="
                                mt-6
                            "
                        >
                            <EmptyCart />
                        </div>
                    )
                    : (
                        <>

                            <div
                                className={`
                                    mt-6
                                    rounded-2xl
                                    border
                                    p-4

                                    ${
                                        cartMatchesSelectedBranch
                                            ? "border-[#eadfd6] bg-white"
                                            : "border-[#e5b768] bg-[#fff7e8]"
                                    }
                                `}
                            >

                                <div
                                    className="
                                        flex
                                        min-w-0
                                        items-start
                                        justify-between
                                        gap-4
                                    "
                                >

                                    <div
                                        className="
                                            min-w-0
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
                                            Selected pickup branch
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
                                            {
                                                branch
                                                    ? branch.name
                                                    : "No branch selected"
                                            }
                                        </p>


                                        {branch?.address
                                            && (
                                                <p
                                                    className="
                                                        mt-1
                                                        line-clamp-2
                                                        text-xs
                                                        leading-5
                                                        text-[#756763]
                                                    "
                                                >
                                                    {branch.address}
                                                </p>
                                            )}

                                    </div>


                                    <Link
                                        href="/"
                                        className="
                                            shrink-0
                                            text-sm
                                            font-semibold
                                            text-[#7a1625]
                                            hover:underline
                                        "
                                    >
                                        Change
                                    </Link>

                                </div>


                                {!cartMatchesSelectedBranch
                                    && (
                                        <div
                                            className="
                                                mt-4
                                                rounded-xl
                                                bg-white/70
                                                px-3
                                                py-3
                                            "
                                        >

                                            <p
                                                className="
                                                    text-sm
                                                    font-bold
                                                    text-[#7a1625]
                                                "
                                            >
                                                Cart branch mismatch
                                            </p>


                                            <p
                                                className="
                                                    mt-1
                                                    text-xs
                                                    leading-5
                                                    text-[#756763]
                                                "
                                            >
                                                These cart items belong to another pickup branch.
                                                Select the matching branch before continuing, or clear
                                                the cart and start a new order from this branch.
                                            </p>

                                        </div>
                                    )}

                            </div>


                            <div
                                className="
                                    mt-6
                                    grid
                                    min-w-0
                                    gap-6
                                    lg:grid-cols-[minmax(0,1fr)_360px]
                                "
                            >

                                <div
                                    className="
                                        min-w-0
                                        space-y-4
                                    "
                                >

                                    {
                                        items.map(
                                            item => (
                                                <CartItem
                                                    key={
                                                        item.product.id
                                                    }
                                                    item={
                                                        item
                                                    }
                                                    onIncrease={
                                                        increaseQuantity
                                                    }
                                                    onDecrease={
                                                        decreaseQuantity
                                                    }
                                                    onChangeWeight={
                                                        setWeightItem
                                                    }
                                                    onRemove={
                                                        handleRemoveItem
                                                    }
                                                />
                                            )
                                        )
                                    }


                                    <Link
                                        href="/menu"
                                        className="
                                            inline-flex
                                            min-h-11
                                            items-center
                                            text-sm
                                            font-semibold
                                            text-[#7a1625]
                                            hover:underline
                                        "
                                    >
                                        ← Add more items
                                    </Link>

                                </div>


                                <div
                                    className="
                                        min-w-0
                                        lg:sticky
                                        lg:top-24
                                        lg:self-start
                                    "
                                >

                                    <CartSummary
                                        itemCount={
                                            itemCount
                                        }
                                        subtotal={
                                            subtotal
                                        }
                                        canContinue={
                                            cartMatchesSelectedBranch
                                        }
                                        onContinue={
                                            handleContinue
                                        }
                                    />

                                </div>

                            </div>

                        </>
                    )}

            </section>


            {weightItem
                && (
                    <WeightSelectorSheet
                        key={`${weightItem.product.id}-${weightItem.weightGrams ?? "minimum"}`}
                        product={
                            weightItem.product
                        }
                        currentWeightGrams={
                            weightItem.weightGrams
                            ?? weightItem.product.minimumWeightGrams
                            ?? 250
                        }
                        onClose={
                            () =>
                                setWeightItem(
                                    null
                                )
                        }
                        onConfirm={
                            (
                                product,
                                weightGrams
                            ) => {

                                setItemWeight(
                                    product.id,
                                    weightGrams
                                );


                                setWeightItem(
                                    null
                                );
                            }
                        }
                    />
                )}

        </AppShell>
    );
}
