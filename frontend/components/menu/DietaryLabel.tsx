"use client";
import {useLanguage} from "@/lib/language";
import styles from "./DietaryLabel.module.css";

export default function DietaryLabel({vegetarian}:{vegetarian?:boolean}) {
    const language=useLanguage();
    if(typeof vegetarian!=="boolean")return null;
    const label=language==="hi"?(vegetarian?"शाकाहारी":"मांसाहारी"):(vegetarian?"Veg":"Non-veg");
    return <span className={`${styles.label} ${vegetarian?styles.veg:styles.nonveg}`} data-dietary={vegetarian?"veg":"non-veg"}>
        <svg role="img" aria-label={label} width="16" height="16" viewBox="0 0 20 20">
            <rect x="1" y="1" width="18" height="18" rx="3" fill="white" stroke="currentColor" strokeWidth="1.6"/>
            {vegetarian?<circle cx="10" cy="10" r="4.5" fill="currentColor"/>:<path d="M10 4.5 16 15H4Z" fill="currentColor"/>}
        </svg><span aria-hidden="true">{label}</span>
    </span>;
}
export function GroupDietaryLabel({products}:{products:{vegetarian?:boolean}[]}) {
    const language=useLanguage();
    if(!products.length||products.some(p=>typeof p.vegetarian!=="boolean"))return null;
    const values=[...new Set(products.map(p=>p.vegetarian))];
    return <span className={styles.group}>{values.map(value=><DietaryLabel key={String(value)} vegetarian={value}/>)}{values.length>1&&<small>{language==="hi"?"विकल्प के अनुसार":"Varies by option"}</small>}</span>;
}
