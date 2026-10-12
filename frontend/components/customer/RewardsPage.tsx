"use client";
import {useState} from "react";
import CustomerIdentityPanel,{type CustomerSession} from "./CustomerIdentityPanel";
import CustomerRewards from "./CustomerRewards";
import MobilePageBack from "./MobilePageBack";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {T} from "@/lib/language";
export default function RewardsPage(){
 const [session,setSession]=useState<CustomerSession|null>(null);const features=useStorefrontFeatures();
 return <div className="customer-rewards-page"><MobilePageBack href="/profile" label="Back to profile"/><h1><T text="Your Gokul rewards"/></h1><p className="rewards-intro"><T text="Earn from completed orders. Choose a saving at checkout."/></p><div className={session?.authenticated?"hidden":""}><CustomerIdentityPanel onSessionChange={setSession}/></div>{session?.authenticated&&(features?.gokulRewards?<CustomerRewards key={session.phone}/>:<p><T text="Rewards are not available yet."/></p>)}</div>;
}
