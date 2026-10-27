"use client";
import { useCallback, useEffect, useState, useId } from "react";
import { getReviewContext, saveReview } from "@/services/reviewApi";
import type { ReviewContext } from "@/types/review";
import { T, useTranslation } from "@/lib/language";
import styles from "./OrderReviewCard.module.css";
const suggestionsByRating: Record<number, string[]> = {
    1: ["Taste needs improvement", "Food was not fresh", "Portion was too small", "Price felt too high", "Pickup took too long", "Order was incorrect"],
    2: ["Taste could be better", "Food could be fresher", "Portion was small", "Price felt high", "Pickup could be faster", "Order had an issue"],
    3: ["Taste was okay", "Could be fresher", "Portion could be better", "Value could be better", "Pickup was okay", "Service could improve"],
    4: ["Tasty food", "Good freshness", "Good portions", "Good value", "Easy pickup", "Friendly service"],
    5: ["Loved the taste", "Fresh and delicious", "Generous portions", "Great value", "Quick pickup", "Wonderful service"],
};
function StarPicker({ value, onChange, label, disabled }: {
    value: number;
    onChange: (rating: number) => void;
    label: string;
    disabled?: boolean;
}) {
    const translate=useTranslation();
    return <div className={styles.stars} role="group" aria-label={label}>{[1, 2, 3, 4, 5].map(star => <button key={star} type="button" disabled={disabled} className={styles.star} data-filled={star <= value} aria-label={`${star} ${translate(star===1?"star":"stars")}`} aria-pressed={star === value} onClick={() => onChange(star)} onKeyDown={event => { if (["ArrowLeft", "ArrowRight"].includes(event.key)) {
        event.preventDefault();
        const next = Math.max(1, Math.min(5, star + (event.key === "ArrowRight" ? 1 : -1)));
        onChange(next);
        (event.currentTarget.parentElement?.children[next - 1] as HTMLButtonElement | undefined)?.focus();
    } }}><svg aria-hidden="true" viewBox="0 0 24 24"><path d="m12 2 3 6.4 7 1-5 5 1.2 7L12 18l-6.2 3.4L7 14.4l-5-5 7-1Z"/></svg></button>)}</div>;
}
export default function OrderReviewCard({ orderNumber, prompt = false }: {
    orderNumber: string;
    prompt?: boolean;
}) {
    const [context, setContext] = useState<ReviewContext | null>(null), [overallRating, setOverallRating] = useState(0), [comment, setComment] = useState(""), [itemRatings, setItemRatings] = useState<Record<number, number>>({}), [error, setError] = useState<string | null>(null), [saving, setSaving] = useState(false), [saved, setSaved] = useState(false), [editing, setEditing] = useState(true), [retry, setRetry] = useState(0);
    const [suggestions, setSuggestions] = useState<string[]>([]);
    const [showCustomerName, setShowCustomerName] = useState(false);
    const commentId = useId();
    const translate = useTranslation();
    const combinedComment = [...suggestions.map(translate), comment.trim()].filter(Boolean).join(". ");
    function changeRating(rating: number) {
        if (rating !== overallRating) setSuggestions([]);
        setOverallRating(rating);
    }
    const fetchContext = useCallback((signal?: AbortSignal) => getReviewContext(orderNumber, signal), [orderNumber]);
    useEffect(() => { const controller = new AbortController(); void fetchContext(AbortSignal.any([controller.signal, AbortSignal.timeout(10000)])).then(response => { if (controller.signal.aborted)
        return; setContext(response); setShowCustomerName(response.review?.showCustomerName ?? false); setOverallRating(response.review?.overallRating ?? 0); setComment(response.review?.comment ?? ""); setItemRatings(Object.fromEntries((response.review?.itemRatings ?? []).map(item => [item.productId, item.rating]))); setEditing(!response.review); setError(null); }).catch(() => { if (!controller.signal.aborted)
        setError("Unable to load the review form. Please try again."); }); return () => controller.abort(); }, [fetchContext, retry]);
    async function handleSubmit() { if (saving || overallRating === 0 || combinedComment.length > 1000)
        return; setSaving(true); setSaved(false); setError(null); try {
        const review = await saveReview(orderNumber, { overallRating, comment: combinedComment || null, showCustomerName, itemRatings: Object.entries(itemRatings).map(([productId, rating]) => ({ productId: Number(productId), rating })) });
        setContext(current => current ? { ...current, review } : current);
        setComment(review.comment ?? "");
        setSuggestions([]);
        setSaved(true);
        setEditing(false);
        window.dispatchEvent(new CustomEvent("gokul-order-review-updated", { detail: { orderNumber, rating: review.overallRating } }));
    }
    catch {
        setError("Your feedback could not be confirmed. Keep your choices and try again.");
    }
    finally {
        setSaving(false);
    } }
    if (!context)
        return <section className={styles.review}>{error ? <div><p role="alert">{translate(error)}</p><button type="button" className={styles.secondary} onClick={() => setRetry(value => value + 1)}><T text="Try again"/></button></div> : <p role="status"><T text="Loading review options…"/></p>}</section>;
    if (!context.eligible)
        return null;
    return <section className={`customer-order-review ${styles.review}`}><span className={styles.eyebrow}><T text="Your Gokul experience"/></span><h2><T text={context.review && !editing ? "Your review" : "How was your order?"}/></h2><p><T text="Your feedback helps us improve freshness, taste and pickup service."/></p>
 {saved && <div className={styles.saved} role="status"><strong><T text="Thank you for your valuable feedback!"/></strong><p><T text="Your feedback helps make the next visit better."/></p></div>}
 {!editing && context.review ? <><article className={styles.savedReview}><h3><T text="Your overall experience"/></h3><div className={styles.submitted} aria-label={`${context.review.overallRating} ${translate("out of 5 stars")}`}>{"★".repeat(context.review.overallRating)}{"☆".repeat(5 - context.review.overallRating)}</div>{context.review.comment && <p>{context.review.comment}</p>}<p><T text="From your completed order"/></p></article><button type="button" className={styles.secondary} onClick={() => setEditing(true)}><T text="Edit my feedback"/></button></> : <>
 <div className={styles.overall}><h3><T text="Overall experience *"/></h3><StarPicker value={overallRating} onChange={changeRating} label={translate("Overall order rating")} disabled={saving}/><span className={styles.feeling} aria-live="polite">{translate(["Choose your stars", "Could be better", "Fair", "Good", "Really enjoyed it", "Loved it!"][overallRating])}</span></div>
 {context.products.length > 0 && <details><summary><T text="Rate your ordered dishes (optional)"/></summary>{context.products.map(product => <div key={product.productId} className={styles.product}><h3>{product.productName}</h3><StarPicker value={itemRatings[product.productId] ?? 0} onChange={rating => setItemRatings(current => ({ ...current, [product.productId]: rating }))} label={`${product.productName} · ${translate("Dish rating")}`} disabled={saving}/>{itemRatings[product.productId] && <button type="button" className={styles.secondary} disabled={saving} onClick={() => setItemRatings(current => { const next = { ...current }; delete next[product.productId]; return next; })}><T text="Clear dish rating"/></button>}</div>)}</details>}
 <details open={prompt || undefined}><summary><T text="Add a detailed review"/></summary><p><T text="Choose one or more suggestions to include with your message. Changing your stars clears the selected suggestions. Published comments may be shown to other customers; please avoid personal details."/></p>
 {overallRating > 0 ? <div className={styles.suggestions} role="group" aria-label={translate("Feedback suggestions")}>{suggestionsByRating[overallRating].map(text => <button type="button" key={text} disabled={saving} aria-pressed={suggestions.includes(text)} onClick={() => setSuggestions(current => current.includes(text) ? current.filter(item => item !== text) : [...current, text])}><T text={text}/></button>)}</div> : <p><T text="Choose your stars to see suggestions."/></p>}
 <label htmlFor={commentId}><T text="Tell us more (optional)"/></label><textarea id={commentId} maxLength={1000} disabled={saving} value={comment} onChange={event => setComment(event.target.value)} placeholder={translate("What did you enjoy? What could we do better?")}/><p aria-live="polite">{combinedComment.length}/1000 <T text="characters including suggestions"/>{combinedComment.length > 1000 ? translate(" · Please shorten your feedback.") : ""}</p></details>
 <label className={styles.nameChoice}><input type="checkbox" checked={showCustomerName} disabled={saving} onChange={event => setShowCustomerName(event.target.checked)} /> <T text="Show my first name with my published review"/></label>
 <button type="button" className={styles.primary} disabled={saving || overallRating === 0 || combinedComment.length > 1000} onClick={() => void handleSubmit()}>{translate(saving ? "Saving…" : context.review ? "Update Review" : "Submit Review")}</button></>}
 {error && <p className={styles.error} role="alert">{translate(error)}</p>}{context.review?.status === "HIDDEN" && <p><T text="This review is currently being checked by our team."/></p>}
 </section>;
}
