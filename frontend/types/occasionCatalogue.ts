export type OccasionSweet = {id: number; categoryId?: number; categoryName?: string; name: string; description: string | null; imageUrl: string | null; saleMode: "UNIT" | "WEIGHT"; occasionOnly: boolean; published: boolean; leadDays: number; pieceGrams: number | null};
export type OccasionBox = {id: number | null; imageUrls?: string[]; name: string; imageUrl: string | null; dimensions: string; material: string; compartments: number; capacityPieces: number; price: number | null; branding: string; leadDays: number; published: boolean};
export type OccasionBranding = {headline: string; description: string; imageUrl: string | null; published: boolean};
export type OccasionCatalogue = {sweets: OccasionSweet[]; boxes: OccasionBox[]; branding?: OccasionBranding | null};
export type GiftSnapshot = {includeSpoons?: boolean;box: OccasionBox; boxCount: number; recipe: {productId: number; pieces: number}[]; packagingEstimate: number | null; approvedPackagingTotal?: number | null};
