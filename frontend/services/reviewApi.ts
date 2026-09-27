import {apiClient} from "@/services/apiClient";

import type {
    CustomerReview,
    ProductRatingSummary,
    ReviewContext,
    UpsertReviewRequest
} from "@/types/review";

export function getReviewContext(
    orderNumber: string,
    signal?: AbortSignal
): Promise<ReviewContext> {
    return apiClient<ReviewContext>(
        `/api/orders/${encodeURIComponent(orderNumber)}/review`,
        {signal, credentials: "include"}
    );
}

export function saveReview(
    orderNumber: string,
    request: UpsertReviewRequest
): Promise<CustomerReview> {
    return apiClient<CustomerReview>(
        `/api/orders/${encodeURIComponent(orderNumber)}/review`,
        {
            method: "PUT",
            credentials: "include",
            body: JSON.stringify(request)
        }
    );
}

export function getProductRatingSummaries(
    productIds: number[],
    signal?: AbortSignal
): Promise<ProductRatingSummary[]> {
    return apiClient<ProductRatingSummary[]>(
        "/api/reviews/product-summaries",
        {
            method: "POST",
            body: JSON.stringify({productIds}),
            signal
        }
    );
}
