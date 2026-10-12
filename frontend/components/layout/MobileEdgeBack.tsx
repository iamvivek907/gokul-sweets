"use client";
import {useEffect} from "react";
import {usePathname} from "next/navigation";

/** Browsers own their native history gesture. Standalone apps use the page's guarded Back link. */
export default function MobileEdgeBack(){
 const pathname=usePathname();
 useEffect(()=>{
  const standalone=matchMedia("(display-mode: standalone)").matches||(navigator as Navigator&{standalone?:boolean}).standalone===true;
  if(!standalone||!matchMedia("(max-width: 640px)").matches||pathname.startsWith("/checkout/payment"))return;
  const shell=document.querySelector<HTMLElement>(".future-storefront[data-customer-route]");
  const reset=()=>{shell?.removeAttribute("data-edge-back");shell?.style.removeProperty("--customer-edge-offset");};
  let origin:{x:number;y:number;link:HTMLAnchorElement}|null=null;
  const start=(event:TouchEvent)=>{
   reset();origin=null;if(event.touches.length!==1||document.querySelector("dialog[open],[aria-modal=true]"))return;
   if(event.target instanceof Element&&event.target.closest("input,select,textarea,button,[contenteditable=true],.mobile-menu-pairing-row"))return;
   const touch=event.touches[0],link=document.querySelector<HTMLAnchorElement>(".mobile-page-back");
   if(touch.clientX<=24&&link&&new URL(link.href).origin===location.origin)origin={x:touch.clientX,y:touch.clientY,link};
  };
  const move=(event:TouchEvent)=>{
   if(!origin||event.touches.length!==1){reset();origin=null;return;}
   const dx=event.touches[0].clientX-origin.x,dy=Math.abs(event.touches[0].clientY-origin.y);
   if(dy>30||dx<0){reset();origin=null;return;}
   if(dx>20&&dx>dy*3){if(event.cancelable)event.preventDefault();if(!matchMedia("(prefers-reduced-motion: reduce)").matches&&shell){shell.dataset.edgeBack="true";shell.style.setProperty("--customer-edge-offset",`${Math.min(dx*.5,80)}px`);}}
  };
  const end=(event:TouchEvent)=>{
   reset();if(!origin)return;const value=origin;origin=null;const touch=event.changedTouches[0];
   if(touch&&touch.clientX-value.x>=100&&Math.abs(touch.clientY-value.y)<30&&value.link.isConnected){
    // Clicking the real link preserves payment-navigation interception and its return target.
    value.link.click();
   }
  };
  const cancel=()=>{reset();origin=null;};
  document.addEventListener("scroll",cancel,{passive:true,capture:true});window.addEventListener("blur",cancel);document.addEventListener("visibilitychange",cancel);
  document.addEventListener("touchstart",start,{passive:true});document.addEventListener("touchmove",move,{passive:false});
  document.addEventListener("touchend",end);document.addEventListener("touchcancel",cancel);
  return()=>{reset();document.removeEventListener("scroll",cancel,true);window.removeEventListener("blur",cancel);document.removeEventListener("visibilitychange",cancel);document.removeEventListener("touchstart",start);document.removeEventListener("touchmove",move);document.removeEventListener("touchend",end);document.removeEventListener("touchcancel",cancel);};
 },[pathname]);
 return null;
}
