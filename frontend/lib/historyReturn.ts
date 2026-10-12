"use client";
import {useSyncExternalStore} from "react";
export function safeHistoryReturn(value:string|null):string{
 if(!value||value.length>2000||!value.startsWith("/")||value.startsWith("//"))return "/orders";
 try{const url=new URL(value,"https://gokul-history.invalid");if(url.origin!=="https://gokul-history.invalid"||!["/orders","/profile/orders"].includes(url.pathname))return "/orders";return url.pathname+url.search;}catch{return "/orders";}
}
const subscribe=(callback:()=>void)=>{window.addEventListener("popstate",callback);return()=>window.removeEventListener("popstate",callback);};
const readReturn=()=>safeHistoryReturn(new URLSearchParams(window.location.search).get("from"));
export function useHistoryReturn(){return useSyncExternalStore(subscribe,readReturn,()=>"/orders");}
