"use client";
import {useEffect,useState} from "react";
export function usePhoneViewport(){
 const [phone,setPhone]=useState<boolean|null>(null);
 useEffect(()=>{const media=matchMedia('(max-width: 640px)');const update=()=>setPhone(media.matches);update();media.addEventListener('change',update);return()=>media.removeEventListener('change',update);},[]);
 return phone;
}
