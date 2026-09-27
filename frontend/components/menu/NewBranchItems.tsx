"use client";

import Image from "next/image";
import {useEffect, useRef, useState} from "react";
import {apiClient} from "@/services/apiClient";
import type {Branch} from "@/types/branch";
import type {MenuProduct} from "@/types/menu";
import styles from "./NewBranchItems.module.css";

interface Highlights {
    latestProductIds: number[];
}

export default function NewBranchItems({branch, products, onSelect}: {
    branch: Branch;
    products: MenuProduct[];
    onSelect: (product: MenuProduct) => void;
}) {
    const [highlights, setHighlights] = useState<{branchId: number; ids: number[]} | null>(null);
    const [failedPhotos, setFailedPhotos] = useState<string[]>([]);
    const row = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const controller = new AbortController();
        apiClient<Highlights>(`/api/branches/${branch.id}/storefront-highlights`, {
            signal: controller.signal
        }).then(data => {
            if (!controller.signal.aborted) setHighlights({branchId: branch.id, ids: data.latestProductIds ?? []});
        }).catch(error => {
            if (!controller.signal.aborted) console.warn("New branch items unavailable.", error);
        });
        return () => controller.abort();
    }, [branch.id]);

    const currentIds = highlights?.branchId === branch.id ? highlights.ids : [];
    const newItems = currentIds.flatMap(id => products.filter(product => product.id === id && product.available));
    if (!newItems.length) return null;

    function move(direction: number) {
        row.current?.scrollBy({left: direction * Math.max(220, row.current.clientWidth * .72), behavior: "smooth"});
    }

    return <section className={styles.banner} aria-labelledby="new-branch-items-title">
        <div className={styles.heading}>
            <div>
                <p className={styles.eyebrow}>Fresh from {branch.name}</p>
        <h2 id="new-branch-items-title">Latest on the menu</h2>
            </div>
            <div className={styles.controls} aria-label="Browse new items">
                <button type="button" aria-label="Previous new items" onClick={() => move(-1)}>‹</button>
                <button type="button" aria-label="Next new items" onClick={() => move(1)}>›</button>
            </div>
        </div>
        <div className={styles.row} ref={row}>
            {newItems.map(product => <button type="button" key={product.id} className={styles.item}
                onClick={() => onSelect(product)} aria-label={`View ${product.name} on the menu`}>
                <span className={styles.photo}>
                    {product.imageUrl && !failedPhotos.includes(product.imageUrl)
                        ? <Image src={product.imageUrl} alt="" fill sizes="(max-width: 600px) 140px, 180px"
                            className={styles.image} onError={() => setFailedPhotos(current => [...current, product.imageUrl!])} />
                        : <span className={styles.fallback} aria-hidden="true">G</span>}
                </span>
                <span className={styles.name}>{product.name}</span>
            </button>)}
        </div>
    </section>;
}
