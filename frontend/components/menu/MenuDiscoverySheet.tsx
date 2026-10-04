"use client";
import {useEffect,useId,useRef,type ReactNode} from "react";
import {T} from "@/lib/language";

export default function MenuDiscoverySheet({title,children,onClose}:{title:string;children:ReactNode;onClose:()=>void}){
 const dialog=useRef<HTMLDialogElement>(null),titleId=useId();
 useEffect(()=>{
  const surface=dialog.current,previous=document.activeElement as HTMLElement|null,overflow=document.body.style.overflow;
  document.body.style.overflow="hidden";surface?.showModal();
  return()=>{surface?.close();document.body.style.overflow=overflow;previous?.focus();};
 },[]);
 return <dialog ref={dialog} className="mobile-menu-suggestion-dialog" aria-labelledby={titleId} onCancel={event=>{event.preventDefault();onClose();}} onClick={event=>{const box=event.currentTarget.getBoundingClientRect();if(event.target===event.currentTarget&&(event.clientX<box.left||event.clientX>box.right||event.clientY<box.top||event.clientY>box.bottom))onClose();}}>
  <header><h2 id={titleId}><T text={title}/></h2><button type="button" onClick={onClose} autoFocus><T text="Close"/> ×</button></header>
  <div className="mobile-menu-suggestion-body">{children}</div>
 </dialog>;
}
