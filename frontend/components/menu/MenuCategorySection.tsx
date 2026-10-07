import type {ReactNode} from "react";
import {T} from "@/lib/language";

export default function MenuCategorySection({id,name,displayName,count,description,collapsible,children}:{id:number;name:string;displayName?:string;count:number;description?:string|null;collapsible:boolean;children:ReactNode}){
    const heading=<><h3>{displayName?<T text={displayName}/>:name} <span className={displayName?"sr-only":undefined}>({count})</span></h3></>;
    const content=<>{description&&<p className="menu-category-description">{description}</p>}{children}</>;
    return <section id={`menu-category-${id}`} className="gokul-menu-category-section" data-menu-presentation={/everyday favourites|snack|biscuit|chips|chocolate|dairy|drink|beverage|bakery|cake|pastr|namkeen|नमकीन|बेकरी|पेय/i.test(name)?"retail":"food"} data-menu-retail-kind={/snack|biscuit|namkeen/i.test(name)?"compact":"photo"} aria-label={`${name} menu items`}>
        {collapsible?<details open className="menu-category-disclosure"><summary className="gokul-menu-category-heading">{heading}<span className="menu-section-chevron" aria-hidden="true"/></summary>{content}</details>:<><div className="gokul-menu-category-heading"><h3>{name}</h3><span>{count} <T text={count===1?"item":"items"}/></span></div>{content}</>}
    </section>;
}
