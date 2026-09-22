"use client";

import {
    useMemo,
    useSyncExternalStore
} from "react";

import type {
    MenuProduct
} from "@/types/menu";

import type {
    AddToCartResult,
    CartItem
} from "@/types/cart";

import {
    clearStoredCart,
    getCartSnapshot,
    getServerCartSnapshot,
    parseCart,
    saveCart,
    subscribeToCart
} from "@/lib/cartStorage";


interface UseCartResult {
    items: CartItem[];
    branchId: number | null;
    itemCount: number;
    subtotal: number;
    isEmpty: boolean;
    addItem: (
        product: MenuProduct,
        branchId: number,
        weightGrams?: number
    ) => AddToCartResult;
    increaseQuantity: (productId: number) => void;
    decreaseQuantity: (productId: number) => void;
    setItemWeight: (
        productId: number,
        weightGrams: number
    ) => void;
    removeItem: (productId: number) => void;
    clearCart: () => void;
    replaceCartForBranch: (
        product: MenuProduct,
        branchId: number,
        weightGrams?: number
    ) => void;
}


function lineSubtotal(item: CartItem): number {
    return item.product.saleMode === "WEIGHT"
        ? item.product.price * (item.weightGrams ?? 0) / 1000
        : item.product.price * item.quantity;
}


function weightStep(product: MenuProduct): number {
    return product.weightStepGrams ?? 50;
}


function minimumWeight(product: MenuProduct): number {
    return product.minimumWeightGrams ?? 250;
}


export function useCart(): UseCartResult {

    const serializedCart =
        useSyncExternalStore(
            subscribeToCart,
            getCartSnapshot,
            getServerCartSnapshot
        );

    const cart =
        useMemo(
            () => parseCart(serializedCart),
            [serializedCart]
        );

    const itemCount =
        useMemo(
            () => cart.items.reduce(
                (total, item) =>
                    total
                    + (item.product.saleMode === "WEIGHT"
                        ? 1
                        : item.quantity),
                0
            ),
            [cart.items]
        );

    const subtotal =
        useMemo(
            () => cart.items.reduce(
                (total, item) => total + lineSubtotal(item),
                0
            ),
            [cart.items]
        );

    function addItem(
        product: MenuProduct,
        selectedBranchId: number,
        requestedWeightGrams?: number
    ): AddToCartResult {

        if (
            cart.branchId !== null
            && cart.branchId !== selectedBranchId
            && cart.items.length > 0
        ) {
            return "branch-mismatch";
        }

        if (
            product.saleMode === "WEIGHT"
            && requestedWeightGrams === undefined
        ) {
            return "weight-required";
        }

        const existingItem =
            cart.items.find(item => item.product.id === product.id);

        const updatedItems = existingItem
            ? cart.items.map(item => {
                if (item.product.id !== product.id) {
                    return item;
                }

                return product.saleMode === "WEIGHT"
                    ? {
                        ...item,
                        product,
                        quantity: 1,
                        weightGrams: requestedWeightGrams ?? item.weightGrams
                    }
                    : {
                        ...item,
                        product,
                        quantity: item.quantity + 1,
                        weightGrams: null
                    };
            })
            : [
                ...cart.items,
                {
                    product,
                    quantity: 1,
                    weightGrams:
                        product.saleMode === "WEIGHT"
                            ? requestedWeightGrams ?? minimumWeight(product)
                            : null
                }
            ];

        saveCart({
            branchId: selectedBranchId,
            items: updatedItems
        });

        return "added";
    }

    function increaseQuantity(productId: number): void {
        if (cart.branchId === null) {
            return;
        }

        const updatedItems = cart.items.map(item =>
            item.product.id !== productId
                ? item
                : item.product.saleMode === "WEIGHT"
                    ? {
                        ...item,
                        weightGrams:
                            (item.weightGrams ?? minimumWeight(item.product))
                            + weightStep(item.product)
                    }
                    : {
                        ...item,
                        quantity: item.quantity + 1
                    }
        );

        saveCart({branchId: cart.branchId, items: updatedItems});
    }

    function decreaseQuantity(productId: number): void {
        if (cart.branchId === null) {
            return;
        }

        const updatedItems = cart.items
            .map(item => {
                if (item.product.id !== productId) {
                    return item;
                }

                if (item.product.saleMode === "WEIGHT") {
                    const nextWeight =
                        (item.weightGrams ?? minimumWeight(item.product))
                        - weightStep(item.product);

                    return nextWeight < minimumWeight(item.product)
                        ? null
                        : {...item, weightGrams: nextWeight};
                }

                const nextQuantity = item.quantity - 1;
                return nextQuantity <= 0
                    ? null
                    : {...item, quantity: nextQuantity};
            })
            .filter((item): item is CartItem => item !== null);

        if (updatedItems.length === 0) {
            clearStoredCart();
            return;
        }

        saveCart({branchId: cart.branchId, items: updatedItems});
    }

    function setItemWeight(
        productId: number,
        requestedWeightGrams: number
    ): void {

        if (
            cart.branchId === null
        ) {

            return;
        }


        const existingItem =
            cart.items.find(
                item =>
                    item.product.id === productId
            );


        if (
            !existingItem
            ||
            existingItem.product.saleMode !== "WEIGHT"
        ) {

            return;
        }


        const minimum =
            minimumWeight(
                existingItem.product
            );


        const step =
            weightStep(
                existingItem.product
            );


        if (
            !Number.isInteger(
                requestedWeightGrams
            )
            ||
            requestedWeightGrams < minimum
            ||
            (
                requestedWeightGrams
                -
                minimum
            )
            %
            step !== 0
        ) {

            return;
        }


        const updatedItems =
            cart.items.map(
                item =>
                    item.product.id === productId
                        ? {
                            ...item,
                            weightGrams:
                                requestedWeightGrams
                        }
                        : item
            );


        saveCart(
            {
                branchId:
                    cart.branchId,

                items:
                    updatedItems
            }
        );
    }

    function removeItem(productId: number): void {
        const updatedItems =
            cart.items.filter(item => item.product.id !== productId);

        if (updatedItems.length === 0) {
            clearStoredCart();
            return;
        }

        saveCart({branchId: cart.branchId, items: updatedItems});
    }

    function clearCart(): void {
        clearStoredCart();
    }

    function replaceCartForBranch(
        product: MenuProduct,
        selectedBranchId: number,
        requestedWeightGrams?: number
    ): void {
        saveCart({
            branchId: selectedBranchId,
            items: [
                {
                    product,
                    quantity: 1,
                    weightGrams:
                        product.saleMode === "WEIGHT"
                            ? requestedWeightGrams ?? minimumWeight(product)
                            : null
                }
            ]
        });
    }

    return {
        items: cart.items,
        branchId: cart.branchId,
        itemCount,
        subtotal,
        isEmpty: cart.items.length === 0,
        addItem,
        increaseQuantity,
        decreaseQuantity,
        setItemWeight,
        removeItem,
        clearCart,
        replaceCartForBranch
    };
}
