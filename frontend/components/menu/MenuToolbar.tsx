"use client";
import {useEffect,useRef,useState,type ReactNode} from 'react';
/** Measure again immediately before a category jump, after its filters have committed. */
export function measureMenuToolbar(toolbar:HTMLElement){
 const header=toolbar.closest('.app-container')?.querySelector('.customer-site-header');
 const height=header?.getBoundingClientRect().height??0;
 toolbar.style.setProperty('--menu-header-height',`${height}px`);
 toolbar.parentElement?.style.setProperty('--menu-scroll-offset',`${Math.ceil(height+toolbar.getBoundingClientRect().height+12)}px`);
}
/** The sticky phone toolbar follows the shared header's measured height. */
export default function MenuToolbar({enabled,children}:{enabled:boolean;children:ReactNode}){
 const ref=useRef<HTMLDivElement>(null);const [attention,setAttention]=useState(false);
 useEffect(()=>{
  if(!enabled)return;const header=document.querySelector('.customer-site-header');
  const toolbar=ref.current,parent=toolbar?.parentElement;
  const measure=()=>{if(toolbar)measureMenuToolbar(toolbar);};
  const resize=new ResizeObserver(measure);if(header)resize.observe(header);if(toolbar)resize.observe(toolbar);measure();
  return()=>{resize.disconnect();parent?.style.removeProperty('--menu-scroll-offset');toolbar?.style.removeProperty('--menu-header-height');};
 },[enabled]);
 useEffect(()=>{
  if(!enabled)return;const show=()=>setAttention(true),clear=()=>setAttention(false);
  window.addEventListener('gokul-pickup-attention',show);window.addEventListener('scroll',clear,{passive:true,capture:true});window.addEventListener('wheel',clear,{passive:true});window.addEventListener('touchmove',clear,{passive:true});
  return()=>{window.removeEventListener('gokul-pickup-attention',show);window.removeEventListener('scroll',clear,true);window.removeEventListener('wheel',clear);window.removeEventListener('touchmove',clear);};
 },[enabled]);
 return <div ref={ref} className={enabled?'mobile-menu-sticky-tools':undefined} data-pickup-attention={enabled&&attention?'true':undefined}>{children}{enabled&&attention&&<p className="pickup-attention-message" role="status">Choose another pickup date or time for this item.</p>}</div>;
}
