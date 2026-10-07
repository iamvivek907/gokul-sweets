"use client";
import BrandLoading from "@/components/common/BrandLoading";
import {useEffect} from "react";
import {useRouter} from "next/navigation";
import AppShell from "@/components/layout/AppShell";
import MobileCheckout from "@/components/checkout/MobileCheckout";
import {usePhoneViewport} from "@/hooks/usePhoneViewport";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
export default function MobileCheckoutPage(){
 const phone=usePhoneViewport(),features=useStorefrontFeatures(),router=useRouter();
 const enabled=phone===true&&features?.simplifiedCheckout===true&&features.checkoutExperienceV2&&features.acceptedCheckoutQuote;
 useEffect(()=>{if(phone===false||phone===true&&features&&!enabled)router.replace(phone===true&&features?.simplifiedCheckout?"/checkout/pickup":"/cart");},[phone,features,enabled,router]);
 return <AppShell showSocialPopup={false}>{enabled?<MobileCheckout/>:<BrandLoading label="Loading checkout…" />}</AppShell>;
}
