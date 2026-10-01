"use client";
import {useEffect,useState} from "react";
import {usePathname} from "next/navigation";
import {useTranslation} from "@/lib/language";
export default function NavigationFeedback(){
 const pathname=usePathname(),translate=useTranslation(),[pending,setPending]=useState(false);
 useEffect(()=>{const reset=window.setTimeout(()=>setPending(false),0);return()=>clearTimeout(reset);},[pathname]);
 useEffect(()=>{let timer:ReturnType<typeof setTimeout>;const start=()=>{setPending(true);clearTimeout(timer);timer=setTimeout(()=>setPending(false),15000);};const click=(event:MouseEvent)=>{if(event.defaultPrevented||event.button!==0||event.metaKey||event.ctrlKey||event.shiftKey||event.altKey)return;const anchor=(event.target as Element)?.closest?.('a[href]') as HTMLAnchorElement|null;if(!anchor||anchor.target==='_blank'||anchor.hasAttribute('download'))return;const target=new URL(anchor.href,location.href);if(target.origin===location.origin&&target.pathname!==location.pathname)start();};document.addEventListener('click',click);window.addEventListener('gokul-navigation-start',start);window.addEventListener('popstate',start);return()=>{clearTimeout(timer);document.removeEventListener('click',click);window.removeEventListener('gokul-navigation-start',start);window.removeEventListener('popstate',start);};},[]);
 return pending?<div role="status" aria-live="polite" className="navigation-feedback"><span aria-hidden="true"/>{translate('Opening…')}</div>:null;
}
