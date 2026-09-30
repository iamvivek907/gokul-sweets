"use client";
import {useSyncExternalStore} from "react";
const snapshot = () => Math.floor(Date.now()/1000)*1000;
function subscribe(listener: () => void) {
 const timer=window.setInterval(listener,1000);window.addEventListener("focus",listener);document.addEventListener("visibilitychange",listener);
 return ()=>{window.clearInterval(timer);window.removeEventListener("focus",listener);document.removeEventListener("visibilitychange",listener);};
}
export function usePickupClock() {return useSyncExternalStore(subscribe,snapshot,()=>0);}
