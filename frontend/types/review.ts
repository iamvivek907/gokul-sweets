export type ReviewStatus = "PUBLISHED" | "HIDDEN";

export interface ReviewItemRating {
    productId: number;
    productName: string;
    rating: number;
}

export interface CustomerReview {
    id: number;
    orderNumber: string;
    overallRating: number;
    comment: string | null;
    status: ReviewStatus;
    itemRatings: ReviewItemRating[];
    createdAt: string;
    updatedAt: string;
}

export interface ReviewableProduct {
    productId: number;
    productName: string;
}

export interface ReviewContext {
    eligible: boolean;
    eligibilityMessage: string | null;
    products: ReviewableProduct[];
    review: CustomerReview | null;
}

export interface UpsertReviewRequest {
    overallRating: number;
    comment: string | null;
    itemRatings: Array<{
        productId: number;
        rating: number;
    }>;
}

export interface ProductRatingSummary {
    productId: number;
    averageRating: number;
    ratingCount: number;
}
