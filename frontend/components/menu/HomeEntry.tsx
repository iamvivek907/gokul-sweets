"use client";

import Link from "next/link";
import {useEffect, useState, type ReactNode} from "react";
import AppShell from "@/components/layout/AppShell";
import BranchSelector from "@/components/branch/BranchSelector";
import CampaignMedia from "@/components/menu/CampaignMedia";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";
import {visibleCampaigns} from "@/lib/campaigns";
import {apiClient} from "@/services/apiClient";
import {getActiveBranches} from "@/services/branchApi";
import type {Branch} from "@/types/branch";
import type {HomepageCampaign} from "@/types/campaign";
import styles from "./HomeEntry.module.css";

export default function HomeEntry({children}: {children: ReactNode}) {
    const {features, error} = useStorefrontConfiguration();
    const {branch} = useSelectedBranch();
    const [exploring, setExploring] = useState(false);
    const [campaigns, setCampaigns] = useState<HomepageCampaign[]>([]);
    const [branches, setBranches] = useState<Branch[]>([]);
    const [failedMedia, setFailedMedia] = useState<number[]>([]);

    useEffect(() => {
        if (!features?.preHomeIntentGateway || exploring) return;
        const controller = new AbortController();
        getActiveBranches(controller.signal).then(setBranches).catch(() => {});
        if (features.homepageCampaigns) {
            apiClient<HomepageCampaign[]>(`/api/storefront/campaigns${features.controlledCampaignPublishing && branch?.id ? `?branchId=${branch.id}` : ""}`, {
                signal: AbortSignal.any([controller.signal, AbortSignal.timeout(5000)])
            }).then(setCampaigns).catch(() => {});
        }
        return () => controller.abort();
    }, [features?.preHomeIntentGateway, features?.homepageCampaigns, features?.controlledCampaignPublishing, branch?.id, exploring]);

    if (!features && !error) return <AppShell showSocialPopup={false}><p role="status" className="p-8">Preparing your Gokul visit…</p></AppShell>;
    if (!features?.preHomeIntentGateway || error || exploring) return <>{children}</>;

    const hero = visibleCampaigns(campaigns, Date.now(), features.controlledCampaignPublishing ? branch?.id ?? -1 : undefined)
        .find(item => item.type === "HERO" && !failedMedia.includes(item.id));

    return <AppShell showSocialPopup={false}>
        <div className={styles.entry}>
            <section className={styles.hero} aria-labelledby="welcome-title">
                {hero && <div className={styles.media}><CampaignMedia campaign={hero} hero accessible={features.accessibleOrderingV2}
                    onUnavailable={() => setFailedMedia(current => [...current, hero.id])} /></div>}
                <div className={styles.shade} />
                <div className={styles.content}>
                    <p className={styles.kicker}>Gokul Sweets & Restaurants · Fresh for pickup</p>
                    <h1 id="welcome-title">A little joy in<br /><em>every visit.</em></h1>
                    <p className={styles.description}>{hero?.subtitle ?? "Sweets for celebrations, favourite snacks for today, and a pickup time that works for you."}</p>
                    <div className={styles.actions}>
                        <Link href="/menu" className={styles.order}>Order food <span aria-hidden="true">↗</span></Link>
                        <Link href="/about#our-branches" className={styles.occasion}>Plan an occasion</Link>
                    </div>
                    <button className={styles.explore} type="button" onClick={() => setExploring(true)}>Explore Gokul <span aria-hidden="true">↓</span></button>
                </div>
                <a className={styles.scroll} href="#our-branches">Scroll to explore <span aria-hidden="true">↓</span></a>
            </section>
            <section id="our-branches" className={styles.branches} aria-labelledby="branches-title">
                <div className={styles.branchHeading}><div><p className={styles.kicker}>Find your Gokul</p><h2 id="branches-title">Your next visit starts here.</h2></div><BranchSelector /></div>
                <div className={styles.branchGrid}>{branches.map(item => <article className={styles.branchCard} key={item.id}>
                    <span className={styles.branchNumber}>{item.city || "Gokul"}</span><h3>{item.name}</h3>
                    {item.address && <p>{item.address}</p>}
                    {branch?.id === item.id ? <Link href="/menu">Browse the menu <span aria-hidden="true">↗</span></Link> : <span className={styles.selectHint}>Select this branch above to order</span>}
                </article>)}</div>
                {!branches.length && <p className={styles.branchEmpty}>Choose a branch above to see the menu and pickup options.</p>}
            </section>
        </div>
    </AppShell>;
}
