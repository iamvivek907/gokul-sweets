"use client";
import {useEffect,useState,type CSSProperties} from "react";
import type {MenuCategory} from "@/types/menu";
import {T} from "@/lib/language";
import MenuDiscoverySheet from "./MenuDiscoverySheet";

export default function FloatingMenuCategories({categories,selectedId,hasCart,onSelect}:{categories:MenuCategory[];selectedId?:number;hasCart:boolean;onSelect:(id:number|null)=>void}) {
    const [open,setOpen]=useState(false),[cartHeight,setCartHeight]=useState(0),[bottom,setBottom]=useState(68);
    useEffect(()=>{
        const dock=hasCart?document.querySelector<HTMLElement>(".gokul-floating-cart"):null;
        const navigation=document.querySelector<HTMLElement>(".customer-bottom-navigation");
        const update=()=>{
            setCartHeight(dock?Math.ceil(dock.getBoundingClientRect().height):0);
            setBottom(Math.ceil(dock?innerHeight-dock.getBoundingClientRect().top+12:(navigation?.offsetHeight??56)+12));
        };
        const observer=new ResizeObserver(update);
        if(dock)observer.observe(dock);
        if(navigation)observer.observe(navigation);
        window.addEventListener("resize",update);
        queueMicrotask(update);
        return()=>{observer.disconnect();window.removeEventListener("resize",update);};
    },[hasCart]);
    function select(id:number|null){setOpen(false);onSelect(id);}
    return <div className="menu-floating-categories" style={{"--floating-cart-height":`${cartHeight}px`,"--floating-menu-bottom":`${bottom}px`} as CSSProperties}>
        <button type="button" className="menu-floating-category-trigger" aria-haspopup="dialog" onClick={()=>setOpen(true)}>
            <svg aria-hidden="true" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8"><rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/></svg>
            <T text="Categories" />
        </button>
        {open&&<MenuDiscoverySheet title="Jump to a category" onClose={()=>setOpen(false)} className="menu-floating-category-sheet">

            <nav aria-label="Jump to menu category">
                <button type="button" onClick={()=>select(null)}><T text="All items" /><span>{categories.reduce((total,category)=>total+category.products.length,0)}</span></button>
                {categories.map(category=><button type="button" key={category.id} aria-current={selectedId===category.id?"true":undefined} onClick={()=>select(category.id)}>
                    <span>{category.name}</span><span>{category.products.length}</span>
                </button>)}
            </nav>
        </MenuDiscoverySheet>}
    </div>;
}
