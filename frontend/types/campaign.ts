export interface HomepageCampaign {
    id: number;
    type: "HERO" | "FEATURE";
    title: string;
    subtitle: string | null;
    mediaUrl: string | null;
    mediaType: string | null;
    fallbackMediaUrl: string | null;
    ctaLabel: string | null;
    ctaTarget: string | null;
    startAt: string | null;
    endAt: string | null;
    active: boolean;
    displayOrder: number;
    createdAt: string;
    updatedAt: string;
}
