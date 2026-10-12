"use client";
import {useEffect,useLayoutEffect,useRef,useState} from "react";
import {apiClient} from "@/services/apiClient";
import {T} from "@/lib/language";
type Suggestion={addressLine:string;locality:string;postalCode:string;attribution:string};
export default function AddressLocationAssist({onSuggestion,contextKey}:{onSuggestion:(value:Suggestion)=>void;contextKey?:string}){
 const context=useRef(contextKey),active=useRef(true);
 useLayoutEffect(()=>{context.current=contextKey;},[contextKey]);
 useEffect(()=>{active.current=true;return()=>{active.current=false;};},[]);
 const [available,setAvailable]=useState(false),[busy,setBusy]=useState(false),[message,setMessage]=useState("");
 useEffect(()=>{const controller=new AbortController();void apiClient<{enabled:boolean}>("/api/customer/identity/account/location",{credentials:"include",signal:controller.signal}).then(result=>{if(!controller.signal.aborted)setAvailable(result.enabled);}).catch(()=>{});return()=>controller.abort();},[]);
 async function locate(){if(busy)return;const startedContext=context.current;setBusy(true);setMessage("");try{if(!navigator.geolocation)throw new Error("This browser cannot share location. Enter your address manually.");const position=await new Promise<GeolocationPosition>((resolve,reject)=>navigator.geolocation.getCurrentPosition(resolve,reject,{timeout:10000,maximumAge:0,enableHighAccuracy:false}));const value=await apiClient<Suggestion>("/api/customer/identity/account/location",{method:"POST",credentials:"include",body:JSON.stringify({latitude:position.coords.latitude,longitude:position.coords.longitude}),signal:AbortSignal.timeout(10000)});if(!active.current)return;if(context.current!==startedContext){setMessage("Your address changed while location was loading. Your entered details were kept. Request location again if needed.");return;}onSuggestion(value);setMessage("Address suggested using Google Maps. Check the house/shop details and postal code, then Save address. Nothing is saved yet.");}catch{if(active.current)setMessage("Location could not be used. You can enter or edit your address manually.");}finally{if(active.current)setBusy(false);}}
 return <aside className="address-location-assist"><strong><T text="Start with your current location"/></strong><p><T text="Optional. Your location is sent once to Google Maps to suggest an address. Review and save it yourself."/></p><button type="button" disabled={!available||busy} onClick={()=>void locate()}><T text={busy?"Finding your address…":"Use my current location"}/></button>{!available&&<p><T text="Location suggestions are unavailable. You can enter your address below."/></p>}{message&&<p role="status"><T text={message}/></p>}</aside>;
}
