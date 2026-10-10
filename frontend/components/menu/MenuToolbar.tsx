"use client";
import {useEffect,useRef,useState,type ReactNode} from 'react';
/** The sticky phone toolbar follows the shared header's measured height. */
export default function MenuToolbar({enabled,children}:{enabled:boolean;children:ReactNode}){
 const ref=useRef<HTMLDivElement>(null);const [attention,setAttention]=useState(false);
 useEffect(()=>{
  if(!enabled)return;const header=document.querySelector('.customer-site-header');
  const measure=()=>{if(header&&ref.current)ref.current.style.setProperty('--menu-header-height',`${header.getBoundingClientRect().height}px`);};
  const resize=new ResizeObserver(measure);if(header)resize.observe(header);measure();return()=>resize.disconnect();
 },[enabled]);
 useEffect(()=>{
  if(!enabled)return;const show=()=>setAttention(true),clear=()=>setAttention(false);
  window.addEventListener('gokul-pickup-attention',show);window.addEventListener('scroll',clear,{passive:true,capture:true});window.addEventListener('wheel',clear,{passive:true});window.addEventListener('touchmove',clear,{passive:true});
  return()=>{window.removeEventListener('gokul-pickup-attention',show);window.removeEventListener('scroll',clear,true);window.removeEventListener('wheel',clear);window.removeEventListener('touchmove',clear);};
 },[enabled]);
 return <div ref={ref} className={enabled?'mobile-menu-sticky-tools':undefined} data-pickup-attention={enabled&&attention?'true':undefined}>{children}{enabled&&attention&&<p className="pickup-attention-message" role="status">Choose another pickup date or time for this item.</p>}</div>;
}
