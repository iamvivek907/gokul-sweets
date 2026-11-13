/* eslint-disable @next/next/no-img-element -- Branch-uploaded photos of finished packages. */
import type {OccasionBox, OccasionPackage, OccasionSweet} from "@/types/occasionCatalogue";
import {T} from "@/lib/language";
import styles from "./OccasionPackages.module.css";

export default function OccasionPackages({packages, boxes, products, guests, onAdd, full}: {
    packages: OccasionPackage[];
    boxes: OccasionBox[];
    products: OccasionSweet[];
    guests: number;
    onAdd: (preset: OccasionPackage) => void;
    full: boolean;
}) {
    const sweets = new Map(products.map(product => [product.id, product]));
    const packing = new Map(boxes.map(box => [box.id, box]));
    const count = Math.max(1, Math.min(10000, Math.trunc(guests) || 1));
    return <div className={styles.cards}>
        {packages.map(preset => {
            const box = packing.get(preset.boxId);
            const contents = preset.recipe.map(line => ({...line, product: sweets.get(line.productId)}));
            if (!box || !contents.length || contents.some(line => !line.product)) return null;
            const notice = Math.max(box.leadDays, ...contents.map(line => line.product!.leadDays));
            return <article key={preset.id} className={styles.card}>
                {preset.imageUrl && <img src={preset.imageUrl} alt={preset.name} width={640} height={400} loading="lazy" />}
                <div className={styles.copy}>
                    <p className={styles.kicker}><T text="Ready-made package" /></p>
                    <h3>{preset.name}</h3>
                    {preset.description && <p>{preset.description}</p>}
                    <p className={styles.meta}>{contents.reduce((sum,line)=>sum+line.pieces,0)} <T text="pieces per box" /> · {box.name}</p>
                    <ul>{contents.map(line => <li key={line.productId}>{line.pieces} × {line.product!.name}</li>)}</ul>
                    {notice > 0 && <p className={styles.meta}>{notice} <T text="days advance notice" /></p>}
                    <button type="button" className={styles.add} disabled={full} onClick={()=>onAdd(preset)}>
                        <T text="Add package" /> · {count.toLocaleString("en-IN")} <T text="boxes" />
                    </button>
                </div>
            </article>;
        })}
    </div>;
}
