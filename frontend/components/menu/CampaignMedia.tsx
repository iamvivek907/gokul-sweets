"use client";

import Image from "next/image";
import {useEffect, useRef, useState, useSyncExternalStore} from "react";
import type {HomepageCampaign} from "@/types/campaign";
import {useStaticCampaignMedia} from "@/lib/mediaRecovery";

function subscribe(callback: () => void) {
    const query = window.matchMedia("(prefers-reduced-motion: reduce)");
    query.addEventListener("change", callback);
    return () => query.removeEventListener("change", callback);
}
function subscribeMobile(callback: () => void) {
    const query = window.matchMedia("(max-width: 767px)");
    query.addEventListener("change", callback);
    return () => query.removeEventListener("change", callback);
}

type DataConnection = {saveData?: boolean; addEventListener?: (name: string, fn: () => void) => void;
    removeEventListener?: (name: string, fn: () => void) => void};
const connection = () => (navigator as Navigator & {connection?: DataConnection}).connection;
function subscribeSaveData(callback: () => void) {
    const active = connection();
    active?.addEventListener?.("change", callback);
    return () => active?.removeEventListener?.("change", callback);
}

export default function CampaignMedia({campaign, hero = false, immersive = false, accessible = false, onUnavailable}: {
    campaign: HomepageCampaign; hero?: boolean; immersive?: boolean; accessible?: boolean; onUnavailable: () => void;
}) {
    const reduced = useSyncExternalStore(subscribe,
        () => window.matchMedia("(prefers-reduced-motion: reduce)").matches, () => true);
    const mobile = useSyncExternalStore(subscribeMobile,
        () => window.matchMedia("(max-width: 767px)").matches, () => false);
    const [failed, setFailed] = useState(false);
    const saveData = useSyncExternalStore(subscribeSaveData, () => connection()?.saveData === true, () => true);
    const [visible, setVisible] = useState(hero);
    const frame = useRef<HTMLDivElement>(null);
    useEffect(() => {
        if (hero || !frame.current || !("IntersectionObserver" in window)) return;
        const observer = new IntersectionObserver(entries => {
            if (entries.some(entry => entry.isIntersecting)) {setVisible(true); observer.disconnect();}
        }, {rootMargin: "200px"});
        observer.observe(frame.current);
        return () => observer.disconnect();
    }, [hero]);
    const mobileAsset = mobile && Boolean(campaign.mobileMediaUrl);
    const selectedType = mobileAsset ? campaign.mobileMediaType ?? "image/png" : campaign.mediaType;
    const selectedUrl = mobileAsset ? campaign.mobileMediaUrl : campaign.mediaUrl;
    const animated = selectedType === "image/gif" || selectedType?.startsWith("video/") === true;
    const useFallback = useStaticCampaignMedia(animated, reduced, accessible && saveData, failed, visible);
    const source = useFallback ? campaign.fallbackMediaUrl : selectedUrl;
    if (!source) return null;
    const fail = () => {
        if (animated && !useFallback && campaign.fallbackMediaUrl) setFailed(true);
        else onUnavailable();
    };
    return <div ref={frame} className={immersive ? "entry-campaign-media" : "relative aspect-[4/3] w-full overflow-hidden rounded-2xl bg-[#fff0dc]"}>
        {!useFallback && selectedType?.startsWith("video/") ? <video key={source}
            src={source} poster={campaign.fallbackMediaUrl ?? undefined} muted loop playsInline autoPlay
            disablePictureInPicture disableRemotePlayback preload={hero ? "metadata" : "none"}
            aria-label={campaign.altText || campaign.title}
            className="h-full w-full object-cover" onError={fail} />
            : <Image src={source} alt={campaign.altText || campaign.title} fill sizes="(max-width: 768px) 100vw, 50vw"
                loading={hero ? "eager" : "lazy"} unoptimized={selectedType === "image/gif" && !useFallback}
                className="object-cover" onError={fail} />}
    </div>;
}
