"use client";
import {useLayoutEffect,useId,useRef,type ReactNode,type RefObject} from "react";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import styles from "./MenuDiscoverySheet.module.css";
import {useMenuSheetDismiss} from "@/hooks/useMenuSheetDismiss";
import {T,useTranslation} from "@/lib/language";

export default function MenuDiscoverySheet({title,children,onClose,onDismissStart,className,returnFocusId,restoreFocus,initialFocusSelector,restoreFocusOnClose,fitVisualViewport=false,tactile=false,footer,closeLabel="Close",hideClose=false}:{title:string;children:ReactNode;onClose:()=>void;onDismissStart?:()=>void;className?:string;returnFocusId?:string;restoreFocus?:HTMLElement|null;initialFocusSelector?:string;restoreFocusOnClose?:RefObject<boolean>;fitVisualViewport?:boolean;tactile?:boolean;footer?:ReactNode|((close:()=>void)=>ReactNode);closeLabel?:string;hideClose?:boolean}){
 const translate=useTranslation(),features=useStorefrontFeatures();
 const polished=tactile&&!!(features?.futuristicStorefrontV2||features?.checkoutExperienceV2);
 const dialog=useRef<HTMLDialogElement>(null),titleId=useId();
 const dismiss=useMenuSheetDismiss(dialog,onClose,tactile,onDismissStart);
 const closeImmediately=()=>{onDismissStart?.();onClose();};
 useLayoutEffect(()=>{
  const surface=dialog.current,previous=restoreFocus??document.activeElement as HTMLElement|null;
  const body=document.body,root=document.documentElement,y=window.scrollY,x=window.scrollX;
  const saved={position:body.style.position,top:body.style.top,left:body.style.left,width:body.style.width,overflow:body.style.overflow,rootOverflow:root.style.overflow};
  body.style.position="fixed";body.style.top=`-${y}px`;body.style.left=`-${x}px`;body.style.width="100%";body.style.overflow="hidden";root.style.overflow="hidden";
  const viewport=fitVisualViewport?window.visualViewport:null;
  const fit=()=>{
   if(!viewport)return;
   surface?.style.setProperty("--menu-viewport-top",`${viewport.offsetTop}px`);
   surface?.style.setProperty("--menu-viewport-height",`${viewport.height}px`);
  };
  fit();
  viewport?.addEventListener("resize",fit);
  viewport?.addEventListener("scroll",fit);
  surface?.showModal();
  if(initialFocusSelector)surface?.querySelector<HTMLElement>(initialFocusSelector)?.focus({preventScroll:true});
  return()=>{
   viewport?.removeEventListener("resize",fit);viewport?.removeEventListener("scroll",fit);
   surface?.close();Object.assign(body.style,{position:saved.position,top:saved.top,left:saved.left,width:saved.width,overflow:saved.overflow});root.style.overflow=saved.rootOverflow;
   window.scrollTo({left:x,top:y,behavior:"instant"});
   // This ref is an explicit close-time decision made by the caller.
   // eslint-disable-next-line react-hooks/exhaustive-deps
   if(restoreFocusOnClose?.current===false)return;
   const resolveTarget=()=>restoreFocus&&returnFocusId?document.getElementById(returnFocusId):previous?.isConnected?previous:returnFocusId?document.getElementById(returnFocusId):null;resolveTarget()?.focus({preventScroll:true});
   // Header observers can briefly make the trigger inert during scroll unlock.
   // Resolve it again after the header settles; never move the viewport to focus it.
   {
    let attempts=0;
    const restore=()=>{
     const current=resolveTarget();
     if(!current?.isConnected)return;
     current.focus({preventScroll:true});
     if(document.activeElement!==current&&attempts++<20)requestAnimationFrame(restore);
    };
    requestAnimationFrame(restore);
   }
  };
 },[returnFocusId,restoreFocus,initialFocusSelector,restoreFocusOnClose,fitVisualViewport]);
 const content=<>
  <header><h2 id={titleId}><T text={title}/></h2>{!polished&&!hideClose&&<button type="button" aria-label={closeLabel==="Close"?undefined:translate(closeLabel)} onClick={()=>tactile?dismiss(true):closeImmediately()} autoFocus={!initialFocusSelector}><T text="Close"/> ×</button>}</header>
  <div tabIndex={polished?0:undefined} className={`mobile-menu-suggestion-body ${polished?styles.scrollBody:""}`}>{children}</div>
  {footer&&<footer className={styles.footer}>{typeof footer==="function"?footer(()=>tactile?dismiss():closeImmediately()):footer}</footer>}
 </>;
 return <dialog ref={dialog} className={`mobile-menu-suggestion-dialog ${styles.dialog} ${tactile?styles.motion:""} ${polished?styles.polished:""} ${className??""}`} aria-labelledby={titleId} onCancel={event=>{event.preventDefault();if(tactile)dismiss();else closeImmediately();}} onClick={event=>{const box=event.currentTarget.getBoundingClientRect();if(event.target===event.currentTarget&&(event.clientX<box.left||event.clientX>box.right||event.clientY<box.top||event.clientY>box.bottom)){if(tactile)dismiss();else closeImmediately();}}}>
  {polished&&!hideClose&&<button type="button" className={styles.floatingClose} aria-label={translate(closeLabel)} onClick={()=>dismiss(true)} autoFocus><span aria-hidden="true">×</span></button>}
  {polished?<div className={styles.frame}>{content}</div>:content}
 </dialog>;
}
