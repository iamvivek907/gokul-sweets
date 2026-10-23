"use client";
import BrandLoading from "@/components/common/BrandLoading";
import {useEffect} from "react";
import {useRouter} from "next/navigation";
import AppShell from "@/components/layout/AppShell";
import MobileCheckout from "@/components/checkout/MobileCheckout";
import {usePhoneViewport} from "@/hooks/usePhoneViewport";
import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";
import {useOnlineStatus} from "@/hooks/useOnlineStatus";
import CustomerRecovery from "@/components/common/CustomerRecovery";
export default function MobileCheckoutPage(){
 const phone=usePhoneViewport(),{features,error,retry}=useStorefrontConfiguration(),router=useRouter(),online=useOnlineStatus();
 const enabled=phone===true&&features?.simplifiedCheckout===true&&features.checkoutExperienceV2&&features.acceptedCheckoutQuote;
 useEffect(()=>{if(phone===false||phone===true&&features&&!enabled)router.replace(phone===true&&features?.simplifiedCheckout?"/checkout/pickup":"/cart");},[phone,features,enabled,router]);
 return <AppShell showSocialPopup={false}>{!features&&(error||!online)?<CustomerRecovery title="Your checkout is saved" message={online?"We couldn’t load ordering settings. Reconnect or try again; your cart is kept.":"You’re offline. Your cart is saved. Reconnect to continue."} onRetry={retry} disabled={!online}/>:enabled?<MobileCheckout/>:<BrandLoading label="Loading checkout…" />}</AppShell>;
}
