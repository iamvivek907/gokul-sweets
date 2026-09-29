import type {CSSProperties} from "react";

export type CampaignFrame = {x: number; y: number; zoom: number; fit: "COVER" | "CONTAIN"};

export function campaignFrameStyle(frame: CampaignFrame): CSSProperties {
    const x = Math.min(100, Math.max(0, frame.x));
    const y = Math.min(100, Math.max(0, frame.y));
    const zoom = Math.min(300, Math.max(100, frame.zoom));
    return {
        objectFit: frame.fit === "CONTAIN" ? "contain" : "cover",
        objectPosition: `${x}% ${y}%`,
        transform: `scale(${zoom / 100})`,
        transformOrigin: `${x}% ${y}%`
    };
}
