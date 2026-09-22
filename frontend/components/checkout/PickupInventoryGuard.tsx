"use client";

import Link from "next/link";
import {useEffect, useMemo, useState} from "react";
import {checkInventory} from "@/services/inventoryApi";
import type {CartItem} from "@/types/cart";
import type {InventoryCheckResponse} from "@/types/inventory";

interface Props {
    branchId: number;
    branchPhone: string | null;
    serviceDate: string;
    items: CartItem[];
    onStateChange: (checking: boolean, orderable: boolean) => void;
    onUseSuggestedDate: (date: string) => void;
}

function formatDate(value: string): string {
    return new Intl.DateTimeFormat("en-IN", {
        weekday: "short",
        day: "numeric",
        month: "short",
        year: "numeric",
        timeZone: "Asia/Kolkata"
    }).format(new Date(`${value}T00:00:00+05:30`));
}

function formatQuantity(value: number, unit: string | null): string {
    if (unit === "GRAM") {
        return value >= 1000
            ? `${value / 1000} kg`
            : `${value} g`;
    }

    return `${value} ${value === 1 ? "item" : "items"}`;
}

export default function PickupInventoryGuard({
    branchId,
    branchPhone,
    serviceDate,
    items,
    onStateChange,
    onUseSuggestedDate
}: Props) {
    const [result, setResult] =
        useState<InventoryCheckResponse | null>(null);

    const [error, setError] =
        useState<string | null>(null);

    const requestKey = useMemo(
        () =>
            JSON.stringify({
                branchId,
                serviceDate,
                items: items.map(item => ({
                    productId: item.product.id,
                    quantity:
                        item.product.saleMode === "UNIT"
                            ? item.quantity
                            : null,
                    weightGrams:
                        item.product.saleMode === "WEIGHT"
                            ? item.weightGrams
                            : null
                }))
            }),
        [branchId, serviceDate, items]
    );

    const [completedRequestKey, setCompletedRequestKey] =
        useState<string | null>(null);

    const [errorRequestKey, setErrorRequestKey] =
        useState<string | null>(null);

    useEffect(() => {
        const controller = new AbortController();

        onStateChange(true, false);

        void checkInventory(
            branchId,
            {
                serviceDate,
                items: items.map(item => ({
                    productId: item.product.id,
                    quantity:
                        item.product.saleMode === "UNIT"
                            ? item.quantity
                            : null,
                    weightGrams:
                        item.product.saleMode === "WEIGHT"
                            ? item.weightGrams
                            : null
                }))
            },
            controller.signal
        )
            .then(response => {
                if (controller.signal.aborted) return;

                setResult(response);
                setError(null);
                setCompletedRequestKey(requestKey);
                setErrorRequestKey(null);

                onStateChange(
                    false,
                    response.orderable
                );
            })
            .catch(exception => {
                if (controller.signal.aborted) return;

                setResult(null);
                setError(
                    exception instanceof Error
                        ? exception.message
                        : "Unable to check product availability."
                );
                setCompletedRequestKey(null);
                setErrorRequestKey(requestKey);

                onStateChange(false, false);
            });

        return () => controller.abort();
    }, [
        branchId,
        serviceDate,
        items,
        requestKey,
        onStateChange
    ]);

    const isChecking =
        completedRequestKey !== requestKey
        && errorRequestKey !== requestKey;

    const currentError =
        errorRequestKey === requestKey
            ? error
            : null;

    const currentResult =
        completedRequestKey === requestKey
            ? result
            : null;

    if (isChecking) {
        return (
            <div className="mt-4 rounded-2xl border border-[#eadfd6] bg-white p-4 text-sm text-[#756763]">
                Checking product availability for this date…
            </div>
        );
    }

    if (currentError) {
        return (
            <div className="mt-4 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
                <p className="font-bold">
                    Availability could not be confirmed
                </p>

                <p className="mt-1">
                    {currentError}
                </p>
            </div>
        );
    }

    if (!currentResult || !currentResult.enforcementEnabled) {
        return null;
    }

    if (currentResult.orderable) {
        return (
            <div className="mt-4 rounded-2xl border border-green-200 bg-green-50 p-4 text-sm font-semibold text-green-800">
                ✓ Every cart item is available for{" "}
                {formatDate(serviceDate)}.
            </div>
        );
    }

    const unavailable =
        currentResult.items.filter(
            item => !item.orderable
        );

    return (
        <div className="mt-4 rounded-3xl border border-amber-200 bg-[#fff8e8] p-5">
            <p className="text-base font-extrabold text-[#7a1625]">
                Some items need your attention
            </p>

            <div className="mt-3 space-y-3">
                {unavailable.map(item => (
                    <div
                        key={item.productId}
                        className="rounded-2xl bg-white p-4 text-sm"
                    >
                        <p className="font-bold text-[#241715]">
                            {item.productName}
                        </p>

                        <p className="mt-1 text-[#756763]">
                            Requested:{" "}
                            {formatQuantity(
                                item.requestedQuantity,
                                item.inventoryUnit
                            )}{" "}
                            · Available:{" "}
                            {formatQuantity(
                                item.availableQuantity,
                                item.inventoryUnit
                            )}
                        </p>

                        {item.unavailableReason && (
                            <p className="mt-1 text-[#7a1625]">
                                {item.unavailableReason}
                            </p>
                        )}
                    </div>
                ))}
            </div>

            {currentResult.suggestedDate && (
                <button
                    type="button"
                    onClick={() =>
                        onUseSuggestedDate(
                            currentResult.suggestedDate!
                        )
                    }
                    className="mt-4 min-h-12 w-full rounded-xl bg-[#7a1625] px-5 text-sm font-bold text-white!"
                >
                    Choose{" "}
                    {formatDate(
                        currentResult.suggestedDate
                    )}{" "}
                    instead
                </button>
            )}

            {currentResult.callBranchRecommended && (
                <div className="mt-4 rounded-2xl border border-[#eadfd6] bg-white p-4">
                    <p className="text-sm font-bold text-[#241715]">
                        Planning a large order?
                    </p>

                    <p className="mt-1 text-sm leading-6 text-[#756763]">
                        No online date within the current
                        booking window can confirm the
                        complete quantity. Please call the
                        branch so the team can check
                        production capacity.
                    </p>

                    {branchPhone && (
                        <a
                            href={`tel:${branchPhone}`}
                            className="mt-3 flex min-h-11 w-full items-center justify-center rounded-xl border border-[#7a1625] text-sm font-bold text-[#7a1625]!"
                        >
                            Call {branchPhone}
                        </a>
                    )}
                </div>
            )}

            <Link
                href="/cart"
                className="mt-3 flex min-h-11 w-full items-center justify-center text-sm font-bold text-[#7a1625]!"
            >
                Adjust cart quantities
            </Link>
        </div>
    );
}