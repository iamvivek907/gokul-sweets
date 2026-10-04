"use client";
import {guardPaymentBack} from "@/lib/paymentNavigation";
import {T,useTranslation} from "@/lib/language";
import {useEffect,useRef,useState} from "react";
import {usePathname,useRouter} from "next/navigation";
/** Intercept deliberate storefront navigation while money/reservations are unresolved. */
export default function PaymentLeaveChoice({active,busy,error,onCancel,autoCancel=false}:{active:boolean;busy:boolean;error?:string|null;autoCancel?:boolean;onCancel:(destination?:string)=>Promise<void>}) {
    const translate = useTranslation();
 const router=useRouter(),pathname=usePathname();const allowed=useRef(false),started=useRef(false);const [destination,setDestination]=useState<string|null>(null);
 useEffect(()=>{
  if(!active)return;
  const requestLeave=(next:string)=>{started.current=false;setDestination(next);};
  const click=(event:MouseEvent)=>{
   const link=(event.target as Element)?.closest?.("a[href]") as HTMLAnchorElement|null;
   if(!link||allowed.current||event.defaultPrevented||event.button!==0||event.metaKey||event.ctrlKey||event.shiftKey||link.target==="_blank")return;
   const target=new URL(link.href);
   if(target.origin!==location.origin||target.pathname===location.pathname)return;
   event.preventDefault();event.stopImmediatePropagation();
   requestLeave(target.pathname+target.search+target.hash);
  };
  const path=pathname+location.search+location.hash;
  let state=history.state;
  queueMicrotask(()=>{state=history.state;});
  const back=(event:PopStateEvent)=>{
   if(!autoCancel||allowed.current||location.pathname===pathname)return;
   event.stopImmediatePropagation();
   const next=location.pathname+location.search+location.hash;
   history.pushState(state,"",path);
   requestLeave(next);
  };
  const navigation=(window as Window&{navigation?:EventTarget}).navigation;
  const traverse=(event:Event)=>{
   const nav=event as Event&{navigationType?:string;canIntercept?:boolean;destination?:{url:string}};
   if(!autoCancel||allowed.current||nav.navigationType!=="traverse"||!nav.canIntercept||!nav.cancelable||!nav.destination)return;
   const next=new URL(nav.destination.url);
   if(next.origin!==location.origin||next.pathname===pathname)return;
   nav.preventDefault();requestLeave(next.pathname+next.search+next.hash);
  };
  if(autoCancel)navigation?.addEventListener("navigate",traverse,true);
  document.addEventListener("click",click,true);
  const unguard=autoCancel?guardPaymentBack(back):()=>{};
  return()=>{document.removeEventListener("click",click,true);unguard();navigation?.removeEventListener("navigate",traverse,true);};
 },[active,autoCancel,pathname]);
 useEffect(()=>{if(autoCancel&&active&&destination&&!busy&&!started.current){started.current=true;void onCancel(destination);}},[autoCancel,active,destination,busy,onCancel]);
 if(autoCancel&&active&&destination)return <MobilePaymentLeave busy={busy} error={error} onStay={()=>{setDestination(null);started.current=false;}} onRetry={()=>void onCancel(destination)}/>;
 if(!active||!destination)return null;
 return <div role="dialog" aria-modal="true" aria-labelledby="leave-payment-title" className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 p-4" onKeyDown={e=>{if(e.key==="Escape"&&!busy)setDestination(null);if(e.key==="Tab"){const buttons=Array.from(e.currentTarget.querySelectorAll<HTMLButtonElement>("button:not(:disabled)"));const first=buttons[0],last=buttons.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}}}}><section className="w-full max-w-md rounded-2xl bg-[#fffaf2] p-6 text-[#173c39]"><h2 id="leave-payment-title" className="text-xl font-bold"><T text="Before you leave payment" /></h2><p className="mt-3 text-sm leading-6"><T text="Keep this order to pay later, or check payment and cancel the unpaid reservation now. Your cart stays saved. A confirmed payment will preserve the order." /></p>{error&&<p role="alert" className="mt-3 rounded-xl border border-red-300 p-3 text-sm">{translate(error)} <T text="Your reservation is unchanged if provider verification failed. You can retry the check or stay on payment." /></p>}<div className="mt-4 grid gap-3"><button autoFocus type="button" disabled={busy} className="min-h-11 rounded-xl bg-[#173c39] px-4 font-bold text-white" onClick={()=>setDestination(null)}><T text="Stay & retry payment" /></button><button type="button" disabled={busy} className="min-h-11 rounded-xl border px-4 font-bold" onClick={()=>{allowed.current=true;router.push(destination);}}><T text="Leave & keep order for later" /></button><button type="button" disabled={busy} className="min-h-11 rounded-xl border px-4 font-bold" onClick={()=>void onCancel()}>{busy?"Checking provider…":translate("Cancel order & keep cart")}</button></div></section></div>;
}

function MobilePaymentLeave({busy,error,onStay,onRetry}:{busy:boolean;error?:string|null;onStay:()=>void;onRetry:()=>void}){
 const dialog=useRef<HTMLDialogElement>(null),translate=useTranslation();
 useEffect(()=>{const surface=dialog.current,previous=document.activeElement as HTMLElement|null;surface?.showModal();return()=>{surface?.close();previous?.focus();};},[]);
 return <dialog ref={dialog} onCancel={event=>{event.preventDefault();if(!busy)onStay();}} aria-labelledby="mobile-leave-payment-title" className="mobile-payment-leave"><section><h2 id="mobile-leave-payment-title"><T text={busy?"Checking payment before leaving…":"Payment check needs attention"}/></h2><p><T text="We’ll cancel the unpaid reservation and keep your cart. A confirmed payment keeps your order."/></p>{error&&<p role="alert">{translate(error)}</p>}<button autoFocus type="button" disabled={busy} onClick={onStay}><T text="Stay on payment"/></button>{!busy&&<button type="button" onClick={onRetry}><T text="Retry payment check"/></button>}</section></dialog>;
}
