"use client";
import {useEffect,useLayoutEffect,useRef,type RefObject} from 'react';
/** Timed scrolling never runs offscreen, in the background, during interaction or reduced motion. */
export function useCarouselAutoplay(ref:RefObject<HTMLElement|null>,count:number,advance:()=>void,paused=false){
 const callback=useRef(advance);
 useLayoutEffect(()=>{callback.current=advance;},[advance]);
 useEffect(()=>{
  const node=ref.current;if(!node||count<2||paused)return;
  const reduced=matchMedia('(prefers-reduced-motion: reduce)');
  let timer:ReturnType<typeof setTimeout>,visible=false,hover=false,drag=false,cooldown=0;
  const tick=()=>{
   const rotationFocused=document.activeElement?.getAttribute('data-carousel-rotation')==='true';
   if(visible&&!reduced.matches&&document.visibilityState==='visible'&&!hover&&!drag&&(!node.parentElement?.contains(document.activeElement)||rotationFocused)&&!document.querySelector('dialog[open]')&&Date.now()>=cooldown&&node.scrollWidth>node.clientWidth+2)callback.current();
   timer=setTimeout(tick,4500);
  };
  const enter=(e:PointerEvent)=>{if(e.pointerType==='mouse')hover=true;};
  const leave=()=>{hover=false;cooldown=Date.now()+4500;};
  const down=()=>{drag=true;};
  const up=()=>{if(drag){drag=false;cooldown=Date.now()+8000;}};
  const interact=()=>{cooldown=Date.now()+8000;};
  const observer=new IntersectionObserver(entries=>{visible=entries.some(e=>e.isIntersecting&&e.intersectionRatio>=0.5);},{threshold:[0,0.5]});observer.observe(node);
  node.addEventListener('pointerenter',enter);node.addEventListener('pointerleave',leave);node.addEventListener('pointerdown',down);node.addEventListener('wheel',interact,{passive:true});node.addEventListener('focusout',interact);
  window.addEventListener('pointerup',up);window.addEventListener('pointercancel',up);
  timer=setTimeout(tick,4500);
  return()=>{clearTimeout(timer);observer.disconnect();node.removeEventListener('pointerenter',enter);node.removeEventListener('pointerleave',leave);node.removeEventListener('pointerdown',down);node.removeEventListener('wheel',interact);node.removeEventListener('focusout',interact);window.removeEventListener('pointerup',up);window.removeEventListener('pointercancel',up);};
 },[ref,count,paused]);
}
