export type OccasionSweet = {id: number; categoryId?: number; categoryName?: string; name: string; description: string | null; imageUrl: string | null; saleMode: "UNIT" | "WEIGHT"; occasionOnly: boolean; published: boolean; leadDays: number; pieceGrams: number | null;unitPrice?:number|null;taxPercent?:number|null};
export type OccasionBox = {id: number | null; imageUrls?: string[]; name: string; imageUrl: string | null; dimensions: string; material: string; compartments: number; capacityPieces: number;capacityGrams?:number|null; price: number | null; branding: string; leadDays: number; published: boolean};
export type OccasionBranding = {headline: string; description: string; imageUrl: string | null; published: boolean};
export type OccasionCatalogue = {sweets: OccasionSweet[]; boxes: OccasionBox[]; branding?: OccasionBranding | null};
export type GiftSnapshot = {includeSpoons?: boolean;box: OccasionBox; boxCount: number; recipe: {productId: number; pieces: number}[]; packagingEstimate: number | null; approvedPackagingTotal?: number | null};
export type PackingGroup = {kind:"MIXED"|"WEIGHT";boxId:number|null;boxCount:number;recipe:{productId:number;pieces:number}[];productId:number|null;totalGrams:number|null;packGrams:number|null;includeSpoons:boolean};
export type PackedGroup = Omit<PackingGroup,"boxId"> & {groupNumber:number;box:OccasionBox;productName:string|null;packagingEstimate:number|null};
export type PackingDraft = PackingGroup & {key:number};
