"use client";
import {useMenuExperience} from "./MenuExperience";
import {T,useTranslation} from "@/lib/language";
import {dishPhotoPosition} from "@/types/menuDetails";
import DietaryLabel, {GroupDietaryLabel} from "./DietaryLabel";

import {mobileMenuRows,type PortionGroup} from "@/lib/mobileMenu";
import Image from "next/image";
import {useEffect, useRef, useState} from "react";
import {apiClient} from "@/services/apiClient";
import type {Branch} from "@/types/branch";
import type {MenuProduct} from "@/types/menu";
import styles from "./NewBranchItems.module.css";

interface Highlights {
    latestProductIds: number[];trendingProductIds?:number[];
}

export default function NewBranchItems({branch, products, onSelect,portionGroups,eligibleIds}: {
    eligibleIds?:number[];
    portionGroups?: PortionGroup[];
    branch: Branch;
    products: MenuProduct[];
    onSelect: (product: MenuProduct) => void;
}) {
    const [highlights, setHighlights] = useState<{branchId: number; ids: number[]} | null>(null);
    const {details,enabled}=useMenuExperience(),t=useTranslation();
    const [collection,setCollection]=useState<"latest"|"trending"|"chef">("latest");
    const [ranked,setRanked]=useState<{branchId:number;ids:number[]}|null>(null);
    const [failedPhotos, setFailedPhotos] = useState<string[]>([]);
    const row = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const controller = new AbortController();
        apiClient<Highlights>(`/api/branches/${branch.id}/storefront-highlights`, {
            signal: controller.signal
        }).then(data => {
            if (!controller.signal.aborted) {setHighlights({branchId: branch.id, ids: data.latestProductIds ?? []});setRanked({branchId:branch.id,ids:data.trendingProductIds??[]});}
        }).catch(error => {
            if (!controller.signal.aborted) console.warn("New branch items unavailable.", error);
        });
        return () => controller.abort();
    }, [branch.id]);

    const available=products.filter(p=>p.available&&(eligibleIds===undefined||eligibleIds.includes(p.id)));
    const currentIds = highlights?.branchId === branch.id ? highlights.ids : [];
    const latestItems = currentIds.flatMap(id => available.filter(product => product.id === id));
    // Keep the visual menu usable while highlights are loading or temporarily unavailable.
    const choices={latest:latestItems.length?latestItems:available.slice(0,8),trending:ranked?.branchId===branch.id?ranked.ids.flatMap(id=>available.filter(p=>p.id===id)).slice(0,8):[],chef:available.filter(p=>details[p.id]?.chefPick).slice(0,8)};
    const currentCollection=enabled&&choices[collection].length?collection:"latest";
    const shownItems=choices[currentCollection];
    if (!shownItems.length) return null;

    function move(direction: number) {
        row.current?.scrollBy({left: direction * Math.max(220, row.current.clientWidth * .72), behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth"});
    }

    return <section className={styles.banner} aria-labelledby="new-branch-items-title">
        {enabled&&<div className="menu-collection-tabs" role="group" aria-label={t("Menu collections")}>{([["latest",latestItems.length?"New arrivals":"Explore menu"],["trending","Branch favourites"],["chef","Chef’s picks"]] as const).filter(([key])=>choices[key].length>0).map(([key,label])=><button key={key} type="button" aria-pressed={currentCollection===key} onClick={()=>setCollection(key)}><T text={label}/></button>)}</div>}
        <div className={styles.heading}>
            <div>
                <p className={styles.eyebrow}><T text="Fresh from"/> {branch.name}</p>
                <h2 id="new-branch-items-title">{currentCollection==="trending"?<T text="Branch favourites"/>:currentCollection==="chef"?<T text="Chef’s picks"/>:latestItems.length?<T text="Latest on the menu"/>:<T text="Explore the menu"/>}</h2>
            </div>
            <div className={styles.controls} aria-label={t("Browse new items")}>
                <button type="button" aria-label={t("Previous new items")} onClick={() => move(-1)}>‹</button>
                <button type="button" aria-label={t("Next new items")} onClick={() => move(1)}>›</button>
            </div>
        </div>
        <div className={styles.row} ref={row}>
            {mobileMenuRows(shownItems,portionGroups??[],products).map(row => {const product=row.group?{...row.product,name:row.group.title}:row.product;return <button type="button" key={product.id} className={styles.item}
                onClick={() => onSelect(product)} aria-label={`View ${product.name} on the menu`}>
                <span className={styles.photo}>
                    {product.imageUrl && !failedPhotos.includes(product.imageUrl)
                        ? <Image style={{objectPosition:dishPhotoPosition(details[product.id])}} src={product.imageUrl} alt="" fill sizes="(max-width: 600px) 140px, 180px"
                            className={styles.image} onError={() => setFailedPhotos(current => [...current, product.imageUrl!])} />
                        : <span className={styles.fallback} aria-hidden="true">G</span>}
                </span>
                {row.group?<GroupDietaryLabel products={row.products??[]}/>:<DietaryLabel vegetarian={product.vegetarian}/>}<span className={styles.name}>{product.name}</span>
            </button>;})}
        </div>
    </section>;
}
