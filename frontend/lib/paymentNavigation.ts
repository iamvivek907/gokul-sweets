/** Installed by the customer shell before router effects, so legacy Back can be guarded. */
let backGuard:((event:PopStateEvent)=>void)|null=null;
if(typeof window!=="undefined")window.addEventListener("popstate",event=>backGuard?.(event),true);
export function guardPaymentBack(handler:(event:PopStateEvent)=>void){
 backGuard=handler;
 return()=>{if(backGuard===handler)backGuard=null;};
}

/** A server-confirmed paid order must not reopen merchant checkout via Android Back.
 * External gateway history remains browser-owned. Do not intercept ordinary menu/history Back. */
export function guardCompletedCheckout(orderNumber:string,onLeave:()=>void) {
 const isCheckout=(url:string)=>{
  const next=new URL(url,location.origin);
  if(next.origin!==location.origin)return false;
  const completed=encodeURIComponent(orderNumber);
  if(next.pathname===`/checkout/payment/${completed}`||next.pathname===`/checkout/offers/${completed}`)return true;
  if(!/^\/checkout(?:\/|$)/.test(next.pathname)||/^\/checkout\/(?:payment|offers)\//.test(next.pathname))return false;
  // A later cart, reservation or uncertain attempt still owns its normal Back/recovery flow.
  try{return !localStorage.getItem("gokul-pending-order")&&!localStorage.getItem("gokul-pending-payment")&&!sessionStorage.getItem("gokul-mobile-order-attempt")&&!JSON.parse(localStorage.getItem("gokul-cart")??"null")?.items?.length;}
  catch{return false;}
 };
 const unguard=guardPaymentBack(event=>{
  if(!isCheckout(location.href))return;
  event.stopImmediatePropagation();
  history.replaceState(history.state,"","/orders");
  onLeave();
 });
 // Let Back advance to the old entry before replacing it. Cancelling a modern
 // Navigation API traversal leaves that entry behind the Orders page forever.
 return unguard;
}
