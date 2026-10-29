"use client";
import {useEffect,useRef,type RefObject} from "react";
import {menuTapFeedback} from "@/lib/menuHaptics";

/** Keep the modal and scroll lock mounted until its short exit finishes. */
export function useMenuSheetDismiss(surface:RefObject<HTMLElement|null>,onClose:()=>void,feedback=false) {
    const timer=useRef<ReturnType<typeof setTimeout>|null>(null);
    useEffect(()=>()=>{if(timer.current!==null)clearTimeout(timer.current);},[]);
    return (tactile=false)=>{
        if(timer.current!==null)return;
        if(tactile&&feedback)menuTapFeedback();
        const node=surface.current;
        if(window.location.pathname!=="/menu"||!node?.closest('.future-storefront')||window.matchMedia('(prefers-reduced-motion: reduce)').matches){onClose();return;}
        node.dataset.closing="true";
        timer.current=setTimeout(onClose,180);
    };
}
