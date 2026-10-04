/** Installed by the customer shell before router effects, so legacy Back can be guarded. */
let backGuard:((event:PopStateEvent)=>void)|null=null;
if(typeof window!=="undefined")window.addEventListener("popstate",event=>backGuard?.(event),true);
export function guardPaymentBack(handler:(event:PopStateEvent)=>void){
 backGuard=handler;
 return()=>{if(backGuard===handler)backGuard=null;};
}
