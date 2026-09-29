"use client";

import Image from "next/image";
import Link from "next/link";
import {useEffect, useState} from "react";
import BranchSelector from "@/components/branch/BranchSelector";
import CampaignMedia from "@/components/menu/CampaignMedia";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {getActiveBranches} from "@/services/branchApi";
import {apiClient} from "@/services/apiClient";
import {visibleCampaigns} from "@/lib/campaigns";
import type {Branch} from "@/types/branch";
import type {HomepageCampaign} from "@/types/campaign";
import styles from "./EditorialArrival.module.css";

export default function EditorialArrival({campaignsEnabled, accessible, branchExperience = false, occasionEnquiries = false}: {
    campaignsEnabled: boolean; accessible: boolean; branchExperience?: boolean; occasionEnquiries?: boolean;
}) {
    const {branch} = useSelectedBranch();
    const [branches, setBranches] = useState<Branch[]>([]);
    const [campaigns, setCampaigns] = useState<HomepageCampaign[]>([]);
    const [branchPhotos, setBranchPhotos] = useState<Record<number, string>>({});
    const [failed, setFailed] = useState<number[]>([]);
    const [failedBranchPhotos, setFailedBranchPhotos] = useState<number[]>([]);
    const [now, setNow] = useState(0);

    useEffect(() => {
        const controller = new AbortController();
        getActiveBranches(controller.signal).then(list => {
            if (controller.signal.aborted) return;
            const active = list.filter(item => item.active !== false);
            setBranches(active);
            if (!campaignsEnabled || branchExperience) return;
            // A branch-specific published poster can illustrate its own card; never imply an unconfigured capability.
            void Promise.all(active.map(async item => {
                try {
                    const data = await apiClient<HomepageCampaign[]>(`/api/storefront/campaigns?branchId=${item.id}`, {
                        signal: controller.signal
                    });
                    const matched = visibleCampaigns(data, Date.now(), item.id)
                        .find(value => value.branchId === item.id && value.type === "HERO");
                    const photo = matched?.fallbackMediaUrl
                        ?? (matched?.mediaType?.startsWith("image/") ? matched.mediaUrl : null);
                    return photo ? [item.id, photo] as const : null;
                } catch { return null; }
            })).then(rows => {
                if (!controller.signal.aborted) setBranchPhotos(Object.fromEntries(rows.filter(
                    (row): row is readonly [number, string] => row !== null
                )));
            });
        }).catch(error => {
            if (!controller.signal.aborted) console.warn("Branch discovery unavailable.", error);
        });
        return () => controller.abort();
    }, [campaignsEnabled, branchExperience]);

    useEffect(() => {
        if (!campaignsEnabled) return;
        const controller = new AbortController();
        const load = async () => {
            try {
                const list = await apiClient<HomepageCampaign[]>("/api/storefront/campaigns", {
                    signal: controller.signal
                });
                if (!controller.signal.aborted) {setCampaigns(list); setNow(Date.now());}
            } catch (error) {
                if (!controller.signal.aborted) console.warn("Entrance campaign unavailable.", error);
            }
        };
        void load();
        const timer = window.setInterval(load, 30_000);
        return () => {controller.abort(); window.clearInterval(timer);};
    }, [campaignsEnabled]);

    const hero = visibleCampaigns(campaigns, now, branch?.id ?? -1)
        .find(value => value.type === "HERO" && !failed.includes(value.id));

    return <div className={`gokul-arrival ${styles.arrival}`}>
        <section className={styles.hero} aria-labelledby="gokul-arrival-title">
            {hero && <div className={styles.media}><CampaignMedia
                campaign={hero} hero immersive accessible={accessible}
                onUnavailable={() => setFailed(current => [...current, hero.id])}
            /></div>}
            <div className={styles.scrim} aria-hidden="true" />
            <div className={styles.topbar}>
                <div className={styles.logo}>Gokul <span>SWEETS &amp; RESTAURANTS</span></div>
                <nav aria-label="Welcome navigation">
                    <a href="#gokul-arrival-title" aria-current="page">Home</a>
                    <Link href="/about">Our story</Link>
                    <a href="#gokul-branches">Our branches</a>
                    <Link href="/branches" className={styles.orderFood}>Order food</Link>
                </nav>
            </div>
            <div className={styles.heroCopy}>
                <p>GOKUL SWEETS &amp; RESTAURANTS</p>
                <h1 id="gokul-arrival-title">A little joy<br />in every visit.</h1>
                <span>Fresh sweets, snacks and meals for the moments you share. Order ahead and collect at your chosen branch.</span>
                <div className={styles.actions}>
                    <Link href="/branches" className={styles.orderFood}>Order food</Link>
                    <Link href={occasionEnquiries ? "/occasions" : "/branches"}>Plan an occasion</Link>
                </div>
            </div>
            <a className={styles.scrollCue} href="#gokul-branches" onClick={event => {
                const target = document.getElementById("gokul-branches");
                if (!target) return;
                event.preventDefault();
                target.scrollIntoView({behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth"});
                window.history.replaceState(null, "", "#gokul-branches");
            }}>
                <span className={styles.scrollCueText}>Scroll to explore</span>
                <span className={styles.scrollCueIcon} aria-hidden="true"><span /></span>
            </a>
        </section>
        <section id="gokul-branches" className={styles.branches}>
            <div className={styles.sectionHead}>
                <span>OUR BRANCHES</span><h2>Your next visit starts here.</h2>
                <p>Choose a Gokul branch near you to see its live menu and pickup choices.</p>
            </div>
            <div className={styles.branchGrid}>
                {branches.map(item => <article className={styles.branchCard} key={item.id}>
                    {!failedBranchPhotos.includes(item.id) && branchExperience && item.coverImageUrl
                        ? <picture><source media="(max-width: 700px)" srcSet={item.mobileCoverImageUrl || item.coverImageUrl} />
                            <img src={item.coverImageUrl} alt={item.coverAltText || item.name}
                                className={styles.branchPhoto} onError={() => setFailedBranchPhotos(current => [...current, item.id])} /></picture>
                        : !branchExperience && branchPhotos[item.id] && <Image src={branchPhotos[item.id]} alt={item.name}
                            fill sizes="(max-width: 700px) 100vw, 50vw" className={styles.branchPhoto} />}
                    <div className={styles.branchCopy}>
                        <span>{item.city ?? "GOKUL BRANCH"}</span><h3>{item.name}</h3>
                        <p>{branchExperience ? item.description || item.city || item.address || "Explore this branch" : item.city ?? item.address ?? "Explore this branch’s live menu and pickup choices."}</p>
                        {branchExperience && <p>{item.pickupAvailable ? "Order for pickup" : "Online pickup unavailable"}</p>}
                        <span className={styles.cardPrompt}>Explore this branch</span>
                    </div>
                    <BranchSelector cardBranch={item} destination={branchExperience ? "branchHome" : "menu"} />
                </article>)}
            </div>
            {!branches.length && <p className={styles.branchFallback}>Branch details will appear here when available. You can still explore the menu.</p>}
        </section>
    </div>;
}
