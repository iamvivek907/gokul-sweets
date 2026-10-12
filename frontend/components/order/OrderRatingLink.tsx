"use client";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { getReviewContext } from "@/services/reviewApi";
/** Use the batched history rating; retain visible-card lookup for legacy callers. */
export default function OrderRatingLink({ orderNumber, rating: knownRating, returnTo }: {
    orderNumber: string;
    rating?: number | null;
    returnTo?: string;
}) {
    const element = useRef<HTMLDivElement>(null);
    const [review, setReview] = useState<{
        order: string;
        rating: number;
    } | null>(null);
    useEffect(() => {
        if (knownRating !== undefined)
            return;
        const controller = new AbortController();
        let started = false;
        const observer = new IntersectionObserver(entries => { if (started || !entries.some(e => e.isIntersecting))
            return; started = true; observer.disconnect(); getReviewContext(orderNumber, controller.signal).then(context => { if (!controller.signal.aborted && context.review)
            setReview({ order: orderNumber, rating: context.review.overallRating }); }).catch(() => { }); });
        if (element.current)
            observer.observe(element.current);
        return () => { observer.disconnect(); controller.abort(); };
    }, [orderNumber, knownRating]);
    const rating = knownRating !== undefined ? (knownRating ?? 0) : (review?.order === orderNumber ? review.rating : 0);
    return <div ref={element}><Link className="mobile-order-rate" href={`/orders/${encodeURIComponent(orderNumber)}${returnTo ? `?from=${encodeURIComponent(returnTo)}` : ""}#order-review`}><span>{rating ? "Your rating" : "Rate your meal"}</span><span aria-label={rating ? `${rating} out of 5 stars` : "Not rated"} style={rating ? { color: "#94621c" } : undefined}>{Array.from({ length: 5 }, (_, n) => n < rating ? "★" : "☆").join(" ")}</span></Link></div>;
}
