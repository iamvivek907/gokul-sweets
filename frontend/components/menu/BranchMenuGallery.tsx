"use client";

import Image from "next/image";
import Link from "next/link";
import {useState} from "react";
import BranchSelector from "@/components/branch/BranchSelector";
import type {Branch} from "@/types/branch";
import type {MenuProduct} from "@/types/menu";

export default function BranchMenuGallery({branch, products}: {branch: Branch; products: MenuProduct[]}) {
    const [failed, setFailed] = useState<string[]>([]);
    const photos = products.filter(product => product.available && product.imageUrl && !failed.includes(product.imageUrl))
        .filter((product, index, list) => list.findIndex(candidate => candidate.imageUrl === product.imageUrl) === index).slice(0, 3);

    return <div className="gokul-branch-gallery">
        <div className="gokul-branch-intro">
            <p className="gokul-overline">Gokul Sweets &amp; Restaurants / Pickup menu</p>
            <h1>{branch.name}</h1>
            <p>Explore the live menu at this branch. Prices and pickup choices are confirmed before payment.</p>
            <div className="gokul-branch-meta"><span>{[branch.address, branch.city].filter(Boolean).join(", ") || "Your selected pickup branch"}</span><BranchSelector compact /></div>
        </div>
        {photos.length ? <div className="gokul-gallery-photos" aria-label="Food from this branch's menu">
            {photos.map((product, index) => <div key={product.id} className={`gokul-gallery-tile gokul-gallery-tile-${index}`}>
                <Image src={product.imageUrl!} alt={product.name} fill sizes={index === 0 ? "(max-width: 700px) 100vw, 65vw" : "(max-width: 700px) 50vw, 25vw"}
                    className="object-cover" onError={() => setFailed(current => [...current, product.imageUrl!])} />
                <span>{product.name}</span>
            </div>)}
        </div> : <div className="gokul-gallery-fallback" role="img" aria-label="Gokul Sweets brand banner"><strong>Freshly made.<br />Ready for you.</strong></div>}
        <nav className="gokul-branch-tabs" aria-label="Branch pages"><a href="#gokul-menu-items" aria-current="page">Menu</a><Link href="/checkout/pickup">Pickup time</Link><Link href="/about#our-branches">Branch details</Link></nav>
    </div>;
}
