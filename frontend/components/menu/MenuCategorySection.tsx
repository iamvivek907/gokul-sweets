import type {ReactNode} from "react";
import {T} from "@/lib/language";

export default function MenuCategorySection({id,name,count,description,collapsible,children}:{id:number;name:string;count:number;description?:string|null;collapsible:boolean;children:ReactNode}){
    const heading=<><h3>{name} <span>({count})</span></h3></>;
    const content=<>{description&&<p className="menu-category-description">{description}</p>}{children}</>;
    return <section id={`menu-category-${id}`} className="gokul-menu-category-section" aria-label={`${name} menu items`}>
        {collapsible?<details open className="menu-category-disclosure"><summary className="gokul-menu-category-heading">{heading}<span className="menu-section-chevron" aria-hidden="true"/></summary>{content}</details>:<><div className="gokul-menu-category-heading"><h3>{name}</h3><span>{count} <T text={count===1?"item":"items"}/></span></div>{content}</>}
    </section>;
}
