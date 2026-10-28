"use client";
import {useEffect,useId,useRef,type ReactNode} from "react";
import styles from "./MenuDiscoverySheet.module.css";
import {T} from "@/lib/language";

export default function MenuDiscoverySheet({title,children,onClose,className,returnFocusId,restoreFocus}:{title:string;children:ReactNode;onClose:()=>void;className?:string;returnFocusId?:string;restoreFocus?:HTMLElement|null}){
 const dialog=useRef<HTMLDialogElement>(null),titleId=useId();
 useEffect(()=>{
  const surface=dialog.current,previous=restoreFocus??document.activeElement as HTMLElement|null;
  const body=document.body,root=document.documentElement,y=window.scrollY,x=window.scrollX;
  const saved={position:body.style.position,top:body.style.top,left:body.style.left,width:body.style.width,overflow:body.style.overflow,rootOverflow:root.style.overflow};
  body.style.position="fixed";body.style.top=`-${y}px`;body.style.left=`-${x}px`;body.style.width="100%";body.style.overflow="hidden";root.style.overflow="hidden";
  surface?.showModal();
  return()=>{
   surface?.close();Object.assign(body.style,{position:saved.position,top:saved.top,left:saved.left,width:saved.width,overflow:saved.overflow});root.style.overflow=saved.rootOverflow;
   window.scrollTo({left:x,top:y,behavior:"instant"});const target=restoreFocus&&returnFocusId?document.getElementById(returnFocusId):previous?.isConnected?previous:returnFocusId?document.getElementById(returnFocusId):null;target?.focus({preventScroll:true});
   // Header observers can briefly make the trigger inert during scroll unlock.
   // Resolve it again after the header settles; never move the viewport to focus it.
   if(restoreFocus){
    let attempts=0;
    const restore=()=>{
     const current=returnFocusId?document.getElementById(returnFocusId):target;
     if(!current?.isConnected)return;
     current.focus({preventScroll:true});
     if(document.activeElement!==current&&attempts++<20)requestAnimationFrame(restore);
    };
    requestAnimationFrame(restore);
   }
  };
 },[returnFocusId,restoreFocus]);
 return <dialog ref={dialog} className={`mobile-menu-suggestion-dialog ${styles.dialog} ${className??""}`} aria-labelledby={titleId} onCancel={event=>{event.preventDefault();onClose();}} onClick={event=>{const box=event.currentTarget.getBoundingClientRect();if(event.target===event.currentTarget&&(event.clientX<box.left||event.clientX>box.right||event.clientY<box.top||event.clientY>box.bottom))onClose();}}>
  <header><h2 id={titleId}><T text={title}/></h2><button type="button" onClick={onClose} autoFocus><T text="Close"/> ×</button></header>
  <div className="mobile-menu-suggestion-body">{children}</div>
 </dialog>;
}
