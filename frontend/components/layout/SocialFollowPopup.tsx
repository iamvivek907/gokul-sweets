"use client";
import {T} from "@/lib/language";
/** An optional inline invitation after confirmed payment; never covers ordering controls. */
export default function SocialFollowPopup({eligible=false}:{eligible?:boolean}){
 if(!eligible)return null;
 return <aside className="customer-social-thanks" aria-label="Stay connected"><div><strong><T text="Thank you for choosing Gokul"/></strong><p><T text="Follow us for fresh arrivals and festive favourites."/></p></div><div><a href="https://www.instagram.com/_gokulsweets" target="_blank" rel="noopener noreferrer">Instagram ↗</a><a href="https://www.facebook.com/visitgokulsweets" target="_blank" rel="noopener noreferrer">Facebook ↗</a></div></aside>;
}
