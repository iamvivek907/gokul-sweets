"use client";

import {useCallback, useEffect, useState} from "react";

import {getReviewContext, saveReview} from "@/services/reviewApi";
import type {ReviewContext} from "@/types/review";

interface Props {
    orderNumber: string;
}

function StarPicker({value, onChange, label}: {
    value: number;
    onChange: (rating: number) => void;
    label: string;
}) {
    return (
        <div className="flex gap-1" role="group" aria-label={label}>
            {[1, 2, 3, 4, 5].map(star => (
                <button
                    key={star}
                    type="button"
                    onClick={() => onChange(star)}
                    aria-label={`${star} star${star === 1 ? "" : "s"}`}
                    aria-pressed={star <= value}
                    className={`min-h-11 min-w-10 text-3xl leading-none ${
                        star <= value ? "text-[#d69216]" : "text-[#d8cbc3]"
                    }`}
                >
                    ★
                </button>
            ))}
        </div>
    );
}

export default function OrderReviewCard({orderNumber}: Props) {
    const [context, setContext] = useState<ReviewContext | null>(null);
    const [overallRating, setOverallRating] = useState(0);
    const [comment, setComment] = useState("");
    const [itemRatings, setItemRatings] = useState<Record<number, number>>({});
    const [error, setError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);

    const fetchContext = useCallback(
        (signal?: AbortSignal) => getReviewContext(orderNumber, signal),
        [orderNumber]
    );

    useEffect(() => {
        const controller = new AbortController();

        void fetchContext(controller.signal)
            .then(response => {
                if (controller.signal.aborted) return;

                setContext(response);
                setOverallRating(response.review?.overallRating ?? 0);
                setComment(response.review?.comment ?? "");
                setItemRatings(Object.fromEntries(
                    (response.review?.itemRatings ?? [])
                        .map(item => [item.productId, item.rating])
                ));
            })
            .catch(exception => {
                if (controller.signal.aborted) return;
                setError(
                    exception instanceof Error
                        ? exception.message
                        : "Unable to load the review form."
                );
            });

        return () => controller.abort();
    }, [fetchContext]);

    async function handleSubmit(): Promise<void> {
        if (saving || overallRating === 0) return;

        setSaving(true);
        setSaved(false);
        setError(null);

        try {
            const review = await saveReview(orderNumber, {
                overallRating,
                comment: comment.trim() || null,
                itemRatings: Object.entries(itemRatings).map(([productId, rating]) => ({
                    productId: Number(productId),
                    rating
                }))
            });

            setContext(current => current ? {...current, review} : current);
            setSaved(true);
        } catch (exception) {
            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to save your review."
            );
        } finally {
            setSaving(false);
        }
    }

    if (!context && !error) {
        return (
            <div className="mt-7 rounded-2xl border border-[#eadfd6] p-5">
                <p className="text-sm text-[#756763]">Loading review options...</p>
            </div>
        );
    }

    if (!context) {
        return (
            <div className="mt-7 rounded-2xl border border-red-200 bg-red-50 p-5 text-sm text-red-700">
                {error}
            </div>
        );
    }

    if (!context.eligible) return null;

    return (
        <div className="mt-7 rounded-2xl border border-[#eadfd6] bg-[#fffaf3] p-5 sm:p-6">
            <h2 className="text-lg font-bold text-[#241715]">
                {context.review ? "Your review" : "How was your order?"}
            </h2>
            <p className="mt-1 text-sm leading-6 text-[#756763]">
                Your feedback helps us improve freshness, taste and pickup service.
            </p>

            <div className="mt-5">
                <p className="text-sm font-bold text-[#241715]">Overall experience *</p>
                <StarPicker value={overallRating} onChange={setOverallRating} label="Overall order rating" />
            </div>

            {context.products.length > 0 && (
                <div className="mt-5 border-t border-[#eadfd6] pt-5">
                    <p className="text-sm font-bold text-[#241715]">Rate individual items (optional)</p>
                    <div className="mt-3 space-y-4">
                        {context.products.map(product => (
                            <div key={product.productId} className="rounded-xl bg-white p-3">
                                <p className="text-sm font-semibold text-[#241715]">{product.productName}</p>
                                <StarPicker
                                    value={itemRatings[product.productId] ?? 0}
                                    onChange={rating => setItemRatings(current => ({
                                        ...current,
                                        [product.productId]: rating
                                    }))}
                                    label={`${product.productName} rating`}
                                />
                            </div>
                        ))}
                    </div>
                </div>
            )}

            <label className="mt-5 block text-sm font-bold text-[#241715]" htmlFor="review-comment">
                Tell us more (optional)
            </label>
            <textarea
                id="review-comment"
                value={comment}
                maxLength={1000}
                rows={4}
                onChange={event => setComment(event.target.value)}
                placeholder="What did you enjoy? What can we improve?"
                className="mt-2 w-full rounded-xl border border-[#d8cbc3] bg-white p-3 text-[#241715] outline-none focus:border-[#7a1625]"
            />
            <p className="mt-1 text-right text-xs text-[#756763]">{comment.length}/1000</p>

            {error && <p className="mt-3 text-sm font-semibold text-red-700">{error}</p>}
            {saved && <p className="mt-3 text-sm font-semibold text-green-700">Thank you. Your review has been saved.</p>}
            {context.review?.status === "HIDDEN" && (
                <p className="mt-3 text-sm text-amber-800">This review is currently being checked by our team.</p>
            )}

            <button
                type="button"
                disabled={saving || overallRating === 0}
                onClick={() => void handleSubmit()}
                className="mt-5 min-h-12 w-full rounded-xl bg-[#7a1625] px-4 font-bold text-white! disabled:cursor-not-allowed disabled:opacity-50"
            >
                {saving ? "Saving..." : context.review ? "Update Review" : "Submit Review"}
            </button>
        </div>
    );
}
